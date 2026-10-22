# OpenAI 文件上传与 Chat Completions 第一阶段验证

日期：2026-10-22。需求依据：[云文件与附件独立状态卡](cloud-files-and-attachment-status.md)。

## 1. 当前结论

**本地验证通过；OpenAI 服务端实测受阻，暂不开放生产文件上传能力。**

- OpenAI Java SDK `4.41.0` 可以发出带有效期的上传请求，以及 Chat Completions 的 `file_id` 引用请求，均已在本地 HTTP 模拟服务验证。
- 当前项目实际使用的 ADK `1.4.0` Chat Completions 适配器不具备上述文件引用转换能力。其底层是 Ktor，不能把“当前使用 ADK”视为“当前已使用 OpenAI Java SDK”。
- 当前环境没有配置 OpenAI 凭据和验证模型；对官方 API 和文档域名的连接被代理策略拒绝。因此本轮没有完成任何文件格式的 OpenAI 官方读取实测。
- 用户提供的其他厂商凭据不能作为 OpenAI 凭据使用，也不能根据兼容聊天接口推定其文件能力。
- 未修改生产协议、模型工厂、正式 UI 或持久化。默认仍是 Chat Completions，没有增加 Responses 自动切换。

## 2. 证据分层

| 层级 | 本轮结果 | 能证明的范围 |
| --- | --- | --- |
| 官方 SDK 类型与源码 | 已检查固定版本 `4.41.0` | 已建模的字段、用途及注释约束；不证明服务端实际接受 |
| SDK → 本地 HTTP 服务 | 10 项验证通过 | 实际请求路径、multipart／JSON 结构、解析、流式与错误映射 |
| 当前应用 ADK 边界 | 3 项定向集成测试通过 | 当前模型工厂的协议与端点行为，以及图片／非图片转换限制 |
| OpenAI 官方 API | 未执行上传或模型请求 | 无格式读取成功或失败的服务端结论 |
| 真机及正式集成 | 本阶段未执行 | 无正式界面、后台恢复或 Android SDK 集成结论 |

### 2.1 SDK 的 10 项本地验证

| 验证项 | 结果 |
| --- | --- |
| 1 天过期上传 | `POST /v1/files`，multipart，`purpose=user_data`，86400 秒 |
| 7 天过期上传 | 同上，604800 秒 |
| 30 天过期上传 | 同上，2592000 秒 |
| 文件 ID 引用 | `POST /v1/chat/completions`，`type=file`，`file.file_id` |
| 独立请求复用同一 ID | 没有重复调用上传接口；仅证明 SDK 请求构造，不证明服务端复用 |
| 文件引用流式请求 | 使用原 Chat Completions 路径、发送 stream 标记、解析模拟 SSE |
| 模拟 HTTP 400 | SDK 保留状态码，关闭隐式重试后不重复请求 |
| 模拟 HTTP 404 | 同上；不是官方文件过期错误码的证据 |
| 模拟 HTTP 429 | 同上；不自动重试增加费用或重复上传 |
| 清理本次创建的文件 | `DELETE /v1/files/{id}` 与响应解析正确，仅连接本地服务 |

捕获到的上传字段名为：

```text
file
purpose
expires_after[anchor]
expires_after[seconds]
```

`anchor` 为 `created_at`。SDK 注释声明期限范围为 3600～2592000 秒，即 1 小时～30 天；本地 1／7／30 天序列化验证不能代替服务端参数验证，也不能证明未来自动过期任务已实际执行。

捕获到的聊天文件片段为：

```json
{"type":"file","file":{"file_id":"file-probe-7"}}
```

请求中未使用 Responses 的 `input_file` 结构，未将文件 ID 拼接为 URL。

### 2.2 当前 ADK 的 3 项定向验证

运行现有 `ChatCompletionsIntegrationTest` 中的以下用例，3 项通过，0 失败：

- `chatCompletionsModelUsesConfiguredEndpointKeyAndJsonOutput`：保持配置的 Chat Completions 路径、凭据及 JSON 输出行为。
- `localImageIsResolvedBeforeOfficialConversion`：本地图片转成 `image_url` 的 base64 data URL。
- `audioAndPdfInUserHistoryAreRejectedBeforeHttp`：历史中的音频、PDF 在 HTTP 请求前被拒绝。

ADK 发布源码中的用户内容转换仅输出文字和图片，明确拒绝非图片媒体。不能把 PDF 上传后简单改成 `FileData(fileUri="file-...")` 交给现有转换器；它不会自动变成 `file.file_id`。

## 3. 格式与能力范围

