# 野火IM 智能助手应用

野火IM 智能助手服务，已接入 OpenAI 兼容的大模型（默认 DeepSeek），并提供 **智能助手管理后台**：机器人、大模型、知识库和网站抓取参数都在后台配置，配置保存在 PostgreSQL 中，保存后立即生效；后台同时提供消息、会话、大模型调用统计和消息检索。

## 功能说明

- 私聊消息立即回复；群聊中只有 @机器人 时才回复（@全体不回复），未被 @ 的群文本消息只写入数据库作为后续上下文，不回复。
- 回复前发送 typing 消息（SDK 的 `TypingMessageContent`，类型 91）作为「正在思考」的即时反馈；typing 有效期 6 秒，等待首个回复期间每 7 秒刷新一次（6 秒 + 1 秒），收到首段内容后停止刷新；群聊回复自动带上 `@发送者`。
- typing 消息需要 IM 服务端和客户端支持，不支持时只会少一个提示，不影响正常回复。
- 文本消息交给大模型处理；图片、语音、位置、视频等消息仍回复固定的提示文案。
- 每次调用大模型时携带该会话最近 N 条记录（N 可在管理后台配置，默认 30）。
- 流式回复：大模型边生成边推送，消息按间隔合并，最后发送一条生成完成的消息。
- 单条用户消息超过 8000 字符会截断；单次 prompt（system + 历史 + 当前消息）上限 32000 字符，超长历史自动裁剪。
- 未开启大模型时，提示语也以流式消息发送：每 200ms 追加 2 个字符，最后发送一条完成消息。
- 消息处理队列有界，队列满时 `/robot/recvmsg` 返回 503，避免任务无限堆积；概览统计缓存 3 秒。
- 支持知识库 / RAG：后台上传文档、粘贴文本或整站抓取网页，机器人先检索资料再回答，并带 `[1][2]` 引用。
- 整站抓取只抓输入地址的完全相同域名，不抓子域名、不抓外部链接；默认最多 5 层、1000 页。
- 管理后台 `/admin`（智能助手管理后台）：配置机器人、大模型、知识库参数；查看统计；管理知识库；分页查看/筛选历史消息；修改管理员密码。
- 服务只提供 `/robot/recvmsg` 一个 IM 回调接口，**仅部署在内网供 IM 服务器调用**。

## 环境要求

- JDK 17 及以上（工程基于 Spring Boot 3.5.16）
- Maven 3.9 及以上
- PostgreSQL 14 及以上，并安装 pgvector 扩展

## 首次启动

1. 创建 PostgreSQL 数据库和 pgvector 扩展：
   ```sql
   CREATE DATABASE robot;
   \c robot
   CREATE EXTENSION IF NOT EXISTS vector;
   ```
2. 修改 `config/application.properties`，填写端口和 PostgreSQL 连接（见下文）。
3. 启动服务：
   ```
   java -jar robot-XXXXX.jar
   ```
4. 管理员账号在首次启动时自动创建：
   - 用户名默认 `admin`，可通过环境变量 `ROBOT_ADMIN_USERNAME` 指定；
   - 密码优先读取环境变量 `ROBOT_ADMIN_PASSWORD`（至少 10 位且同时包含字母和数字）；
   - 未设置或强度不足时，生成随机初始密码并打印在启动日志中；使用随机密码首次登录会**强制修改密码**后才能进入后台。
5. 浏览器访问 `http://<服务地址>:<端口>/admin` 登录，进入“配置管理”填写机器人 IM 配置和大模型配置，然后启用大模型。
6. 在 IM 管理端将机器人消息回调地址配置为 `http://<服务地址>:<端口>/robot/recvmsg`。

> 未完成机器人 Secret 和大模型配置前，机器人不会正常工作；概览页会有配置提醒。

> 忘记管理员密码：停止服务后执行 `DELETE FROM admin_user WHERE username = 'admin';`，然后设置 `ROBOT_ADMIN_PASSWORD` 环境变量并重启；不设置时会生成随机密码并打印在启动日志中。使用自定义用户名时替换 SQL 中的 `admin`。

## 配置文件

配置文件里只保留端口和数据库配置，机器人、大模型等业务配置全部在管理后台维护。

`config/application.properties`：

