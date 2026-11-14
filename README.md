# 野火IM机器人应用

野火IM机器人示例服务，已接入 OpenAI 兼容的大模型（默认 DeepSeek），并带有一个管理后台：机器人和大模型的全部参数都在后台配置，配置保存在数据库中，保存后立即生效；后台同时提供消息、会话和大模型调用的统计。

## 功能说明

- 私聊消息立即回复；群聊中只有 @机器人 时才回复（@全体不回复），未被 @ 的群文本消息只写入数据库作为后续上下文，不回复。
- 回复前发送 typing 消息（SDK 的 `TypingMessageContent`，类型 91）作为「正在思考」的即时反馈；群聊回复自动带上 `@发送者`。
- typing 消息需要 IM 服务端和客户端支持，不支持时只会少一个提示，不影响正常回复。
- 文本消息交给大模型处理；图片、语音、位置、视频等消息仍回复固定的提示文案。
- 每次调用大模型时携带该会话最近 N 条记录（N 可在管理后台配置，默认 30）。
- 流式回复：大模型边生成边推送，消息按间隔合并，最后发送一条生成完成的消息。
- 单条用户消息超过 8000 字符会截断；单次 prompt（system + 历史 + 当前消息）上限 32000 字符，超长历史自动裁剪。
- 消息处理队列有界，队列满时 `/robot/recvmsg` 返回 503，避免任务无限堆积；概览统计缓存 3 秒。
- 支持知识库 / RAG：后台上传文档或粘贴文本，机器人先检索资料再回答，并带 `[1][2]` 引用。
- 管理后台 `/admin`：配置机器人、大模型、知识库参数；查看统计；管理知识库；修改管理员密码。
- 服务只提供 `/robot/recvmsg` 一个 IM 回调接口，**仅部署在内网供 IM 服务器调用**。

## 环境要求

- JDK 17 及以上（工程基于 Spring Boot 3.5.16）
- Maven 3.9 及以上

## 首次启动

1. 修改 `config/application.properties`，只保留端口和数据库配置（见下文）。
2. 启动服务：
   ```
   java -jar robot-XXXXX.jar
   ```
3. 管理员账号在首次启动时自动创建：
   - 用户名默认 `admin`，可通过环境变量 `ROBOT_ADMIN_USERNAME` 指定；
   - 密码优先读取环境变量 `ROBOT_ADMIN_PASSWORD`（至少 10 位且同时包含字母和数字）；
   - 未设置或强度不足时，生成随机初始密码并打印在启动日志中；使用随机密码首次登录会**强制修改密码**后才能进入后台。
4. 浏览器访问 `http://<服务地址>:<端口>/admin` 登录，进入“配置管理”填写机器人 IM 配置和大模型配置，然后启用大模型。
5. 在 IM 管理端将机器人消息回调地址配置为 `http://<服务地址>:<端口>/robot/recvmsg`。

> 未完成机器人 Secret 和大模型配置前，机器人不会正常工作；概览页会有配置提醒。

> 忘记管理员密码：停止服务后删除 `admin_user` 表中的账号记录，重启服务会按环境变量或随机密码重新初始化。

## 配置文件

配置文件里只保留端口和数据库配置，机器人、大模型等业务配置全部在管理后台维护。

`config/application.properties`：

```properties
server.port=8883

# 默认 H2 文件数据库，免安装，数据在 ./data 下
spring.datasource.url=jdbc:h2:file:./data/robot;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# 生产环境建议切 MySQL（先创建数据库，表结构启动时自动创建）
#spring.datasource.url=jdbc:mysql://127.0.0.1:3306/robot?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
#spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
#spring.datasource.username=root
#spring.datasource.password=your-password
```

所有业务配置保存在数据库 `app_config` 表中，包括：

- 机器人：`im_id`、`im_name`、`im_url`、`im_secret`
- 大模型：是否启用、接口地址、API Key、模型、系统提示词、温度、最大 token 数、超时、历史条数、是否流式、流式间隔
- 数据保留：历史数据保留天数（0 表示不自动清理，默认 0）
- 知识库：是否启用、Embedding 地址/Key/模型、TopK、相似度阈值、参考资料最大字符数、分块大小/重叠

## 管理后台

访问 `/admin`，包含：

- **概览**：今日消息、今日用户消息、今日机器人回复、今日大模型调用、失败次数、会话总数、累计消息、累计调用、成功率、平均耗时；最近 7 天消息/调用图表；最近会话和最近消息。
- **配置管理**：修改机器人和大模型全部参数；Secret/API Key 不回显，留空表示保持原值，API Key 支持清除。
- **修改密码**：修改管理员登录密码。

### 安全设计

- 管理员密码使用 BCrypt（strength 12）哈希存储，数据库中不保存明文。
- 机器人 Secret 和大模型 API Key 使用 AES-256-GCM 加密后存入 `app_config`，不会以明文落库。
- 表单登录 + CSRF 防护；管理后台所有写操作都校验 CSRF Token。
- 登录成功后更换 Session ID，防会话固定；会话 30 分钟超时；同一管理员只保留一个会话，新登录会使旧会话失效。
- 会话 Cookie 使用 HttpOnly + SameSite=Lax，并设置独立 Cookie 名称。
- 登录失败限制：同一 IP 连续失败 10 次锁定 10 分钟（内存计数，重启重置）。
- 使用随机初始密码首次登录时强制修改密码后才能访问其他后台页面。
- CSP、X-Frame-Options 等安全响应头；管理页面不引用外部资源。
- 配置页和日志均不输出 Secret/API Key 明文。
- 建议只在内部网络开放管理后台；如需跨网访问，请使用 HTTPS 反向代理。

