# SDK 设计与独立迁出

## 与官方一致的资源模型

官方 Python SDK 使用 `Sandbox.create/connect` 构建沙箱对象，通过 `sandbox.commands`、`sandbox.files` 分组访问资源。这里保留相同概念及 camelCase 命名，同时增加可复用 `E2BClient`：它拥有配置和 HTTP 连接池，允许在同一客户端上操作多个沙箱。

`Sandbox.create(config, options)` / `Sandbox.connect(id, config)` 是 Java 可用的静态便捷方法。创建失败时关闭内部客户端，成功后由沙箱的 close 释放内部客户端。`client.create()` 得到的沙箱不拥有客户端。close/kill/pause 的区别见 README；暂停不会悄悄改变现有句柄的凭据，恢复后应通过 `connect` 取得新对象。

Java 入口采用普通类、getter、JDK List/Map、`@JvmStatic`、`@JvmOverloads` 和 Closeable。配置使用 builder，选项使用不可变 Kotlin data class 加 Java 构造重载。公开 API 不暴露 Gson/OkHttp 类型。首版提供同步 API，对 Java 使用者不强制引入协程概念；以后另设 `e2b-coroutines` 扩展模块，将真正可取消的异步 HTTP transport 与 suspend/Flow 集成，避免用 `runBlocking` 伪装同步接口。

## 三类通信

| 层 | 地址/格式 | 认证 |
| --- | --- | --- |
| Control plane | `https://api.<domain>`，REST JSON；创建/列表和连接选择非废弃 v2 路径 | `X-API-Key` |
| 沙箱命令/元数据 | 官方域名用 `https://sandbox.<domain>`；自建域名用 `https://49983-<id>.<domain>`。Connect unary JSON 与 `application/connect+json` 流 | `E2b-Sandbox-Id`、`E2b-Sandbox-Port`、`X-Access-Token`，指定用户时另用 Basic |
| 文件正文 | envd `/files`，GET 下载、multipart POST 上传 | envd token；操作用户使用 `username` query |

默认域名/密钥来自 `E2B_DOMAIN`、`E2B_API_KEY`。API 和 envd URL 可独立覆盖，便于自建服务和 MockWebServer 测试。传输不共用认证 header map，不跟随重定向，不自动重试有副作用的调用。调用者提供的 API URL 是被信任的密钥接收端，应该使用 HTTPS；本地 HTTP 覆盖用于测试。

envd 0.4.0 前默认用户由 SDK 填 `user`，之后交由服务端默认值决定；显式用户始终优先。上传使用通用 multipart，避免把依赖 envd 0.5.7 的 octet-stream 优化作为首版前置条件。

Connect 流包含 flag 字节、四字节大端长度和 JSON payload。普通帧 flag=0，结束帧 flag=2，拒绝压缩/未知标志和过大、截断帧。protobuf JSON 的 bytes 是 base64，int64 可能是字符串。命令先验证 start/PID，收集 stdout/stderr，等待 end 和最终 trailer，再生成结果；最后 trailer 中的 RPC error 不会被提前退出结果遮蔽。stdout/stderr 先合并字节再解码，避免 UTF-8 跨帧乱码。响应未知字段自然忽略。

总命令 deadline 通过 OkHttp call timeout 与 Connect-Timeout-Ms 控制；其过期会关闭本地监听，不保证远端进程被终止。要停止进程应显式 kill。一般请求使用请求超时，沙箱 TTL 使用远端 timeout 字段，均明确使用秒。首版错误保留状态与 RPC code、限流 retry hint，省略可能包含秘密的响应正文。

## 仓库边界

此目录是独立 Gradle JVM 工程，不是 Android library，也没有根项目 project dependency。它有自己的 settings、构建依赖版本、wrapper、测试、README、Apache-2.0 LICENSE 和 Maven publication。宿主不需要 Android SDK 即可构建此库；库也不读取上层版本目录或 Gimi 的 Kotlin 2.4 构建约定。

迁出时复制整个 `sdk/e2b/` 目录即可，排除 `.gradle/`、`.kotlin/`、`build/`。正式发布前补齐仓库 URL、开发者信息、签名、公共坐标和 CI。当前仅生成本地发布产物，不向公共仓库发布；版本也不跟随 Gimi 的应用版本。

未来宿主接入可在根 settings 增加 `includeBuild("sdk/e2b")`，Gradle composite 自动将 `dev.e2b.community:e2b-sdk` 依赖替换为源码构建；或者独立构建并使用 Maven 产物。迁出后删除 includeBuild，换正式 Maven 坐标即可，SDK 包名不变。

宿主业务适配器应放在拥有沙箱能力的 data 模块，对 domain 暴露项目自己的接口，不让 ViewModel/domain 直接依赖 SDK 或网络类型。密钥存储由宿主负责；库不添加 Android Settings、Keystore、Hilt 或 UI。此阶段只创建库，不提前引入空的 domain/data 占位模块，也不更改应用网络或权限行为。

## 验证策略

MockWebServer 覆盖 REST method/path/body、分页 token 和过滤器编码、control/envd header 隔离、重定向、not-found/already-paused/权限/限流分支；Connect 请求帧、退出状态、trailer 错误、缺失/截断/超限响应和跨帧 UTF-8；文件二进制传输和 protobuf int64；Java 源文件编译及资源关闭。

发布验证同时生成 JAR、sources JAR 和 POM，检查依赖不含 Android/Gimi。当前工程没有宿主接入变更，独立 SDK 验证不声称验证整个 Android 应用。账号联调应在未来有测试凭据和 E2B 域名网络访问时单独执行，并使用短 TTL、显式清理且不把密钥纳入测试源码。
