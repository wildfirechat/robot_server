# 野火实时语音 Agent —— 给 AI 打电话

在野火 IM 里给机器人拨一通语音电话，它接听、听懂、开口回答，说到一半你插话它立刻停下。
全程在内网，音频不出域。

## 为什么是野火来做这件事

野火有一个**服务端音视频 SDK**（`avenginekit`），在服务端给出通话音频的裸 PCM 读写口：

```java
// 收：对端音频，带 userId —— 按说话人分开
void playoutData(CallSession s, String userId, byte[] sampleData, int nBuffSize);
// 发：我们要播给对方的音频
void fetchRecordData(CallSession s, byte[] sampleData, int nSamples,
                     int nSampleBytes, int nChannels, int nSampleRate, int nBuffSize);
```

SDK 的 README 原话是"可以开发 AI 语音助手、陪聊机器人和机器人电话服务"——**这个发动机是为这件事造的，
但此前只有一个 echo demo：把你说的话延迟 3 秒播回来。** 本模块把它接到了真正的 AI 上。

那个 `userId` 是关键。别的会议 AI 拿到的是混音流，得先做说话人分离，那是整条链路上最脏的活；
野火在这里直接按人分好了。

三件套全部自有且可私有化：音视频高级版（SFU）+ wf-voice（自有 ASR）+ 这个服务端 SDK。
公有云语音 Agent 进不了政企内网，私有化 IM 厂商没有音视频和 ASR。

## 链路

```
用户拨打机器人
      │  WebRTC
      ▼
野火 IM ──► avenginekit ──► AiAudioDevice
                              │
              playoutData(userId, PCM)   每 10ms 一帧
                              │
              ┌───────────────┼───────────────┐
              ▼                               ▼
        能量判打断                      下混单声道 + 重采样 16k
     (SpeechGate, RMS)                        │
              │                               ▼
              │                  wf-voice WebSocket :12436
              │                  (Silero VAD + SenseVoice)
              │                               │
              │                    [时间戳+时长] 识别文本
              │                               ▼
              │                    攒句（VAD 常把一句切成几段）
              │                               ▼
              │                     LLM 流式 /v1/chat/completions
              │                               │
              │                        边生成边断句
              │                               ▼
              │                            TTS 合成
              │                               ▼
              └──── 作废本轮 ────►      重采样回通话采样率
                                              ▼
                                        PlaybackQueue
                                              ▼
                                      fetchRecordData
                                              ▼
                                       用户听到 AI 说话
```

## 延迟是怎么压下去的

端到端首次出声 = VAD 断点 + ASR + LLM 首句 + TTS。四段里有三段做了流式：

| 环节 | 做法 |
| --- | --- |
| 断句 | wf-voice 内置 Silero VAD，静音 2 个 chunk（约 64ms）即判定说完，不等整段 |
| ASR | SenseVoice 非自回归，一段话一次前向，没有自回归解码循环 |
| LLM | 不等生成完。攒出一个完整小句就立刻送 TTS，硬标点立断，软标点满 8 字断，60 字强制断 |
| TTS | 单线程串行合成，保证句子播放顺序；第一句合成完就开播，后面的边播边合成 |

`utteranceGapMs`（默认 400ms）是唯一主动加的延迟：VAD 经常把一句话切成几段
（"我想问一下" / "今天几号"），一段就触发生成会答非所问，所以等一等再看有没有下文。

## 打断（barge-in）

判定用的不是 VAD——VAD 要等一段话说完才出结果，对打断太慢。
`SpeechGate` 在通话线程上直接算帧能量 RMS，连续 6 帧（60ms）超阈值就认为用户开口了。
客户端有回声消除，服务端收到的基本是纯净人声，阈值可以给得低；要求连续多帧是为了
不被一声咳嗽误触。

作废机制是一个单调递增的整数 `currentTurn`：

- 每轮生成开始时 `currentTurn` 加一，并被闭包捕获；
- LLM 每读一行、TTS 每合成一句之前都对一次 turn；
- 用户一开口，`currentTurn` 再加一，播放队列清空。在途的 LLM 和 TTS 下一次检查时自己停下。

不中断线程，不用共享取消标志，只靠这一个整数。已在测试中验证：打断后仍在途的 TTS
完成了合成，但结果被正确丢弃，没有进入播放队列。