```properties
server.port=8883

# PostgreSQL 连接；先创建数据库和 pgvector 扩展，表结构启动时自动创建
spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/robot
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=robot
spring.datasource.password=change-me
spring.datasource.hikari.maximum-pool-size=10
```

所有业务配置保存在数据库 `app_config` 表中，包括：

- 机器人：`im_id`、`im_name`、`im_url`、`im_secret`
- 大模型：是否启用、接口地址、API Key、模型、系统提示词、温度、最大 token 数、超时、历史条数、是否流式、流式间隔
- 数据保留：历史数据保留天数（0 表示不自动清理，默认 0）
- 知识库：是否启用、Embedding 地址/Key/模型、TopK、相似度阈值、参考资料最大字符数、分块大小/重叠

## 管理后台

访问 `/admin`（智能助手管理后台），包含：

- **概览**：今日消息、今日用户消息、今日机器人回复、今日大模型调用、失败次数、会话总数、累计消息、累计调用、成功率、平均耗时；最近 7 天消息/调用图表；最近会话和最近消息。
- **消息**：分页查看所有聊天记录；先选择会话类型（单聊/群聊），点击“搜索用户”或“搜索群组”弹窗选择对象，页面显示用户/群名称而不是内部 ID，可调整每页条数。
  - 用户支持昵称、手机号、邮箱搜索；手机号走 IM 精确查询，昵称/邮箱覆盖最近有消息的用户；
  - 群组搜索覆盖最近有消息的群组；
  - IM SDK 当前没有全局邮箱/昵称/群名称搜索接口，因此不是全量通讯录搜索。
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

- 上传文件：支持 `txt`、`md`、`csv`、`json`、`pdf`、`docx`，单文件不超过 50MB；
- 粘贴文本：适合少量 FAQ、产品说明；
- 抓取网站：输入一个 URL，只抓完全相同域名和端口，默认最多 5 层、1000 页；不抓子域名和外部链接；
- 文档异步解析、分块、调用 Embedding 接口，状态显示 `PROCESSING / READY / FAILED`；
- 支持删除文档、重新加载向量索引；
- “检索测试”可以直接输入问题，查看召回的片段和相似度。

工作流程：

1. 文档解析或网页正文提取后按配置的分块大小/重叠切分；
2. 调用 OpenAI 兼容的 `/embeddings` 接口生成向量，存入 `kb_chunk`；网页分块同时记录页面 URL；
3. 用户提问时先对问题做 embedding，在 PostgreSQL/pgvector 中做 TopK + 相似度阈值过滤；
4. 召回的片段以 `【参考资料】[1] 标题 ...` 形式插入 system 消息；
5. 大模型根据资料回答，并要求用 `[1][2]` 标注引用；
6. 没有命中或检索失败时继续普通对话，不影响机器人可用性。

配置项（管理后台 → 配置管理 → 知识库 / RAG）：

- `rag.enabled`：总开关；
- Embedding 接口地址/Key：留空复用大模型配置；
- Embedding 模型：如 `text-embedding-3-small`、`bge-m3`；
- TopK、相似度阈值、参考资料最大字符数；
- 分块大小、分块重叠；
- 整站抓取：最大页数（默认 1000）、最大深度（默认 5）、单页/整站大小、最大时长、请求间隔、User-Agent、是否遵守 robots.txt、是否允许内网地址。

注意事项：

- 入库和查询必须使用同一个 Embedding 模型；换模型后需要删除文档并重新上传（向量维度不同）。
- 向量和正文都保存在 PostgreSQL，多个实例共享同一份数据，入库后无需在每个实例刷新内存索引。
- 当前 `kb_chunk.embedding` 使用无维度 `vector` 类型，检索是 pgvector 精确扫描，适合几万级分块；数据量很大时建议固定向量维度并创建 HNSW 索引（见下文）。
- 文档/网页内容属于不可信数据，已在 prompt 中要求模型只把资料当参考、不执行资料中的指令；仍建议对上传权限和知识库内容做管控。
- 整站抓取默认禁止内网/保留地址；内部 Wiki 需要在后台显式勾选“允许抓取内网/私有地址”。抓取会校验同域名、同端口、重定向、响应大小和 robots.txt。
- 整站抓取只解析服务端返回的 HTML，不执行 JavaScript；纯前端渲染、登录后页面、验证码页面抓不到正文。

