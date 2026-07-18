# Cherry Studio 文件上传到 AI 回复的完整链路

> 文档目的:说清楚 Cherry Studio 中"用户上传图片 / 文档 → 助手回复消息"这条主线的代码全貌,覆盖 renderer 入口、main 进程持久化、消息组装、Provider 路由、流式回传、附件类型分类与文档文本抽取。所有 `file:line` 引用均针对当前仓库 `main` 分支。
>
> 修订记录:
> - v1(本次):首版。基于 `main` 分支(2026-07-11 提交 `f95a640ca`)的代码现状撰写。

---

## 目录

- [§ 0. 一句话总结](#0-一句话总结)
- [§ 1. 全链路时序图](#1-全链路时序图)
- [§ 2. 用户上传入口(renderer)](#2-用户上传入口renderer)
  - 2.1 三种入口 / 2.2 关键类型 / 2.3 `ComposerAttachment` 状态
- [§ 3. 文件持久化(main 进程)](#3-文件持久化main-进程)
  - 3.1 两套存储并存 / 3.2 传统 `FileStorage` / 3.3 v2 `FileManager` / 3.4 `createInternalEntry` 详解
- [§ 4. 消息组装:`buildFileParts` 桥接](#4-消息组装buildfileparts-桥接)
  - 4.1 4 步拆解 / 4.2 `withCherryMeta` 类型安全机制 / 4.3 `providerMetadata.cherry` 命名空间
- [§ 5. 附件 → Provider 路由](#5-附件--provider-路由)
  - 5.1 `attachmentRouting` 判定 / 5.2 `materializeNativeFilePart` base64 inlining / 5.3 `extractDocumentText` 文本抽取
- [§ 6. 流式响应回传](#6-流式响应回传)
  - 6.1 main → renderer IPC event / 6.2 renderer 端 `IpcChatTransport` / 6.3 AI SDK `useChat` 累积
- [§ 7. 数据持久化](#7-数据持久化)
  - 7.1 message 表 / 7.2 fileEntry 表 / 7.3 terminal 时写回
- [§ 8. 文件类型分类](#8-文件类型分类)
  - 8.1 `FILE_TYPE` 6 选 1 / 8.2 5 个扩展名白名单 / 8.3 分类函数 / 8.4 MIME 推断
- [§ 9. 文档文本抽取(legacy vs v2)](#9-文档文本抽取legacy-vs-v2)
- [§ 10. 关键文件清单](#10-关键文件清单)
- [§ 11. 已知的坑与 deferred 工作](#11-已知的坑与-deferred-工作)
- [§ 12. 自测清单](#12-自测清单)

---

## § 0. 一句话总结

用户在 composer 里选/拖/粘文件 → 文件以绝对路径形式留在 renderer state → 用户按"发送"时,`buildFilePartsForAttachments` 触发 main 端 `FileManager.createInternalEntry` 把字节**拷到 Cherry 私有目录并写一行 SQLite**,返回稳定 `FileEntryId`;构造 `FileUIPart = {type:'file', mediaType, url:'file://...', filename}` + `providerMetadata.cherry.{fileEntryId, fileTokenSourceId}` → 随 IPC 送到 main → `MessageService.createUserMessageWithPlaceholders` 在一个事务里写 user row + N 个 assistant placeholder → `attachmentRouting.prepareChatMessages` 决定 native(走 `materializeNativeFilePart` 把 entry 字节读出做 base64 data URL 内联)还是 extract(走 `extractDocumentText` 抽纯文本塞 text part) → `aiAgent.stream()` 调 AI SDK 各家 adapter → 流式 chunk 走 `ai.stream_chunk` IPC event 回 renderer → AI SDK `useChat` 自动累积成最终 assistant message parts。

---

## § 1. 全链路时序图

```
[用户:选/拖/粘文件]
   ↓
[renderer/composer] ComposerAttachment{path, fileTokenSourceId, ...}
   ↓ 按"发送"
[renderer/IpcChatTransport.sendMessages]  ← userMessageParts 序列化随 IPC
   ↓
[renderer/buildFilePartsForAttachments]                ← § 4
   │ 1. window.api.file.createInternalEntry({source:'path', path})
   │    → main: FileManager.createInternalEntry
   │    → 拷字节到 {userData}/Data/Files/{uuid}.{ext}
   │    → INSERT fileEntryTable (Drizzle ORM, SQLite)
   │    → 返回 FileEntry{id, origin:'internal', ...}
   │ 2. window.api.file.getPhysicalPath(id)
   │ 3. window.api.file.getMetadata(handle)
   │    → mime.getType(ext) 推断 MIME
   │ 4. basePart = {type:'file', mediaType, url:'file://'+path, filename}
   │ 5. withCherryMeta(basePart, {fileEntryId, fileTokenSourceId})
   ↓
[userMessageParts: FileUIPart[] 随 IPC 发出]
   ↓
[main/AiStreamManager.dispatch → createUserMessageWithPlaceholders]
   │ 一个事务:
   │ INSERT messageTable(role:'user', data:{parts: userMessageParts})
   │ INSERT N 条 assistant placeholder(每个 model 一条)
   ↓
[main/buildAgentParams → attachmentRouting.prepareChatMessages]   ← § 5
   │ 对每个 FileUIPart:
   │   ├── native (model 支持)?
   │   │   └── materializeNativeFilePart:
   │   │       ├── 读 readCherryMeta(part).fileEntryId
   │   │       ├── FileManager.read(id, {encoding:'base64'})
   │   │       └── 改写 url → "data:<mime>;base64,..."
   │   └── 非 native?
   │       ├── image → ocrImage() 或 note
   │       ├── text/code → extractDocumentText(entryId)
   │       │      ├── pdf → extractPdfText (pdf-parse)
   │       │      ├── doc → WordExtractor
   │       │      ├── docx/pptx/xlsx → officeParser
   │       │      └── 其他 → decodeTextWithAutoEncoding
   │       ├── audio/video → note
   │       └── 文档无文本 → noExtractableTextNote(filename)
   ↓
[main/Agent.stream → aiAgent.stream({messages: modelMessages})]
   │ AI SDK 把 ModelMessage[] 转各家 wire format
   │ (OpenAI: {type:'image_url', image:{url:'data:...'}}
   │  Anthropic: {type:'image', source:{type:'base64', media_type, data}}
   │  Google: {inline_data:{mime_type, data}})
   ↓
[main/WebContentsListener.sendChunk → 'ai.stream_chunk' event]
   ↓
[renderer/IpcChatTransport → useChat → parts 累积 → React render]
```

---

## § 2. 用户上传入口(renderer)

所有输入都收敛到 `composer` 模块,产出统一的 `ComposerAttachment[]` 放在 composer state 里。**文件以绝对路径形式存在 renderer state 中,真正的字节落盘要等到"发送"那一刻**。

### 2.1 三种入口

| 入口 | 关键文件 | 流程 |
|---|---|---|
| 点击上传按钮 | `src/renderer/components/composer/tools/components/AttachmentButton.tsx:33` | 调原生 dialog `window.api.file.select({...multiSelections})` 拿 `FileMetadata[]` |
| 拖拽 | `src/renderer/components/composer/paste/useFileDragDrop.ts:108` | `getFilesFromDropEvent` → `filterSupportedFiles` → `toComposerAttachments` |
| 粘贴(文本) | `src/renderer/components/composer/paste/usePasteHandler.ts:43` → `pasteHandling.ts:31` | 长文本(>`LONG_TEXT_PASTE_THRESHOLD`)写到 `createTempFile('pasted_text.txt')` 再读回,产出 `PastedTextFileMetadata`(line 49-62) |
| 粘贴(图片) | `pasteHandling.ts:76-95` | 剪贴板图片无 path 时 → `createTempFile` + `file.arrayBuffer()` + `window.api.file.write` 拿 `FileMetadata` |

### 2.2 关键类型

- `FileMetadata` — `src/shared/types/file/common.ts:28`(id/path/name/origin_name/ext/**type**/size/count/created_at;type 是 `FILE_TYPE` 6 选 1)
- `FILE_TYPE` — `src/shared/types/file/common.ts:5`(IMAGE/VIDEO/AUDIO/TEXT/DOCUMENT/OTHER),与 `src/renderer/types/file.ts:5` 同值,renderer 副本用于显示
- `ComposerAttachment` — `src/renderer/utils/message/composerAttachment.ts:17`(lean 版本,带稳定 `fileTokenSourceId`,映射 composer token id)
- `PastedTextFileMetadata` — `src/shared/types/file/common.ts:84`(`FileMetadata & {composerFileKind: 'pasted-text'}`)

### 2.3 `ComposerAttachment` state

```
上传后 state:
{
  path: 'C:\\Users\\foo\\photo.png',     // 绝对路径,浏览器侧不读字节
  name: 'photo.png',
  origin_name: 'photo.png',
  ext: '.png',
  type: FILE_TYPE.IMAGE,
  size: 1234567,
  fileTokenSourceId: 'tok_xxx',           // 稳定 id,跨消息追踪
  id: '<uuid>'                            // 临时,发送时换 FileEntryId
}
```

---

## § 3. 文件持久化(main 进程)

### 3.1 两套存储并存

| 维度 | Legacy `FileStorage` | v2 `FileManager` |
|---|---|---|
| 文件 | `src/main/services/FileStorage.ts` | `src/main/services/file/FileManager.ts` |
| 注册方式 | 模块级 singleton(`export const fileStorage = new FileStorage()`) | lifecycle service(`@Injectable('FileManager')`, `application.get('FileManager')`) |
| 实例化时机 | 模块加载时,**早于** `application.bootstrap()` | bootstrap 后,Phase.WhenReady |
| 文件路径 | `{userData}/Data/Files/{uuid}{ext}` | 同样 `{userData}/Data/Files/{uuid}.{ext}` |
| 数据表 | 纯 FS,**没 DB row** | `fileEntryTable`(`src/main/data/db/schemas/file.ts`) |
| Origin 概念 | 无 | `internal`(Cherry 拥有) / `external`(引用用户路径) |
| 去重 | md5 hash 扫整个 storageDir(`findDuplicateFile`) | 无 —— 每个 create 都是新 UUID |
| 图片处理 | `compressImage`(实际只是 copyFile,**没真正压缩**) | 不做处理,只是 atomic copy |
| 文档读取 | `readFileCore`(不处理 PDF) | 通过 `attachmentTextExtraction.ts`(处理 PDF) |
| 主要消费方 | v1 旧路径 + IPC `file:selectFile` / `file:uploadFile` / `file:readFile` | v2 新路径 `file.batch_*` IpcApi |

### 3.2 传统 `FileStorage` 关键方法

| 方法 | 行号 | 干什么 |
|---|---|---|
| `selectFile` | `FileStorage.ts:130` | 原生 dialog 拿 path,返回 `FileMetadata[]` |
| `findDuplicateFile` | `FileStorage.ts:84` | md5 hash 扫 storageDir 找重复 |
| `compressImage` | `FileStorage.ts:167` | **注释承诺压缩但实现里只是 copyFile**(line 175, 183) |
| `uploadFile` | `FileStorage.ts:192` | 存到 `feature.files.data`,命名 `uuid<ext>`,图片走 `compressImage`,文档 `copyFile` |
| `readFile` | `FileStorage.ts:460` | 委托 `readFileCore` |
| `readFileCore` | `FileStorage.ts:395` | `.doc` → WordExtractor;其他 office → officeparser;文本 → `readTextFileWithAutoEncoding(chardet)` |
| `_isTextFile` | `FileStorage.ts:1018` | `isbinaryfile` 库 sniff 字节(v1 兜底,v2 没) |
| `createTempFile` | `FileStorage.ts:501` | 渲染端写剪贴板图片/长文本用 |
| `writeFile` | `FileStorage.ts:506` | 直接 fs write |
| `base64Image` / `binaryImage` / `base64File` / `pdfPageCount` | `FileStorage.ts:540+` | 给 renderer 读字节;`pdfPageCount` 用 `pdf-lib` |

### 3.3 v2 `FileManager` 关键设计

`FileManager.ts:1-1100` 是一个 facade,**每个公开方法都委托到 `./internal/*` 下的纯函数模块**,类本身只负责 lifecycle 和 per-instance `versionCache`(`FileManager.ts:645`)。

**Origin 双轨制**:
- `internal` = Cherry 把字节拷到自家目录,**完全掌控**(trash/restore/emptyTrash 都生效)
- `external` = 只在 DB 存 `externalPath`,**不动用户文件**(`fe_external_no_delete` CHECK 约束,line 96-106)

**DanglingCache**(`FileManager.ts:152`)给 external entry 维护 `'present' | 'missing' | 'unknown'` LRU。关键不对称语义(line 109-124):
- 失败 stat(ENOENT)→ commit `'missing'`(`observeExternalAccess` chokepoint)
- 成功 create / ensure / rename → 显式推 `'present'`(`addEntry` + `onFsEvent(..., 'present', 'ops')`)
- **被动 read/hash/stat 不动 cache** —— 故意避免 "ops 每次 stat 都 flip present" 抵消 watcher 设计

**VersionCache + OCC**(`FileManager.ts:967 writeIfUnchanged`):用 `(mtime, size)` 做乐观并发;FAT32/SMB/NFS 上 mtime 是秒精度,可能 silent mis-identify(line 254-279 大段注释),提供 `expectedContentHash` 兜底(xxhash-h64)。

### 3.4 `createInternalEntry` 详解(核心)

```ts
// src/main/ipc/handlers/file.ts:45-46
'file.batch_create_internal_entries': async ({ items }) =>
  application.get('FileManager').batchCreateInternalEntries(items as CreateInternalEntryIpcParams[])
```

`FileManager.ts:899` 委托给 `internalCreateInternal(this.deps, params)`,对应 `src/main/services/file/internal/entry/create.ts`。

**4 种 source**(`CreateInternalEntryIpcSchema`,`FileManager.ts:231-241`):
- `path` — `{path: AbsolutePath}`(本次 `buildFileParts` 用这条)
- `url` — `{url: z.url()}`(下载远端文件)
- `base64` — `{data: string, name?}`(base64 字节)
- `bytes` — `{data: Uint8Array, name, ext?}`(内存字节)

**步骤**:
1. 生成新 UUID(`origin: 'internal'`)
2. 把字节写到 `{userData}/Data/Files/{uuid}.{ext}`(atomic copy)
3. `INSERT fileEntryTable` 一行(SQLite via Drizzle)
4. 返回 `FileEntry{id, origin, name, ext, size, mtime, ...}`

---

## § 4. 消息组装:`buildFileParts` 桥接

### 4.1 4 步拆解 — `src/renderer/utils/file/buildFileParts.ts:27-45`

```ts
export async function buildFilePartsForAttachments(attachments: ComposerAttachment[]): Promise<FileUIPart[]> {
  return Promise.all(
    attachments.map(async (attachment) => {
      const entry = await window.api.file.createInternalEntry({ source: 'path', path: attachment.path as FilePath })
      const physicalPath = await window.api.file.getPhysicalPath({ id: entry.id })
      const metadata = await window.api.file.getMetadata(createFilePathHandle(physicalPath))
      const basePart: FileUIPart = {
        type: 'file',
        mediaType: metadata.kind === 'file' ? metadata.mime : 'application/octet-stream',
        url: `file://${physicalPath}`,
        filename: attachment.origin_name || attachment.name
      }
      return withCherryMeta(basePart, {
        fileEntryId: entry.id,
        fileTokenSourceId: attachment.fileTokenSourceId
      })
    })
  )
}
```

| 步骤 | IPC 调用 | 关键点 |
|---|---|---|
| 1 | `window.api.file.createInternalEntry({source:'path', path})` | 触发 main `FileManager.createInternalEntry`;字节拷到自家目录;**写一行 `fileEntry` 到 SQLite**;返回 `entry.id` |
| 2 | `window.api.file.getPhysicalPath({id})` | 从 entry 解析物理路径(`resolvePhysicalPath`) |
| 3 | `window.api.file.getMetadata(createFilePathHandle(physicalPath))` | 走 `dispatchHandle` → `FileManager.getMetadata(entryId)`(`FileManager.ts:851`),`fs.stat` + `mime.getType(ext)` 推断 MIME |
| 4 | `withCherryMeta(basePart, {fileEntryId, fileTokenSourceId})` | 把稳定 ID 藏到 `providerMetadata.cherry` 命名空间 |

**关键设计点**:
1. **每个附件独立 create entry** —— `Promise.all` + `map`,互不阻塞。重复上传**不会复用 entry**(注释 line 7 "no conflict resolution")
2. **三处冗余身份标识,各有分工**:
   - `entry.id`(`FileEntryId`)→ 跨消息/重启的稳定身份,DB 主键
   - `part.url = file://${physicalPath}`→ 临时身份,UI 渲染或本地工具能用,**路径会因为 userData 迁移而失效**
   - `part.filename`→ 用户可见名,`origin_name` 优先(原文件名),否则用 `name`(可能已重命名)
3. **`createFilePathHandle(physicalPath)` 是临时 workaround** —— `getMetadata` 暂时还没 entry-handle 版本(注释 `FileManager.ts:676` "Phase 2 not yet wired"),所以走 path-handle 分支落 `getMetadataByPath`

### 4.2 `withCherryMeta` 类型安全机制 — `src/shared/data/types/uiParts.ts:310-323`

```ts
export function withCherryMeta<P extends CherryMessagePart>(
  part: P,
  patch: Partial<CherryMetaForPartType<P['type']>>
): P {
  const existingMeta = (part as { providerMetadata?: Record<string, unknown> }).providerMetadata
  const existingCherry = (existingMeta?.cherry ?? {}) as Record<string, unknown>
  return {
    ...part,
    providerMetadata: {
      ...existingMeta,
      cherry: { ...existingCherry, ...(patch as Record<string, unknown>) }
    }
  } as P
}
```

**设计意图**(头文件注释 line 305-309):

> *"Patch cherry meta with compile-time part-scoping. Writing a field that doesn't belong to the part's meta shape fails to compile — e.g. `withCherryMeta(textPart, { thinkingMs: 1 })` is a type error."*

`CherryMetaForPartType<T>` 是一个按 part 类型**派生的联合类型**:
```ts
type CherryMetaForPartType<T> = T extends 'file'   ? CherryFileMeta      // { fileEntryId, fileTokenSourceId, ... }
                                  | T extends 'text'    ? CherryTextMeta
                                  | T extends 'thinking'? CherryThinkingMeta  // { thinkingMs, ... }
                                  | ...
```

- 调用方只看到自己 part 类型**应该**有的字段
- AI SDK 序列化 `UIMessage.parts` 时,`providerMetadata.cherry.*` 原样保留(AI SDK 5 标准字段),所以**这些 cherry 私有字段可随 message 持久化到 `messageTable.data.parts`**
- 对应 reader `readCherryMeta`(line 295)做反向操作,带 Zod safeParse,失败返回 `undefined`

### 4.3 `providerMetadata.cherry` 命名空间

AI SDK 5 标准 `UIMessagePart` 已有 `providerMetadata?: Record<string, unknown>`,Cherry 用 `providerMetadata.cherry` 作为私有命名空间,不会污染 provider wire format。Provider 通常忽略此字段。

**消费方**:
- `src/main/ai/messages/fileProcessor.ts:75 materializeNativeFilePart` —— 调 `readCherryMeta(part)?.fileEntryId` 拿 entry id
- `src/main/ai/messages/attachmentRouting.ts` —— 准备 chat messages 时读 `fileEntryId` 做 inlining 决策

### 4.4 文件 → part 整体抽象(为什么不再有 messageBlockService)

v2 已经统一到 AI SDK 的 `UIMessage.parts`,v1 的 `blocks` 体系被删除。**没有独立的"messageBlockService" / "blockMessageService"** —— parts 就是 AI SDK `UIMessagePart`,token 累积单位。

`FileUIPart` 类型定义在 `src/shared/data/types/message.ts`(`CherryMessagePart` 联合的一个变体),`ImageUIPart` / `TextUIPart` / `ToolUIPart` 等并列。Renderer 端 `useChat`(`@ai-sdk/react`)把流式 chunk 自动累积成 parts,**业务层不需要手动拼装**。

---

## § 5. 附件 → Provider 路由

### 5.1 `attachmentRouting` 判定 — `src/main/ai/messages/attachmentRouting.ts`

`prepareChatMessage(part)`(line 128)和 `prepareChatMessages(parts)`(line 194)是核心路由函数。

**路由决策**:
1. **第一方 file**(`fileEntryId` backed)→ 判断 model `nativeFileSupport`(`resolveNativeFileSupport(provider, model, aiSdkProviderId)`)
   - **native** → `materializeNativeFilePart` 读字节 inline base64
   - **非 native** → 文本/文档走 `extractDocumentText`;image 走 OCR 或 note;audio/video → note
2. **Legacy(gateway, 无 fileEntryId)** → eager materialization,失败降级为 note

`nativeFileSupport` 来源于 `buildAgentParams.ts:86`:
```ts
const nativeFileSupport = resolveNativeFileSupport(provider, model, aiSdkProviderId)
```

### 5.2 `materializeNativeFilePart` base64 inlining — `src/main/ai/messages/fileProcessor.ts:75-97`

```ts
export async function materializeNativeFilePart(part: FileUIPart): Promise<FileUIPart | null> {
  const fileEntryId = readCherryMeta(part)?.fileEntryId
  if (fileEntryId) {
    const inlined = await fileEntryIdToDataUrl(fileEntryId)
    if (inlined) return { ...part, ...inlined }
    // fileEntry missing / unreadable — try to rescue from a still-valid
    // `file://` snapshot (legacy / migrated rows). If no usable file:// URL
    // is available, drop the part rather than emit `{type:'file', data:''}`.
    const url = part.url
    if (!url || !url.startsWith('file://')) return null
    const rescued = await fileUrlToDataUrl(url)
    return rescued ? { ...part, ...rescued } : null
  }

  const url = part.url
  if (!url) return part
  if (!url.startsWith('file://')) return part

  const inlined = await fileUrlToDataUrl(url)
  if (!inlined) return null
  return { ...part, ...inlined }
}
```

**Fallback 链**:
1. **首选**:`fileEntryId` → `FileManager.read(entryId, {encoding:'base64'})` → `data:${mime};base64,${content}`
2. **fallback A**:entry 读失败但 `part.url` 还在 → `fileUrlToDataUrl(url)` 从磁盘重新读字节
3. **fallback B**:URL 不是 `file://` → 保留原 part 不动(例如已经在线 URL)
4. **fallback C**:都没法读 → 返回 `null`,caller 降级为 note

**未来扩展**(注释 line 64-74):接 provider File API(Gemini File / OpenAI Files)后,小文件继续 inline base64,大文件上传拿 reference。函数签名不变,caller 无感。

### 5.3 `extractDocumentText` 文本抽取 — `src/main/ai/messages/attachmentTextExtraction.ts`

详见 § 9。

### 5.4 `Agent.stream` 真正调模型 — `src/main/ai/runtime/aiSdk/Agent.ts:131`

```ts
async stream(...) {
  ...
  const modelMessages = await toModelMessages(initialMessages, params.mediaCapabilities)  // line 175
  ...
  return this.aiAgent.stream({ messages: modelMessages, abortSignal: signal })  // line 178
}
```

`toModelMessages` 在 `src/main/ai/messages/messageRules.ts:73`:
1. `stripUnsupportedMedia(messages, caps)` —— 按 model 媒体能力剔除不支持的 part
2. `convertToModelMessages(shaped, {ignoreIncompleteToolCalls:true})` —— AI SDK 原生
3. 合并连续同 role、补空

然后 AI SDK 各 provider adapter 把 `ModelMessage[]` 转各家 wire format:
- **OpenAI**: `{type:'image_url', image:{url:'data:image/png;base64,...'}}`
- **Anthropic**: `{type:'image', source:{type:'base64', media_type:'image/png', data:'...'}}`
- **Google**: `{inline_data:{mime_type:'image/png', data:'...'}}`

---

## § 6. 流式响应回传

### 6.1 main → renderer IPC event — `src/main/ai/streamManager/listeners/WebContentsListener.ts`

```ts
// line 165-167
sendChunk(chunk) {
  this.wc.send(IpcChannel.IpcApi_Event, 'ai.stream_chunk', { topicId, executionId, anchorMessageId, chunk })
}
```

- 单例 per `(wc, topic)`,id = `wc:${wc.id}:${topicId}`(line 52)
- 16ms 滚窗合流,直到 `MAX_COALESCE_CHARS=2048` 才 flush(line 9-12 + `onChunk` line 58),避免每 token 触发 IPC
- `onDone` → `ai.stream_done`;`onError` → `ai.stream_error`

### 6.2 renderer 端 `IpcChatTransport` — `src/renderer/services/aiTransport/IpcChatTransport.ts:167-189`

`buildListenerStream`:
- 订阅 ACK(`streamDispatchService`)+ 三个 IPC event(`ai.stream_chunk` / `_done` / `_error`)
- `controller.enqueue(data.chunk)` 把每个 chunk 推入流
- 16ms `requestAnimationFrame` 做 batch flush,避免每 token 触发 React 渲染(line 107-122)

### 6.3 AI SDK `useChat` 累积

```ts
// src/renderer/hooks/useChatWithHistory.ts:37
new Chat({ transport: ipcChatTransport })
```

`@ai-sdk/react` 的 `useChat` 自动把 chunks 累积成 `CherryUIMessage`(parts: text / reasoning / tool / etc.)。**业务层不需要单独的 messageBlockService** —— parts 本身就是 token 累积单位。

**中断**: `useChatWithHistory.stop` → `ipcApi.request('ai.stream_abort', {topicId})`(line 63-67)

### 6.4 IPC 通道总览

| 类型 | channel | 方向 |
|---|---|---|
| request/response | `ai.stream_open` | renderer → main |
| request/response | `ai.stream_attach` | renderer → main |
| request/response | `ai.stream_abort` | renderer → main |
| event | `ai.stream_chunk` | main → renderer(单 `IpcApi_Event` 通道) |
| event | `ai.stream_done` | main → renderer |
| event | `ai.stream_error` | main → renderer |

Schema 定义在 `src/shared/ipc/schemas/ai.ts:115-148`(request)和 `:186 AiEventSchemas`(event)。

---

## § 7. 数据持久化

### 7.1 message 表 — `src/main/data/db/schemas/message.ts:17`

```ts
messageTable: {
  id: UUIDv7
  topicId: FK
  parentId: tree
  role: 'user' | 'assistant' | 'system'
  data: JSON  // 装 MessageData = {parts: CherryMessagePart[]}
  searchableText: FTS5 同步
  status: 'pending' | 'streaming' | 'completed' | 'failed'
  siblingsGroupId: ...
  modelId: FK user_model
  modelSnapshot: JSON
  stats: JSON  // 装 MessageStats
  ftsRowid: trigger 填
}
```

**附件不直接存在 `data` 里** —— `FileUIPart` 只在 `data.parts` 里存 `providerMetadata.cherry.fileEntryId`(稳定 id)+ `url: 'file://<physical>'`(临时)+ `filename/mediaType`。

### 7.2 fileEntry 表 — `src/main/data/db/schemas/file.ts`

`fileEntryTable` —— 实际磁盘条目:
- `id` UUID
- `origin` `'internal' | 'external'`
- `name`, `ext`
- `externalPath?`(external 才填)
- `size`, `mtime`, `createdAt`
- `deletedAt`(trash)
- UNIQUE(`externalPath`)

加上 `chatMessageFileRefTable`(`fileRelations.ts`)记录哪个 message 引用哪个 fileEntry。

### 7.3 terminal 时写回

`PersistenceListener.ts:75 onDone` → `backend.persistAssistant({finalMessage, status, stats})`

Backend `MessageServiceBackend.ts:26` → `messageService.update(assistantMessageId, {data:{parts}, status, stats})` —— 原地把 `pending` placeholder 改成带 parts 的最终 row。

---

## § 8. 文件类型分类

### 8.1 `FILE_TYPE` 6 选 1

**两个地方定义完全相同**:

**`src/shared/types/file/common.ts:5-12`**(权威源,带 Zod schema):
```ts
export const FILE_TYPE = {
  IMAGE: 'image', VIDEO: 'video', AUDIO: 'audio',
  TEXT: 'text', DOCUMENT: 'document', OTHER: 'other'
} as const
```

**`src/renderer/types/file.ts:5`**(副本,renderer 内部用)。

**没有 `application/pdf`、`text/markdown` 这种 MIME 字符串枚举** —— 整个 codebase 只在 6 选 1 这个粗粒度上做业务路由,**真正的 MIME 字符串只在送 provider 的那一刻才推断**。

### 8.2 5 个扩展名白名单 — `src/shared/utils/file/fileExtensions.ts`

```ts
export const imageExts    = ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp']
export const videoExts    = ['.mp4', '.avi', '.mov', '.wmv', '.flv', '.mkv']
export const audioExts    = ['.mp3', '.wav', '.ogg', '.flac', '.aac']
export const documentExts = ['.pdf', '.doc', '.docx', '.pptx', '.xlsx', '.xls', '.odt', '.odp', '.ods']
export const textExts     = [...new Set([...customTextExts.values()).flat(), ...codeLangExts])  // 1000+
```

`textExts` 由两源合并:
- `codeLangExts`(`fileExtensions.ts:38`)—— 来自 `@shared/utils/codeLanguages`(linguist 数据库)
- `customTextExts`(`fileExtensions.ts:44`)—— 13 类手工补充(`language` / `template` / `config` / `document` / `data` / `build` / `database` / `web` / `version` / `subtitle` / `log` / `eda`)

### 8.3 分类函数

#### 8.3.1 `getFileTypeByExt(ext)` —— 纯查表,SoT

```ts
// src/shared/utils/file(由 legacyFile.ts 重导出,line 33-43)
const fileTypeMap = new Map<string, FileType>()
imageExts.forEach((ext) => fileTypeMap.set(ext, FILE_TYPE.IMAGE))
videoExts.forEach((ext) => fileTypeMap.set(ext, FILE_TYPE.VIDEO))
audioExts.forEach((ext) => fileTypeMap.set(ext, FILE_TYPE.AUDIO))
textExts.forEach((ext)  => fileTypeMap.set(ext, FILE_TYPE.TEXT))
documentExts.forEach((ext) => fileTypeMap.set(ext, FILE_TYPE.DOCUMENT))
// 命中失败 → FILE_TYPE.OTHER
```

#### 8.3.2 `getFileType(ext)` — v1 legacy 兜底 — `src/main/utils/legacyFile.ts:121-128`

```ts
export function getFileType(ext: string): FileType {
  ext = ext.toLowerCase()
  return fileTypeMap.get(ext) || FILE_TYPE.OTHER
}
```

**v1 多走一步**(`FileStorage.ts:123-128`):
```ts
return fileType === FILE_TYPE.OTHER && (await this._isTextFile(filePath)) ? FILE_TYPE.TEXT : fileType
```

`.dat`/`.xyz`/无扩展名这种 OTHER 文件,会用 `isbinaryfile` 库**真的打开读前几 KB** 判断是不是文本(`FileStorage.ts:1018-1020`)。

#### 8.3.3 `isTextFile(target)` — v2 主进程 — `src/main/utils/file/metadata.ts:22-24`

```ts
export async function isTextFile(target: FilePath): Promise<boolean> {
  return (await getFileType(target)) === FILE_TYPE.TEXT
}
```

注释承认 v2 **没有 binary sniff 兜底**(`metadata.ts:4-7`):*"Fallback: buffer detection (isBinaryFile + chardet) for unknown extensions — deferred (no consumer yet); current detection is extension-only."*

#### 8.3.4 `mimeToExt(mimeType)` — 反向 — `src/main/utils/file/metadata.ts:27-30`

```ts
export function mimeToExt(mimeType: string): string | undefined {
  const ext = mime.getExtension(mimeType)
  return ext ?? undefined
}
```

### 8.4 MIME 推断

**只在两处**用 `mime` 包:

#### `FileManager.getMetadata:864`(v2 主流)
```ts
const inferredMime = ext ? (mime.getType(ext) ?? 'application/octet-stream') : 'application/octet-stream'
```

也就是说 **`buildFileParts` 那条链路上,FileUIPart.mediaType 直接来自 `mime.getType(ext)`**。

### 8.5 对照表(扩展名 → MIME / 6 分类 / 走向)

| ext | 6 分类 | `mime.getType` | 走向 |
|---|---|---|---|
| `.png` | IMAGE | `image/png` | native → base64 → Anthropic `image` |
| `.jpg`/`.jpeg` | IMAGE | `image/jpeg` | native → base64 → OpenAI `image_url` |
| `.gif` | IMAGE | `image/gif` | Anthropic 支持,Gemini 支持 |
| `.webp` | IMAGE | `image/webp` | 部分 provider 不接受 |
| `.bmp` | IMAGE | `image/bmp` | 老格式,基本 provider 都不支持 |
| `.mp4` | VIDEO | `video/mp4` | Gemini native;OpenAI 用 video_url;Anthropic 不直接 |
| `.mp3` | AUDIO | `audio/mpeg` | Gemini native;OpenAI 用 input_audio |
| `.wav` | AUDIO | `audio/wav` | OpenAI 支持 input_audio |
| `.pdf` | DOCUMENT | `application/pdf` | Anthropic native;其他 → `extractDocumentText` |
| `.docx` | DOCUMENT | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | → `extractDocumentText`(officeparser) |
| `.xlsx` | DOCUMENT | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | → `extractDocumentText` |
| `.md` | TEXT | `text/markdown` | 当文本塞 text part |
| `.txt` | TEXT | `text/plain` | 当文本 |
| `.ts` | TEXT | ⚠️ `application/typescript` 或 `video/mp2t`(歧义) | 当文本 |
| `.xyz` | OTHER | `application/octet-stream` | v2 直接 binary;v1 sniff 降级 TEXT |

### 8.6 `PhysicalFileMetadata` 类型 — `src/shared/types/file/common.ts:111-140`

discriminatedUnion,**kind × type 双轴**:
```ts
{
  kind: 'file', type: 'image',    mime, width?, height?    // 只有 image 有 w/h
  kind: 'file', type: 'document', mime, pageCount?        // 只有 document 有 pageCount
  kind: 'file', type: 'text',     mime, encoding?         // 只有 text 有 encoding
  kind: 'file', type: 'audio',    mime
  kind: 'file', type: 'video',    mime
  kind: 'file', type: 'other',    mime
}
```

`FileManager.getMetadata:851-873` 当前实现里,**所有非 image/document 的 kind 都标 `type: 'other'`**(注释 line 846-850):*"Per-kind enrichment — image width/height, PDF pageCount, text encoding — is deferred; renderer call sites that need those fields are expected to tolerate their absence until enrichment lands."*

---

## § 9. 文档文本抽取(legacy vs v2)

### 9.1 总览

| 维度 | Legacy `FileStorage.readFileCore` | v2 `attachmentTextExtraction.extract` |
|---|---|---|
| 文件 | `src/main/services/FileStorage.ts:395` | `src/main/ai/messages/attachmentTextExtraction.ts:35` |
| 入口 | `readFile(event, id)`(line 460) | `extractDocumentText(entryId, {signal?})` |
| 输入 | `filePath` 或 `{id}`(拼成 `storageDir/{id}`) | `FileEntryId` |
| 取字节方式 | `fs.readFileSync(path, 'utf-8')` / `officeparser.parseOfficeAsync(filePath, ...)` | `FileManager.read(entryId, {encoding:'binary'})` |
| **PDF** | ❌ **不支持**(只走 officeparser) | ✅ `extractPdfText(content)`(`@main/utils/pdf`,基于 `pdf-parse`) |
| `.doc` | ✅ `WordExtractor().extract(filePath)` | ✅ `new WordExtractor().extract(buffer)` |
| Office (.docx/.pptx/.xlsx/.od*) | ✅ `officeParser.parseOfficeAsync(filePath, {tempFilesLocation})` | ✅ 同上,但传 `Buffer` |
| 文本 / 代码 | ✅ `readTextFileWithAutoEncoding(filePath)`(chardet) | ✅ `decodeTextWithAutoEncoding(buffer)`(chardet) |
| 编码 | chardet 采样 1MB(`legacyFile.ts:210`) | chardet 采样 1MB(`legacyFile.ts:186`) |
| 缓存 | ❌ 无 | ✅ `cacheService.set(key, text, 30 * 60 * 1000)` |
| 取消 | ❌ 不支持 | ✅ `opts.signal?.aborted`,rethrow `signal.reason` |
| 文档无文本(扫描件) | 直接 throw | 返回空字符串,caller 用 `noExtractableTextNote(filename)` |

### 9.2 v2 路径核心代码 — `attachmentTextExtraction.ts:35-74`

```ts
async function extract(entryId: FileEntryId, ext: string): Promise<string> {
  const { content } = await application.get('FileManager').read(entryId, { encoding: 'binary' })

  if (ext === 'pdf') return (await extractPdfText(content)).trim()

  const buffer = Buffer.from(content)
  if (ext === 'doc') {
    const extracted = await new WordExtractor().extract(buffer)
    return extracted.getBody().trim()
  }
  if (OFFICE_PARSER_EXTS.has(ext)) {
    const text = await officeParser.parseOfficeAsync(buffer, { tempFilesLocation: application.getPath('app.temp') })
    return text.trim()
  }
  return decodeTextWithAutoEncoding(buffer).trim()
}
```

**Office 扩展名白名单** (line 24-26):
```ts
const OFFICE_PARSER_EXTS = new Set(
  documentExts.map((ext) => ext.replace(/^\./, '')).filter((ext) => ext !== 'pdf' && ext !== 'doc')
)
```

从 `documentExts` **减去** pdf 和 doc,剩下的全交给 `officeparser`。

**缓存键** (line 62):
```ts
const cacheKey = `doc-extraction:${entryId}:${version.mtime}:${version.size}`
```
基于 `(entryId, mtime, size)` —— 文件改了自动失效。

### 9.3 `decodeTextWithAutoEncoding` 两轮尝试 — `legacyFile.ts:185-201`

```ts
export function decodeTextWithAutoEncoding(data: Buffer): string {
  const sample = data.length > MB ? data.subarray(0, MB) : data
  const detected = chardet.detect(sample) || 'UTF-8'
  for (const encoding of [detected, 'UTF-8']) {
    try {
      const content = iconv.decode(data, encoding)
      if (!content.includes('�')) return content  // U+FFFD 替代符 → 解码失败
      logger.warn(`...`)
    } catch (error) { logger.error(...) }
  }
  return iconv.decode(data, 'UTF-8')  // 兜底
}
```

策略:**chardet 检测 + UTF-8 fallback**,任何一轮出现 `U+FFFD` 就认为该编码不对,继续试下一个。能避开"GBK 文件被检测成 UTF-8"。

### 9.4 `pdf-parse` vs `pdf-lib`

仓库里**同时存在**,分工不同:
- `pdf-lib`(`PDFDocument.load`)—— 只用来**算 PDF 页数**(`FileStorage.pdfPageCount`),不抽文本,给 v1 UI 显示
- `pdf-parse`(`@main/utils/pdf.ts:9`)—— 真正抽文本,异步,生成 `PdfParser`,`getText()` 后必须 `destroy()` 释放

### 9.5 v1 路径为什么不抽 PDF

`FileStorage.readFileCore:395` 看到 `.pdf` 会进 `documentExts` 分支,再走到 `officeparser.parseOfficeAsync(filePath)`,officeparser 对 PDF 支持弱,**要么返回空字符串要么抛错**。v1 路径里 PDF 文档**实际上传过去时不含文本内容**(只把 `file://` URL 给 provider,provider 自己读)。v2 `extractDocumentText` 是把这条路补完的唯一地方。

---

## § 10. 关键文件清单

### 上传入口(renderer)
- `src/renderer/components/composer/tools/components/AttachmentButton.tsx` — 点击上传
- `src/renderer/components/composer/paste/useFileDragDrop.ts` — 拖拽
- `src/renderer/components/composer/paste/usePasteHandler.ts` + `pasteHandling.ts` — 粘贴

### 文件持久化(main)
- `src/main/services/FileStorage.ts` — legacy 存储(传统 `uploadFile` / `readFile` / `base64Image` / `compressImage` / `readFileCore`)
- `src/main/services/file/FileManager.ts` — v2 lifecycle 服务(`createInternalEntry` / `read` / `getMetadata` / `versionCache` / `DanglingCache`)
- `src/main/ai/messages/attachmentTextExtraction.ts` — v2 文档抽取(`extractDocumentText` 带 30 分钟缓存)

### 附件桥接(核心)
- `src/renderer/utils/file/buildFileParts.ts:27` — `buildFilePartsForAttachments` 4 步桥接
- `src/shared/data/types/uiParts.ts:295 / 310` — `readCherryMeta` / `withCherryMeta`
- `src/main/ai/messages/fileProcessor.ts:75` — `materializeNativeFilePart`(entry → base64 data URL)

### Provider 调用
- `src/main/ai/messages/attachmentRouting.ts:128` — `prepareChatMessage` / `:194` `prepareChatMessages`(路由核心)
- `src/main/ai/runtime/aiSdk/Agent.ts:131` — `Agent.stream()`
- `src/main/ai/messages/messageRules.ts:73` — `toModelMessages`(`stripUnsupportedMedia` + `convertToModelMessages`)
- `src/main/ai/runtime/aiSdk/params/buildAgentParams.ts:71` — `collectFileAttachments` + `resolveNativeFileSupport`
- `src/main/ai/provider/factory.ts:20` — `getAiSdkProviderId`

### 流式回传
- `src/main/ai/streamManager/listeners/WebContentsListener.ts` — main 端 `ai.stream_chunk` 定向 send
- `src/main/ai/streamManager/listeners/PersistenceListener.ts:75` — terminal `persistAssistant`
- `src/main/ai/streamManager/persistence/backends/MessageServiceBackend.ts:26` — `messageService.update`
- `src/renderer/services/aiTransport/IpcChatTransport.ts:167` — renderer 订阅 IPC event 拼 `ReadableStream`
- `src/renderer/hooks/useChatWithHistory.ts` — 接 AI SDK `useChat`

### IPC 与数据
- `src/main/ipc/handlers/file.ts` — `file.batch_*` IpcApi 路由
- `src/main/ipc/handlers/ai.ts:52` — `ai.stream_open` 入口
- `src/main/data/services/MessageService.ts:211` — `createUserMessageWithPlaceholders`
- `src/main/data/db/schemas/message.ts:17` — `messageTable`
- `src/main/data/db/schemas/file.ts` — `fileEntryTable`
- `src/main/data/db/schemas/fileRelations.ts` — `chatMessageFileRefTable`

### 文件类型分类
- `src/shared/types/file/common.ts:5` — `FILE_TYPE` 6 选 1(权威源)
- `src/renderer/types/file.ts:5` — `FILE_TYPE`(副本)
- `src/shared/utils/file/fileExtensions.ts` — 5 个扩展名白名单 + linguist 合并
- `src/main/utils/legacyFile.ts:121` — v1 `getFileType`(带 `isbinaryfile` 兜底)
- `src/main/utils/file/metadata.ts:16 / 22 / 27` — v2 `getFileType` / `isTextFile` / `mimeToExt`
- `src/main/services/FileStorage.ts:1018` — `_isTextFile`(v1 sniff 实现)
- `src/shared/types/file/common.ts:111-140` — `PhysicalFileMetadata` discriminatedUnion
- `src/main/utils/pdf.ts:9` — `extractPdfText`(pdf-parse 封装)

---

## § 11. 已知的坑与 deferred 工作

### 11.1 `compressImage` 是空操作
`FileStorage.ts:167-190` 三个分支都是 `copyFile`,**没有任何压缩逻辑**,但日志还说 "Image compressed successfully"(line 176)。v1 residue,等 `FileStorage` 退役后清理。

### 11.2 `FileUIPart` 不携带 `FILE_TYPE`
`buildFileParts` 构造的 `FileUIPart` **没有 6 分类字段**,只有 `mediaType`(MIME 字符串)。**业务路由不看 6 分类,只看 MIME**(在 `attachmentRouting` 走 `nativeFileSupport` 判断)。两套维度并行。

### 11.3 `.ts` 在 `mime` 包里有歧义
`mime.getType('ts')` 老版本返回 `application/typescript`,新版本可能返回 `video/mp2t`(MPEG-2 Transport Stream)。**代码里不要**靠 `mime.getType` 单点判定,以 `textExts` 这套白名单为准。

### 11.4 v2 没有 binary sniff 兜底
`metadata.ts:4-7` 注释承认 deferred。**生产中如果用户拖一个无扩展名的 `.gitignore` 进去,v2 会标 OTHER,被当 binary 处理**(走 native → inlining base64 把整个文件塞给 provider,provider 可能拒收)。

### 11.5 重复上传不复用 entry
`buildFileParts` 每个附件独立 create,即使 md5 相同也不复用。`FileStorage.findDuplicateFile` 那种 md5 去重在 v2 路径里没继承。

### 11.6 文件路径不稳定
`part.url = file://${physicalPath}` 在 `userData` 迁移后会失效,这是 entry id 才是真相、`url` 只是 fallback 的根本原因。

### 11.7 Phase 2 entry-handle 未完成
`FileManager.ts:676` 注释:`getMetadata(FileEntryHandle) is not yet wired (@phase 2)`。`getMetadata` 暂时只能走 `FilePathHandle`,所以 `buildFileParts` 还要 `createFilePathHandle(physicalPath)` 绕一层。

### 11.8 `PhysicalFileMetadata` 富化字段未填
`width/height/pageCount/encoding` 这些 per-kind 字段**当前都是 `undefined`**,等后续 phase 接入 sharp / pdf-lib / chardet 调用再填。

### 11.9 v1 路径不处理 PDF
见 § 9.5。

---

## § 12. 自测清单

读完后能回答以下问题,说明理解到位:

- [ ] **用户拖一个 5MB PNG 进 composer,renderer 端立即做了哪些操作?**(答:加进 composer state,只存 path,不读字节)
- [ ] **点"发送"时,文件字节的第一次落盘发生在哪?**(答:`FileManager.createInternalEntry`,拷到 `{userData}/Data/Files/{uuid}.{ext}`)
- [ ] **`FileUIPart` 上 4 个字段都是干嘛的?**(答:type=AI SDK part 类型;mediaType=MIME 字符串;url=`file://` 临时身份;filename=显示名)
- [ ] **`providerMetadata.cherry` 命名空间的意义?**(答:Cherry 私有字段不污染 AI SDK wire format,Provider 通常忽略)
- [ ] **provider 实际收到的是 base64 还是 file URL?**(答:base64 data URL,由 `materializeNativeFilePart` 在 main 端 inline)
- [ ] **`fileEntryId` 失效了怎么办?**(答:`materializeNativeFilePart` fallback 链:fallback A 读 `file://` URL;fallback B 保留原 URL;fallback C 降级为 note)
- [ ] **PDF 文档的文本抽取走哪条路径?**(答:v2 `attachmentTextExtraction.extract` → `extractPdfText`(pdf-parse);v1 `FileStorage.readFileCore` 不支持)
- [ ] **流式响应走哪条 IPC 通道?**(答:单 `IpcChannel.IpcApi_Event` 通道,event 名 `ai.stream_chunk` / `_done` / `_error`)
- [ ] **16ms 滚窗合流的目的是什么?**(答:避免每 token 触发 IPC + 避免每 token 触发 React render)
- [ ] **6 分类 `FILE_TYPE` 和 MIME 字符串的关系?**(答:6 分类给 UI 和存储用,MIME 给 provider 用;MIME 只在 `FileManager.getMetadata` 用 `mime.getType(ext)` 推断一次)
- [ ] **v2 `isTextFile` 有没有 binary sniff 兜底?**(答:没有,deferred。未知扩展名一律 OTHER)
- [ ] **`FileUIPart` 是否携带 `FILE_TYPE` 字段?**(答:不携带。业务路由靠 MIME + `nativeFileSupport`,不靠 6 分类)
- [ ] **重复上传同一文件,v2 路径会复用 entry 吗?**(答:不会,每次 create 都是新 UUID)
- [ ] **三套相关存储(FileStorage / FileManager / fileEntryTable)的关系?**(答:FileStorage 是 v1 纯 FS,singleton,即将退役;FileManager 是 v2 lifecycle 服务,接管字节 + DB;两者都把字节写到 `{userData}/Data/Files/{uuid}{ext}` 同目录)

---

## 附录 A:相关文档索引

仓库内:
- `docs/references/file/file-manager-architecture.md` — FileManager 架构
- `docs/references/file/architecture.md` — file 模块架构
- `docs/references/main-process-architecture.md` — main 进程模块划分
- `docs/references/lifecycle/README.md` — lifecycle 服务体系
- `docs/references/ipc/README.md` — IpcApi 范式

外部参考(本仓库):
- AI SDK 5(`@ai-sdk/*`):`UIMessagePart` / `providerMetadata` / `convertToModelMessages`
- pdf-parse:`extractPdfText` 用法
- word-extractor:`WordExtractor().extract(buffer).getBody()`
- officeparser:`parseOfficeAsync(buffer, {tempFilesLocation})`
- chardet + iconv-lite:编码检测与解码
- mime 包:`mime.getType(ext)` / `mime.getExtension(mime)`

---

## 附录 B:名词对照表

| 术语 | 含义 |
|---|---|
| `FileEntryId` | v2 附件的稳定身份(UUID),FileEntry 主键 |
| `FileUIPart` | AI SDK 5 `UIMessagePart` 的一个变体,type='file' |
| `CherryMessagePart` | Cherry 在 AI SDK 基础上的 part 联合扩展 |
| `providerMetadata.cherry` | Cherry 在 AI SDK 标准字段上的私有命名空间 |
| `fileTokenSourceId` | composer 端稳定 token id,跨消息追踪附件 |
| `ComposerAttachment` | composer state 中的附件 lean 描述 |
| `FILE_TYPE` | 6 分类(IMAGE/VIDEO/AUDIO/TEXT/DOCUMENT/OTHER) |
| `nativeFileSupport` | model 是否原生支持该文件类型(inline base64) |
| `materializeNativeFilePart` | 把 entry 字节读出 inline base64 的函数 |
| `extractDocumentText` | 把文档抽成纯文本的函数(非 native 时用) |
| `AiStreamRequest` / `AiStreamManager` | 流式请求封装 |
| `WebContentsListener` | per-window 定向 send chunk 的 listener |
| `IpcChatTransport` | renderer 端订阅 IPC event 拼 ReadableStream |