# 野火IM机器人应用

野火IM机器人示例服务，已接入 OpenAI 兼容的大模型（默认 DeepSeek），并带有一个管理后台：机器人和大模型的全部参数都在后台配置，配置保存在数据库中，保存后立即生效；后台同时提供消息、会话和大模型调用的统计。

## 功能说明

- 私聊消息直接回复；群聊中 @机器人 时回复（@全体不回复）。
- 文本消息交给大模型处理；图片、语音、位置、视频等消息仍回复固定的提示文案。
- 每次调用大模型时携带该会话最近 N 条记录（N 可在管理后台配置，默认 30）。
- 流式回复：大模型边生成边推送，消息按间隔合并，最后发送一条生成完成的消息。
- 管理后台 `/admin`：配置机器人、大模型、历史条数、流式参数；查看统计；修改管理员密码。
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

- 默认在首次启动时生成 `data/config.key`（600 权限），用于加密 Secret/API Key，请和数据库一起备份。
- 也可以通过环境变量 `ROBOT_CONFIG_KEY` 提供 Base64 编码的 32 字节密钥，或通过 `ROBOT_CONFIG_KEY_FILE` 指定密钥文件路径。
- 密钥丢失后已保存的 Secret/API Key 无法解密，需要重新在管理后台填写。

## 数据库表

启动时自动创建（H2/MySQL 通用），不保留旧版本库的结构兼容：

| 表 | 说明 |
| --- | --- |
| `app_config` | 业务配置，key-value；Secret/API Key 为 AES-GCM 密文 |
| `chat_message` | 聊天记录（用户消息、机器人回答），角色字段为 `msg_role` |
| `llm_call` | 大模型调用记录，用于统计成功率和耗时 |
| `admin_user` | 管理员账号，BCrypt 密码哈希、是否需强制改密 |

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

测试覆盖：配置读写/加密与校验、聊天记录、大模型流式/非流式解析、历史上下文、调用统计、管理员密码、强制改密标记、登录限流、管理后台权限/CSRF 等。

## 安全注意事项

- `/robot/recvmsg` 按内网调用设计，没有签名校验；请用防火墙/IP 白名单只允许 IM 服务器访问该端口，不要暴露到公网。
- 管理后台和机器人接口共用端口，请确保该端口只在内网可达。
- 生产环境使用 MySQL 并限制数据库账号权限；定期备份数据库和 `data/config.key`（或保管好 `ROBOT_CONFIG_KEY`）。
- 首次登录后立即修改管理员密码；不要使用弱密码。

## LICENSE
UNDER MIT LICENSE. 详情见LICENSE文件