## 数据库表

启动时自动创建（PostgreSQL），不保留旧版本库的结构兼容：

| 表 | 说明 |
| --- | --- |
| `app_config` | 业务配置，key-value；Secret/API Key 为 AES-GCM 密文 |
| `chat_message` | 聊天记录（用户消息、机器人回答），角色字段为 `msg_role` |
| `llm_call` | 大模型调用记录，用于统计成功率和耗时 |
| `admin_user` | 管理员账号，BCrypt 密码哈希、是否需强制改密 |
| `app_secret` | 配置加密密钥（AES-256），首次启动自动生成 |
| `kb_document` | 知识库文档及处理状态 |
| `kb_chunk` | 文档分块、正文、向量和来源页面 URL |
| `rag_query_log` | 检索记录（命中数、Top 分数、分块 ID、耗时） |

生产环境请为 PostgreSQL 账号设置最小权限，并定期备份数据库。

### 从旧版 H2/MySQL 迁移

应用不会自动迁移旧库，需要单独处理：

- `app_config`、`chat_message`、`llm_call`、`admin_user`、`app_secret`：导出旧库数据后导入 PostgreSQL；
- **`app_secret` 必须一起迁移**，否则 `app_config` 中的 Secret/API Key 密文无法解密，需要重新填写；
- `kb_document`、`kb_chunk`：可以重新上传文档；如果要保留向量，需要把旧库的 `embedding` BLOB 转成 pgvector 文本格式 `[0.1,0.2,...]` 后导入；
- 最省事的方式是只迁移配置和管理员账号，知识库文档重新上传重建向量。

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

测试覆盖：配置读写/加密与校验、聊天记录、大模型流式/非流式解析、历史上下文、群聊 @ 和未 @ 记录、typing 消息、长输入裁剪、数据保留、知识库分块/PDF/Word 解析/检索、整站抓取范围/深度/页数/robots/内网拦截、调用统计、管理员密码、强制改密标记、登录限流、管理后台权限/CSRF 等。单元测试使用 H2，仅在 test scope，不参与生产运行。

## 安全注意事项

- `/robot/recvmsg` 按内网调用设计，没有签名校验；请用防火墙/IP 白名单只允许 IM 服务器访问该端口，不要暴露到公网。
- 管理后台和机器人接口共用端口，请确保该端口只在内网可达。
- 生产环境使用 PostgreSQL，限制数据库账号权限，并定期备份数据库。
- 首次登录后立即修改管理员密码；不要使用弱密码。

## 性能与容量

- 默认异步线程 10~20 个、队列 500；单次大模型调用超时默认 120 秒，高并发场景请按实际压力调整 `ExecutorConfig` 和数据库连接池。
- 管理后台可配置历史数据保留天数（默认 0，即不自动清理）；大于 0 时每天凌晨 3:30 清理过期的 `chat_message`、`llm_call`、`rag_query_log`。
- 消息页使用分页查询：`chat_message` 有 `(conv_type, conv_target, id)`、`(sender, id)`、`(send_time)`、`(msg_role, send_time)` 索引，分别覆盖会话过滤、用户过滤、时间范围/保留清理和统计；全量列表按主键倒序，走主键索引。
- 消息页需要精确总数，会执行一次 `COUNT(*)`；数据量特别大且深分页较多时，考虑改为游标分页或汇总表。
- 概览页统计会扫描消息/调用表，已做 3 秒缓存；数据量特别大时建议做定时汇总表。
- 知识库向量和正文都在 PostgreSQL。`embedding` 目前是无维度 `vector`，检索走精确扫描；如果分块数达到几十万以上，建议先固定维度，再建 HNSW 索引：
  ```sql
  -- 仅在 embedding 维度一致时执行；已有数据需匹配
  ALTER TABLE kb_chunk ALTER COLUMN embedding TYPE vector(1024);
  CREATE INDEX idx_kb_chunk_embedding ON kb_chunk
    USING hnsw (embedding vector_cosine_ops);
  ```
  维度要和 Embedding 模型一致，例如 bge-m3 是 1024、text-embedding-3-small 是 1536。

## LICENSE
UNDER MIT LICENSE. 详情见LICENSE文件