| 内容／能力 | 已获得的证据 | OpenAI 官方实测状态 |
| --- | --- | --- |
| PDF 文件 ID 输入 | SDK 有文件片段，本地 wire 验证通过；现有 ADK 会拒绝 PDF 内联输入 | 待验证 |
| TXT、Markdown、CSV | 已生成可复现的合成样本 | 待验证，不能由 Files 上传范围推断聊天读取范围 |
| DOCX、XLSX | 已生成 OOXML 合成样本并通过结构检查 | 待验证，不能套用 Responses 或 Assistants 的格式清单 |
| PNG、JPEG 的文件 ID 输入 | 已生成图片样本；SDK 普通图片输入建模为 URL／base64，而非直接图片 ID | 待验证；不能将 `file-...` 当作 `image_url.url` |
| 1／7／30 天期限 | SDK 字段及本地 multipart 验证通过 | 待验证服务端是否接受，以及返回的到期时间 |
| 跨会话复用文件 ID | 本地独立请求引用验证通过 | 待验证同一有效 ID 的独立请求读取 |
| 文件引用流式回复 | 本地 SSE 验证通过 | 待验证真实模型读取与流式回复 |
| 自动到期及失效错误 | 未等待服务端实际到期；仅有模拟错误码 | 待验证，不将删除与自然到期混为一谈 |
| 其他厂商 | 不在本轮 OpenAI 证据范围内 | 必须单独验证，不能混用凭据和结论 |

上表的“待验证”不等于“不支持”。目前不能给出任何“OpenAI 已实测支持”的格式清单。

## 4. 官方实测阻塞

通过环境配置与受控请求观察到：

- 未配置 `OPENAI_API_KEY`，未指定验证模型。
- `api.openai.com`、`developers.openai.com` 不在当前网络放行范围。
- 对 `https://api.openai.com/v1/models` 和官方 PDF 输入文档的有界请求均得到代理 CONNECT 的 `403 Forbidden`。

该 403 发生在代理层，不能表述为 OpenAI 拒绝文件上传、凭据无效或模型不支持。未绕过代理、未关闭 TLS 校验，也未尝试从 Android 安全存储提取用户凭据。

恢复实测需要通过环境配置绑定正确厂商的密钥、指定模型并放行对应官方主机。不要在聊天、源码、文档、命令参数或普通日志中填写密钥。

## 5. 已准备的真实验证探针

目录：`temp/openai-files-validation/`，与生产应用隔离；具体命令与约束见该目录 `README.md`。

- 使用官方 OpenAI Java SDK，固定目标为 `https://api.openai.com/v1`；其他厂商凭据不适用。
- 8 种小型合成样本：PDF、TXT、Markdown、CSV、DOCX、XLSX、PNG、JPEG。
- 每个样本带独立随机标记，仅写在文件内部，提示词不泄露该标记。分开记录上传接受、聊天接受、实际读出标记，避免将上传成功误判为模型读取成功。
- PDF 额外验证同一 ID 的独立请求复用与流式读取；额外核对 1 天、30 天期限。
- 关闭 SDK 隐式重试；鉴权、限流或传输异常中止后续矩阵。结束时只清理本次创建的文件，并记录是否清理成功。
- 日志与结果不保存密钥、响应头、完整服务端错误正文或原始远端文件 ID。

默认完整矩阵至多上传 10 个小文件、发送 10 次模型请求，每次最大输出 128 token。真实调用有 API 费用。本轮运行 `live` 模式时仅生成了“缺少前提”的记录，没有发送真实上传或模型请求。

## 6. 正式接入前的门槛

1. 先获得正确厂商、指定模型的真实“上传 → 文件 ID → Chat Completions 读取”证据，报告实际通过和被拒绝的格式与原因。
2. 根据实测确定能力矩阵。首期若只有部分格式能闭环，就只开放这些格式；其他格式明确提示，不自动切换 Responses。
3. 为支持的格式增加 SDK 请求转换适配，保留现有流式、工具调用、历史、取消和错误恢复语义。需要在 Android 依赖环境再验证，不能仅凭独立 JVM 探针通过直接发布。
4. 再接入安全配置、逐文件状态、共享引用记录、过期恢复与已确认的界面。

本轮不提前修改正式聊天行为，也不提前将任何服务标记为支持云文件。

## 7. 可复现证据位置

| 文件 | 内容 |
| --- | --- |
| `temp/openai-files-validation/mock-results.json` | 10 项 SDK 本地验证结果及实际字段 |
| `temp/openai-files-validation/live-results.json` | 真实调用前提缺失记录 |
| `temp/openai-files-validation/source-fetch-results.json` | 官方 SDK 源码下载与 API／文档预检结果 |
| `temp/openai-files-validation/adk-boundary-tests.log` | 3 项现有 ADK 测试的构建日志 |
| `data/agent/build/test-results/testDebugUnitTest/` | 对应 JUnit 结果 |
| `temp/openai-files-validation/fixtures/manifest.json` | 8 个合成样本的格式、随机标记及摘要 |

源码依据：[OpenAI SDK Files 参数](https://github.com/openai/openai-java/blob/v4.41.0/openai-java-core/src/main/kotlin/com/openai/models/files/FileCreateParams.kt)、[Chat Completions 文件片段](https://github.com/openai/openai-java/blob/v4.41.0/openai-java-core/src/main/kotlin/com/openai/models/chat/completions/ChatCompletionContentPart.kt)、[图片片段](https://github.com/openai/openai-java/blob/v4.41.0/openai-java-core/src/main/kotlin/com/openai/models/chat/completions/ChatCompletionContentPartImage.kt)。ADK 依据为 Maven Central 发布的 `google-adk-kotlin-integrations-openai-android:1.4.0` 源码包。