> 内网 HTTP 部署时没有强制 `Secure` Cookie（否则浏览器无法登录）；如通过公网/HTTPS 访问，请在反向代理层强制 HTTPS。

### 配置加密密钥

- 加密密钥保存在数据库 `app_secret` 表中，首次启动自动生成，无需额外密钥文件或环境变量。
- 多实例共享同一个数据库时会自动使用同一把密钥。
- 备份数据库即可同时保留密文和密钥；但数据库泄露也会同时泄露密钥和密文，请严格限制数据库访问权限。
- 不要手动删除 `app_secret` 中的密钥记录，否则已有 Secret/API Key 密文将无法解密，需要重新在管理后台填写。

## 知识库 / RAG

管理后台进入「知识库」页面：

- 上传文件：支持 `txt`、`md`、`csv`、`json`、`pdf`、`docx`，单文件不超过 10MB；
- 粘贴文本：适合少量 FAQ、产品说明；
- 文档异步解析、分块、调用 Embedding 接口，状态显示 `PROCESSING / READY / FAILED`；
- 支持删除文档、重新加载向量索引；
- “检索测试”可以直接输入问题，查看召回的片段和相似度。

工作流程：

1. 文档解析后按配置的分块大小/重叠切分；
2. 调用 OpenAI 兼容的 `/embeddings` 接口生成向量，存入 `kb_chunk`；
3. 用户提问时先对问题做 embedding，在内存向量索引中做 TopK + 相似度阈值过滤；
4. 召回的片段以 `【参考资料】[1] 标题 ...` 形式插入 system 消息；
5. 大模型根据资料回答，并要求用 `[1][2]` 标注引用；
6. 没有命中或检索失败时继续普通对话，不影响机器人可用性。

配置项（管理后台 → 配置管理 → 知识库 / RAG）：

- `rag.enabled`：总开关；
- Embedding 接口地址/Key：留空复用大模型配置；
- Embedding 模型：如 `text-embedding-3-small`、`bge-m3`；
- TopK、相似度阈值、参考资料最大字符数；
- 分块大小、分块重叠。

注意事项：

- 入库和查询必须使用同一个 Embedding 模型；换模型后需要删除文档并重新上传（向量维度不同）。
- 当前使用单机内存向量索引，适合几万级分块；多实例部署时每个实例各自加载，上传后需要刷新索引；更大规模建议替换为 pgvector/Qdrant/Milvus。
- 文档内容属于不可信数据，已在 prompt 中要求模型只把资料当参考、不执行资料中的指令；仍建议对上传权限和知识库内容做管控。

## 数据库表

启动时自动创建（H2/MySQL 通用），不保留旧版本库的结构兼容：

| 表 | 说明 |
| --- | --- |
| `app_config` | 业务配置，key-value；Secret/API Key 为 AES-GCM 密文 |
| `chat_message` | 聊天记录（用户消息、机器人回答），角色字段为 `msg_role` |
| `llm_call` | 大模型调用记录，用于统计成功率和耗时 |
| `admin_user` | 管理员账号，BCrypt 密码哈希、是否需强制改密 |
| `app_secret` | 配置加密密钥（AES-256），首次启动自动生成 |
| `kb_document` | 知识库文档及处理状态 |
| `kb_chunk` | 文档分块、正文和向量 |
| `rag_query_log` | 检索记录（命中数、Top 分数、分块 ID、耗时） |

`./data` 目录已加入 `.gitignore`。生产环境建议使用 MySQL，并为数据库账号设置最小权限。

## 编译与运行

```
mvn package
```

在 `target` 目录找到 `robot-XXXX.jar`，把 jar 包和 `config` 目录放到一起，然后执行：

```
java -jar robot-XXXXX.jar
```

首次启动如需指定管理员密码：

```
ROBOT_ADMIN_PASSWORD='YourStrongPass123' java -jar robot-XXXXX.jar
```

## 测试

```
mvn test
```

测试覆盖：配置读写/加密与校验、聊天记录、大模型流式/非流式解析、历史上下文、群聊 @ 和未 @ 记录、typing 消息、长输入裁剪、数据保留、知识库分块/PDF/Word 解析/检索、调用统计、管理员密码、强制改密标记、登录限流、管理后台权限/CSRF 等。

## 安全注意事项

- `/robot/recvmsg` 按内网调用设计，没有签名校验；请用防火墙/IP 白名单只允许 IM 服务器访问该端口，不要暴露到公网。
- 管理后台和机器人接口共用端口，请确保该端口只在内网可达。
- 生产环境使用 MySQL 并限制数据库账号权限；定期备份数据库。
- 首次登录后立即修改管理员密码；不要使用弱密码。

## 性能与容量

- 默认异步线程 10~20 个、队列 500；单次大模型调用超时默认 120 秒，高并发场景请按实际压力调整 `ExecutorConfig` 和数据库连接池。
- 管理后台可配置历史数据保留天数（默认 0，即不自动清理）；大于 0 时每天凌晨 3:30 清理过期的 `chat_message`、`llm_call`。
- 概览页统计会扫描消息/调用表，已做 3 秒缓存；数据量特别大时建议做定时汇总表。
- 知识库向量索引当前在内存中，文档越多启动/刷新越慢；大规模检索建议迁移到 pgvector/Qdrant/Milvus。

## LICENSE
UNDER MIT LICENSE. 详情见LICENSE文件
