简体中文 · [English](../../en/conversation/vendor-files.md)

# 厂商文件网关（多协议 Files API）

面向开发人员。

本文记录厂商文件能力（上传/列出/查询/删除/下载内容）的**操作性约束与接入步骤**（实现于 2026-11-15）：术语与范围、domain 模型规则、协议分发规则、注册表联动规则，以及新增厂商或新协议时的强制步骤。后续开发必须遵循本文。

## 术语与范围

- **厂商文件网关（VendorFileGateway）**：对厂商 Files API 的统一五能力操作入口（upload / listFiles / getFile / deleteFile / getContent），domain 层唯一接口。
- **协议族（FileProtocol）**：一套厂商 Files API 的线格式约定。当前两个协议族：`OPENAI_COMPATIBLE`（DeepSeek、OpenAI 及一切兼容 OpenAI files 线格式的厂商）与 `ANTHROPIC`（Claude 及兼容其 beta files 格式的厂商）。
- **协议客户端（FileProtocolClient）**：实现单个协议族全部五个能力的 data 层类；厂商差异只有 endpoint 与凭据。
- **文件上传开关**：模型服务实例页的实例级 switch；开启时文档能力为 `FILE_REFERENCE`（厂商 file 引用），关闭时为 `PATH_TEXT`（文档不传输内容、以本地路径文本送达模型）。厂商是否提供 Files 能力由它决定，登记表只回答"用哪套协议"；新建草稿阶段随草稿落盘，落库后直接写仓库。
- **文件引用（VendorFileReference）**：`gimi-file://<vendor>/<fileId>` 私有 URI，fileId 是厂商服务器侧唯一真源；重放会话不重传。

## Domain 模型约束

> 决策依据：把 upload-only 网关演进为五能力接口而不是平行新接口，是因为同一实现类天然共享认证、endpoint 与序列化配置；拆两个接口只会让 DI 与分发翻倍。

- `VendorFileGateway` 的每个方法都把 `vendor` / `endpoint` / `apiKey` 作为**调用参数**传入，而不是按厂商绑定的 Bean。这是"兼容厂商换 endpoint 即可复用"的根基；不要把它改成按厂商的多绑定注入。
- 统一 DTO `VendorFileObject` 只收各家 FileObject 的**交集字段**（id/filename/sizeBytes/mimeType/createdAt/expiresAt）；厂商特有字段（Claude 的 `scope`/`downloadable`、OpenAI 的 `purpose`）不进模型，原样保留在 `raw: JsonObject`。调用方只写一套逻辑；不要让上层出现 `if provider == "claude"`。
- 列表分页统一为 **limit + 不透明 cursor**：实现把自家游标（OpenAI `last_id`、Claude `next_page`）原样塞进 `nextCursor`，调用方原样回传即可翻页，完全不感知各家分页协议。排序参数不进统一接口。
- 失败模型共享顶层枚举 `VendorFileFailureReason`（TIMEOUT / TOO_LARGE / NETWORK / VENDOR_ERROR / UNSUPPORTED_VENDOR / NOT_FOUND）。"文件不存在"必须用 `NOT_FOUND`（上层要程序化清理注册表残留），禁止退化成 `VENDOR_ERROR` + message 字符串解析。
- `VendorFileUpload.purposeHint` 是可空的厂商 purpose 覆盖值（非空时优先）；为空时各协议实现按 MIME 走默认映射。purpose 映射是**实现方私有细节**：DeepSeek 按 MIME 映射（PDF/Office → `rich_layout_recognition`，其余 → `user_data`），OpenAI 默认 `user_data`，Claude 无 purpose 概念直接丢弃。上层不得感知厂商私有枚举。
- `getContent` 返回**落盘后的临时文件路径**（`VendorFileContentResult.Success.localPath`），不是 ByteArray——Android 内存敏感，OpenAI 单文件上限可达 512MB；落盘位置由实现内定，调用方负责消费后释放。
- `UploadedFileRegistry.remove(vendor, fileId)` 不存在时静默幂等；删除联动见下文注册表规则。

## 协议分发与 DI

- 分发收敛在 `DefaultVendorFileGateway` 的 vendor → 协议两级分发：先查 `VendorFileProtocols.byVendor` 拿协议族，再从 `FileProtocolClient` 集合里取 `protocol` 匹配的客户端。未登记厂商返回 `UNSUPPORTED_VENDOR`。
- `VendorFileProtocols.byVendor` 当前登记：`deepseek`/`openai` → OPENAI_COMPATIBLE，`anthropic` → ANTHROPIC。
- DI 形态：`@Binds` 单绑定网关 + `@Provides @IntoSet` 收集协议客户端（`ModelAccessModule.kt`）。不要退回每厂商一个绑定。
- 共享支撑在 `FileClientSupport`：Json 配置、OkHttp（150s 超时，厂商要求 120s 内完成）、ASCII 文件名清洗、错误详情提取。
- DeepSeek 的 `deepseek/file/` 专有实现**已删除**并归并进 `OpenAiProtocolFileClient`；禁止重新创建厂商专有的文件实现类。

