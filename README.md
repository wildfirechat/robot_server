# 野火IM机器人应用

作为野火IM机器人应用的演示，本工程演示机器人应用的接口。当前已接入 OpenAI 兼容的大模型（默认使用 DeepSeek）：用户发送消息后，机器人会带上会话最近的聊天记录调用大模型，并将回答以**流式文本消息**回复给用户。

## 功能说明

- 私聊消息直接回复；群聊中 @机器人 时回复（@全体不回复）。
- 文本消息交给大模型处理；图片、语音、位置、视频等消息仍回复固定的提示文案。
- 支持 `/list` 和 webhook 命令（与原示例一致）。
- 用户消息与机器人回答都会写入数据库，作为后续对话的上下文。
- 每次调用大模型时携带该会话最近 `robot.llm.history_limit` 条记录（默认 30，可配置）。
- 流式回复：大模型边生成边推送，消息按间隔合并，最后发送一条生成完成的消息。

## 配置

工程的 `config` 目录下有两个配置文件：`application.properties`（服务端口、数据库）和 `robot.properties`（机器人、大模型）。部署时请把 `config` 目录放到 jar 包所在目录。

### 1. 大模型配置（config/robot.properties）

```properties
# 是否启用大模型回复
robot.llm.enabled=true
# OpenAI 兼容接口地址（默认 DeepSeek），程序会自动拼接 /chat/completions
robot.llm.base_url=https://api.deepseek.com
# API Key，本地 vLLM/Ollama 等无鉴权服务可留空
robot.llm.api_key=sk-your-api-key
# 模型名称（DeepSeek 默认 deepseek-chat，也可用 deepseek-reasoner）
robot.llm.model=deepseek-chat
# 系统提示词
robot.llm.system_prompt=你是野火IM的智能助手，名字叫小火，正在野火IM中为用户解答问题。请使用简洁的中文回答。如需提供野火IM的资料，可以告诉用户：项目地址 https://github.com/wildfirechat ，文档 https://docs.wildfirechat.cn ，论坛 https://bbs.wildfirechat.cn ，官网 https://wildfirechat.cn 。
# 温度与最大 token 数
robot.llm.temperature=0.7
robot.llm.max_tokens=2048
# 请求超时（秒）
robot.llm.timeout_seconds=120
# 每次请求携带的历史消息条数（包含当前用户消息），默认 30
robot.llm.history_limit=30
# 是否流式调用
robot.llm.stream=true
# 流式消息两次推送的最小间隔（毫秒），<=0 时使用默认 500
robot.llm.stream_interval_ms=500
```

常见的 `base_url` 示例：

| 服务 | base_url |
| --- | --- |
| DeepSeek（默认） | `https://api.deepseek.com` |
| OpenAI | `https://api.openai.com/v1` |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` |
| 本地 vLLM | `http://127.0.0.1:8000/v1` |
| 本地 Ollama | `http://127.0.0.1:11434/v1` |

也可以直接填写完整的 `.../chat/completions` 地址。

### 2. 数据库配置（config/application.properties）

默认使用 **H2 文件数据库**，无需安装，数据保存在工作目录的 `./data` 下，开箱即用：

```properties
spring.datasource.url=jdbc:h2:file:./data/robot;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
```

生产环境建议切换为 MySQL（先创建数据库，表结构会在启动时自动创建）：

```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/robot?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=root
spring.datasource.password=123456
```

聊天记录表 `chat_message` 会自动创建，主要字段：

| 字段 | 说明 |
| --- | --- |
| `conv_type` | 会话类型，0 单聊、1 群聊 |
| `conv_target` | 会话目标，单聊为用户ID，群聊为群ID |
| `sender` | 发送者ID（机器人回答时为机器人ID） |
| `role` | `user` / `assistant` |
| `content` | 消息内容 |
| `send_time` | 消息时间 |

### 3. 流式回复说明

流式回复使用野火IM的流式文本消息（生成中 + 生成完成），客户端需要支持该类消息才能看到逐字输出的效果。为避免消息条数过多，`stream_interval_ms` 内的增量会合并后再推送。

## 编译

```
mvn package
```

## 运行

在 `target` 目录找到 `robot-XXXX.jar`，把 jar 包和放置配置文件的 `config` 目录放到一起，然后执行下面命令：

```
java -jar robot-XXXXX.jar
```

## 测试

```
mvn test
```

测试覆盖了聊天记录的读写、OpenAI 兼容接口的流式/非流式解析，以及「保存用户消息 → 读取最近 30 条历史 → 流式回复 → 保存机器人回答」的完整链路（使用本地桩服务，不依赖真实大模型和 IM 服务）。

## 使用到的开源代码

1. [TypeBuilder](https://github.com/ikidou/TypeBuilder) 一个用于生成泛型的简易Builder

## LICENSE
UNDER MIT LICENSE. 详情见LICENSE文件