## 依赖

| 组件 | 说明 |
| --- | --- |
| 音视频 | 免费版或高级版均可。多人会议需要高级版 |
| ASR | wf-voice，启动后监听 12435(HTTP) / **12436(WebSocket)** |
| LLM | 任何 OpenAI 格式的 `/v1/chat/completions`：Ollama、vLLM、客户内网自建、云端 API |
| TTS | **野火没有自带语音合成，必须外挂。** 默认按 OpenAI `/v1/audio/speech` 格式请求，
请求体是可配的模板（`{text}`/`{model}`/`{voice}`），非标准接口改配置即可适配 |

## 配置

全部在 `config/voiceagent.properties`，见文件内注释。几个要点：

- `agent.enabled=false` 退回原来的 `EchoAudioDevice`（延迟 3 秒复读）。**排查时先用这个**：
  能复读就说明通话链路本身是通的，问题在 ASR/LLM/TTS 那一段。
- `agent.transcript-to-im=true` 把通话文字记录发回 IM 会话。用 `PersistFlag.Persist(1)`：
  本地存下来可回看但不计未读数。对政企客户这就是天然的审计留痕——谁、什么时候、
  说了什么、AI 答了什么，全落在 IM 的消息库里。
- `agent.barge-in-rms-threshold` 现场噪声大就调高，说话打不断就调低。
- `agent.tts-model` / `agent.tts-voice` 换合成模型和发音人，填进模板的 `{model}` / `{voice}`。
  接口不是 OpenAI 格式的（GPT-SoVITS、CosyVoice、商业 TTS），改 `agent.tts-body-template`
  这一行适配，音色字段各家叫法不同（speaker / spk_id / reference_id），把 `{voice}`
  放到它自己的字段名下面即可。
- `agent.playback-max-seconds` 播放队列最多缓存多少秒 AI 语音，默认 60。**这个不要调小**：
  TTS 比实时快，一段长回答会整段堆在队列里，缓存不够就得丢正在播的句子，
  听感上就是文本对、合成也对，但是丢音。

## 跑起来

```bash
# 1. ASR
./wf-voice -m ./model/wf-voice-small.gguf -p 4

# 2. LLM（示例用 Ollama）
ollama serve && ollama pull qwen2.5:7b-instruct

# 3. TTS：自行部署，把地址填进 agent.tts-url

# 4. 本服务
mvn -Dwebrtc.platform=macos-aarch64 -Djavacpp.platform=macosx-arm64 -Dmaven.test.skip=true package
java -jar target/robot-0.25.jar
```

`webrtc.platform` 可选 `linux-x86_64` / `linux-aarch64` / `macos-aarch64` / `windows-x86_64`。

然后在野火客户端里给机器人拨语音电话。

## 已验证 / 未验证

**已验证**（`AudioTest` / `PipelineTest`，见提交说明）：

- 重采样跨帧相位连续（48k→16k 正弦，最大相邻差 2070，理论上限 2073）
- 上下采样输出长度精确
- 播放队列跨块读取、不足补静音、按通话格式算上限、写满阻塞而不丢队头、打断清空
- WAV 解析（16bit 单声道 / 立体声下混）
- 打断需要连续多帧、静音不误触
- 端到端编排：边生成边断句分多次合成、通话记录双向、打断清空且在途结果被丢弃

**未验证**（需要真实环境）：

- 没有接过真实通话。以上都是用假的 LLM/TTS 和构造音频跑的，真机延迟、回声、
  丢包下的表现都还不知道。
- 真实端到端延迟未测。首句出声的预算估计在 600~1500ms，但没有实测数据。
- 噪声环境和行业术语下的 ASR 准确率未测。
- TTS 尚未选型。这是目前唯一没有自有实现的一环。

## 接下来

同一个底座上还能长出：

1. **AI 实时参会** —— `avEngineKit.joinConference(...)` 已经在 SDK 里。minutes-server 证明了
   机器人能实时拿到会议音频，但它只做会后纪要。接上这套链路，AI 就能在会议里被 @ 到就开口说话。
2. **AI 外呼** —— `startPrivateCall` 路径已经接好了，批量回访、通知、满意度调查。
3. **数字人视频通话** —— `FileVideoCapture` / `ImageVideoSink` 说明视频通道也是开放的。