## 各协议差异点

| 能力 | OpenAI 兼容协议 | Anthropic 协议 |
|---|---|---|
| 认证 | `Authorization: Bearer` | `x-api-key` + `anthropic-version` |
| 上传 purpose | 必填（`rich_layout_recognition`/`user_data`/6 枚举，按 vendor 映射） | 无 purpose；可选 `expires_in_seconds` |
| 列表分页 | `after` / `has_more` / `last_id` | `page` / `next_page` |
| 删除响应 | `{id, deleted: bool}` | `{id, type: "file_deleted"}` → deleted=true |
| 元数据字段 | `bytes`、`purpose` | `size_bytes`、`mime_type`、`scope`（进 raw） |
| 路径拼接 | 直接追加 `/files` | 官方基地址不含 `/v1`，`filesUrl` 自动补 `v1` 段 |

Anthropic 客户端**不发送已废弃的 beta header**；Claude Files 的状态机字段（uploading/pending/processed）不进统一模型。

## 消息侧引用直通

- OpenAI 兼容转换器（`ChatCompletionsConverters`）早已支持 `gimi-file://` 直通为 file_id 参数。
- Anthropic 转换器（`ClaudeRequestConverter`）的 FileData 分支支持两种来源：HTTP URI（原行为不变）与 `gimi-file://` 厂商引用——后者解析 fileId 后图片 → `ImageBlockParam` + `FileImageSource.of(fileId)`，PDF → `DocumentBlockParam` + `FileDocumentSource.of(fileId)`；其余 MIME 抛 `UnsupportedOperationException`。遇到 `gimi-file://` **不得**要求 HTTP 引用校验（曾经直接抛异常，开关打开后发送必崩）。
- 文件上传开关的总闸是 `ServiceCatalogModels.kt` 的 `FILE_UPLOAD_TEMPLATE_IDS`（当前 `deepseek`/`anthropic`/`openai`）；实例详情页的 switch、能力推导（`withDerivedUploadCapabilities`）与发送门控全部由它驱动。
- 开关关闭（`PATH_TEXT`）时文档不随请求传输：`AttachmentResolvingModel` 把本地文档引用替换为含**绝对路径**的文本 part（文件名与 MIME 一并保留），agent 自行决定是否用环境文件导入工具（如 `import_file_to_environment`）读取；文件已不存在时才降级为占位文本。`FILE_REFERENCE` 模式下的历史本地文档草稿同样按路径文本送达。

## 注册表联动

- 注册表在 chip 上传成功时立即记录（无论草稿随后是否发送）；本地不存文件字节、不缓存 file_id 映射、不做内容去重。
- `deleteFile` 成功后网关**同步移除** `UploadedFileRegistry` 对应记录（防 UI 幽灵条目）；删除失败（含 NOT_FOUND 之外的厂商错误）保留记录，由用户重试。
- Room DAO（`uploaded_files`，PK fileId）提供按 vendor 查询与按 (vendor, fileId) 删除查询。

## 已知坑位（重写或调试时必读）

- multipart 顺序：purpose 字段必须**先于** file part，反序被厂商拒绝；Builder 必须显式 `setType(MultipartBody.FORM)`（默认 `multipart/mixed` 会被拒）。
- 文件名 ASCII 化：Content-Disposition 文件名含非 ASCII 字符会 400，必须经 `asciiFileName()` 压平。
- HTTP 413 → `TOO_LARGE`；超时 150s。
- K2 编译：不支持对带默认参数的构造器用 `::Class.Nested` 引用（改 lambda）；`@Serializable` 类带自定义 private companion 会在运行期反射时抛 `IllegalAccessError`；同包不同文件的同名 private DTO 报 Redeclaration（本模块按 `OpenAi*`/`Claude*` 前缀命名）。

## 新增厂商 / 新协议的强制步骤

1. **兼容 OpenAI 或 Claude 协议的厂商**：在 `VendorFileProtocols.byVendor` 加一行 vendor → 协议映射；在模板（`ModelServiceTemplates.kt`）声明对话连接默认 endpoint；如需展示开关，把 templateId 加入 `FILE_UPLOAD_TEMPLATE_IDS`。零客户端代码。
2. **全新协议**：实现一个 `FileProtocolClient`（五个能力全部实现，未支持的抛 `Failure(UNSUPPORTED_VENDOR)`）+ `ModelAccessModule` 加一处 `@IntoSet` 绑定 + 登记表加映射。现有代码零修改。
3. **新增协议客户端必须带单元测试**（MockWebServer 风格，参照 `OpenAiProtocolFileClientTest` / `ClaudeProtocolFileClientTest`），至少覆盖：multipart 字段顺序、非 ASCII 文件名、413/404 映射、游标映射、统一 DTO raw 兜底。
