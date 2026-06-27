# ADK Event 字段渲染决策全解（Field → Reducer → UI）

> 文档目的：说清楚 ADK `Event` 每个字段在本项目里**被谁读、怎么读、影响什么 UI**，以便后续开发新功能时快速判断"哪个字段挂到哪里"。
>
> 原版参考：`~/.claude/projects/E--workplace-adk-web/memory/event-field-routing.md`（adk-web，Angular 前端）。两份文档的 Event 字段语义 1:1 对齐，因为 ADK 的 Python / Kotlin / TypeScript SDK 共用同一份 proto schema；本文件仅替换实现细节（Kotlin / Compose / Jetpack）。
>
> 修订记录：
> - v2（本次）：adk-web 对齐版。拆分 action / nodeInfo / parts 子字段；补 `event.interrupted` / `event.finishReason` / `event.requestedToolConfirmations`；新增 §5 派生字段层、§7 路线图、§8 自测清单、§10 相关笔记。
> - v1：初版。所有"当前未消费"字段用一行散列注释带过。

---

## 目录

- [§ 0. Android 端数据流](#0-android-端数据流)
- [§ 0.5. 一句话字段路由总结](#05-一句话字段路由总结)
- [§ 1. Event 顶层字段](#1-event-顶层字段)
  - 1.1 `id` / 1.2 `author` / 1.3 `invocationId` / 1.4 `partial` / 1.5 `interrupted` / 1.6 `turnComplete` / 1.7 `inputTranscription` / 1.8 `outputTranscription` / 1.9 `longRunningToolIds` / 1.10 `errorCode`/`errorMessage` / 1.11 `output` / 1.12 `branch` / 1.13 `usageMetadata` / 1.14 `customMetadata` / 1.15 `timestamp` / 1.16 `finishReason` / 1.17 `requestedToolConfirmations`
- [§ 2. `Event.content.parts[]` 字段](#2-eventcontentparts-字段)
  - 2.1 `text` / 2.2 `thought` / 2.3 `inlineData` / 2.4 `fileData` / 2.5 `functionCall` / 2.6 `functionResponse` / 2.7 `executableCode` / 2.8 `codeExecutionResult` / 2.9 `a2ui` / 2.10 `Content.role`
- [§ 3. `Event.actions` 字段](#3-eventactions-字段)
  - 3.1 `stateDelta` / 3.2 `artifactDelta` / 3.3 `agentState.nodes` / 3.4 `endOfAgent` / 3.5 `route` / 3.6 `transferToAgent` / 3.7 `functionCall` / `functionResponse` / `finishReason` / `message`
- [§ 4. `Event.nodeInfo` 字段](#4-eventnodeinfo-字段)
  - 4.1 `path` / 4.2 `messageAsOutput` / 4.3 `outputFor` / 4.4 `attrEventId` / `attrInvocationId` / `attrAssociatedEventIds`
- [§ 5. 派生字段（Android 端 Message 加工层）](#5-派生字段android-端-message-加工层)
- [§ 6. 当前 Android 端已消费的字段子集](#6-当前-android-端已消费的字段子集)
- [§ 7. 字段扩展优先级路线图](#7-字段扩展优先级路线图)
- [§ 8. 复习自测清单](#8-复习自测清单)
- [§ 9. 本项目关键文件清单](#9-本项目关键文件清单)
- [§ 10. 相关笔记](#10-相关笔记)

---

## 0. Android 端数据流

```
[后端 / InMemoryRunner Event]
  │
  ▼ AgentChatRunner.kt
  │   runner.runAsync(...) : Flow<Event>
  │
  ▼ ChatViewModel.send()
  │   runner.send(...).collect { event -> applyEvent(event) }
  │
  ▼ ChatViewModel.applyEvent / mergePartialEvent / appendCompleteEvent / buildMessageFromParts
  │   ├─ event.errorCode / event.errorMessage      → Messages.fromError(...)
  │   ├─ event.partial                             → mergePartialEvent vs appendCompleteEvent 分流
  │   ├─ event.author                              → authorToRole() → MessageRole
  │   ├─ event.content.parts[].{ text, thought }   → TextPart 累积（addTextToParts）
  │   ├─ event.functionCalls() / functionResponses → FunctionCallView / FunctionResponseView
  │   ├─ event.turnComplete                        → 翻转 _isStreaming
  │   └─ event.invocationId                        → Message.invocationId（同一 turn 关联）
  ▼
  [Message] (model/Message.kt — 渲染用视图模型)
  │
  ▼ MessageBubble.kt (Compose)
  │   ├─ message.role             → ChatBubble 左右 + color
  │   ├─ message.textParts        → Markdown（partial 时走 StreamingMarkdownState）
  │   ├─ message.functionCalls    → ToolCallChip 行
  │   ├─ message.functionResponses→ ToolResponseChip 行
  │   └─ part.thought             → ThoughtBubble vs 普通 Markdown
  ▼
  ChatBubble.kt / ThoughtBubble.kt / ToolCallChip.kt / ErrorBubble.kt
```

> **重要**：本项目当前**只消费了 Event 的一个最小子集**（见 § 6）。其余字段（同 `branch`、`output`、`inputTranscription`、`outputTranscription`、`actions.stateDelta`、`actions.transferToAgent` 等）在 ADK Event 里**存在但尚未在本端渲染**。下面每节会显式标注 `当前未消费`，方便后续"挂上某个新气泡/chip"。**v2 起，每个未消费字段都附带"扩展点说明"，告诉你在哪里写 reducer / 新建什么 Bubble。**

---

## 0.5. 一句话字段路由总结

> **`event.id` → 列表 key / `event.author` → role → 气泡左右 / `event.invocationId` → partial 合并键 / `event.partial` → 流式开关 / `event.interrupted` → 音频停止（未消费） / `event.turnComplete` → 关闭 streaming / `event.inputTranscription` → 语音输入气泡（未消费） / `event.outputTranscription` → 语音输出气泡（未消费） / `event.longRunningToolIds` → OAuth 弹窗（未消费） / `event.errorCode/errorMessage` → 错误气泡 / `event.output` → 节点输出 JSON（未消费） / `event.branch` → 多 Tab（未消费） / `content.parts[].text+thought` → TextPart 累积 + 流式 Markdown + ThoughtBubble 切换 / `content.parts[].inlineData` → 媒体（未消费） / `content.parts[].fileData` → artifact 媒体（未消费） / `content.parts[].functionCall+functionResponse` → ToolCallChip / ToolResponseChip / `content.parts[].executableCode` → 代码块（未消费） / `content.parts[].codeExecutionResult` → 执行结果（未消费） / `content.parts[].a2ui` → 动态 UI（未消费） / `actions.stateDelta` → State chip（未消费） / `actions.artifactDelta` → Artifact chip + 异步加载（未消费） / `actions.agentState.nodes` → 工作流图（未消费） / `actions.endOfAgent` → completed chip（未消费） / `actions.route` → 路由 chip（未消费） / `actions.transferToAgent` → 转移 chip（未消费） / `nodeInfo.path` → 缩进线（未消费） / `nodeInfo.messageAsOutput` → 特殊渲染（未消费） / `nodeInfo.outputFor` → tooltip（未消费） / `usageMetadata` → 仅日志（未渲染） / `customMetadata.a2a:response` → A2UI 分支（未消费） / `timestamp` → 未消费 / `finishReason` / `requestedToolConfirmations` → 未消费。**

---

## 1. Event 顶层字段

### 1.1 `event.id: string`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.buildMessageFromParts`：`id = event.id` | 写入 `Message.id`，稳定锚点 |
| `MessageBubble` / `Message`：`id` 用于 key | Compose 列表 diff；同 id 的 partial 事件不会产生新行 |

**作用**：Event 的**唯一标识符**。Reducer 用它区分"同一条流"的多个 Event；UI 用它做列表 key。

> **与 adk-web 对照**：adk-web 用 `id` 在 history 里反查已有 `UiEvent`（`chat.component.ts:1293`），并维护 `eventData: Map<id, Event>`。本项目 reducer 按 partial / non-partial 分流，不需要 id 索引——但**未来做 `LocalReplayService`（本地回放 / 离线缓存）时，需要参照 adk-web 的 eventData Map 模式**。

---

### 1.2 `event.author: string`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.authorToRole`：`author == "user" → User`，否则 `Assistant` | 写入 `Message.role`，决定气泡左右 |
| `MessageBubble.ChatBubble(role = ...)` | Material3 对齐、配色 |

**作用**：决定**气泡归属（User / Assistant）**。

> **与 adk-web 对照**：adk-web 还会派生头像颜色（`AuthorService`，`chat.component.ts:136, 228`），并把 `inputTranscription` 的 author 强制设为 `'user'`、`outputTranscription` 强制设为 `'bot'`（`chat.component.ts:1214-1218`）。本项目当前没有自定义头像，按 `role` 走 Material3 默认配色。**多 agent / sub-agent / Live ASR 区分头像时再扩展**——扩展点：把 `event.author` 直接传给 `ChatBubble` 一个新参数 `authorName: String?`，并由 `ChatAvatar` 按 author 哈希取色。

---

### 1.3 `event.invocationId: string`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.buildMessageFromParts`：`invocationId = event.invocationId` | 写入 `Message.invocationId` |
| `ChatViewModel.mergePartialEvent`：合并前置条件之一 | `"last.partial && last.author == author && last.invocationId == event.invocationId"` |

**作用**：标记同一 turn 的多个 Event。当前用作**partial 合并的去重键**之一。

> **与 adk-web 对照**：adk-web 有完整的 `invocationId` filter chip + trace 跳转（`chat.component.ts:236-268, 1907-1930`）。本项目**尚未做 UI 显式分组**。扩展点：在 `ChatScreen` 顶部加 `InvocationFilterChip` 列表，由 `ChatViewModel.invocationIds: StateFlow<List<String>>` 维护。

---

### 1.4 `event.partial: boolean`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.applyEvent`：`if (event.partial) mergePartialEvent(event) else appendCompleteEvent(event)` | **核心**：流式合并的开关 |
| `ChatViewModel.mergePartialEvent`：`msg.partial && msg.invocationId == event.invocationId && ...` | 找上一条 partial 消息 |
| `Message.copy(partial = event.partial)` | 标记 Message 是流式中还是已完成 |
| `MessageBubble.RenderTextPart.partial` | 决定走 `rememberStreamingMarkdownState` 还是静态 `Markdown(content=...)` |

**作用**：**流式合并的核心开关**。详见 `ChatViewModel.applyEvent` 与 `addTextToParts`。

> **🚨 Bug 隐患**：用户消息或已完成的 assistant 消息**绝不能**走 streaming 路径（`chunkChannel == null` 时 streaming state 无法显示文本）。`RenderTextPart` 里 `if (partial) ... else { Markdown(content = part.text) }` 是关键分支。任何新增的 bubble 类型都不要在 partial=true 时用 `Markdown(content = ...)`——必须走 `rememberStreamingMarkdownState`。

> **与 adk-web 对照**：adk-web 的 `mergePartialEvent` 还额外区分 input 转写、output 转写、普通 content 三类（`chat.component.ts:1272-1277`）。本项目目前只消费普通 content——见 § 1.7/1.8 扩展点。

---

### 1.5 `event.interrupted: boolean` 🆕（adk-web 已有，本地 v1 未单独列出）

→ **当前未消费**。

| adk-web 读法（参照） | 影响什么 |
|---|---|
| `chat.component.ts:711-713`：`if (apiEvent.interrupted \|\| (apiEvent.inputTranscription !== undefined && apiEvent.partial))` | **停止音频播放** |
| `event-content.component.html:47-53`：`@if (uiEvent.event.interrupted)` | 渲染红色 "Interrupted" chip |

**扩展点**：本项目未启用 Live（双向流式语音）。如未来接入 Gemini Live：
- 在 `AgentChatRunner` 收到 `partial=false + interrupted=true` 时，调用 `TTS.stop()`；
- 在 `ChatViewModel.applyEvent` 里：`if (event.interrupted) msg.interrupted = true`，并新建 `InterruptedChip`（红色 surfaceError 色）。

---

### 1.6 `event.turnComplete: boolean`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.appendCompleteEvent`：`if (event.turnComplete) _isStreaming.value = false` | 关闭 streaming 状态 |

**作用**：标记**当前 turn 已结束**。当前仅用作内部状态；**未渲染**显式 "Turn Complete" chip（adk-web 有 `event-content.component.html:39-45`）。

**扩展点（P0 简单）**：在 `MessageBubble` 里 `if (message.partial == false && turnComplete == true)` 时下方加一个灰色 "Turn Complete" 小 chip。

---

### 1.7 `event.inputTranscription: { text: string }`

→ **当前未消费**（v1 文档散列在 § 1.9 之后，本次单独提出来）。

**adk-web 完整处理流程**：
1. `chat.component.ts:1214-1216`：`apiEvent.author = 'user'`（强制设 role）
2. `chat.component.ts:1272-1277`：partial 合并的分流（input 转写 vs output 转写 vs 普通）
3. `chat.component.ts:1295-1317`：非 partial input 转写 → 反向找最近 partial input 转写合并
4. `chat.component.ts:1376, 1390-1393`：`text` 累加到 `lastEvent.event.inputTranscription.text`
5. `event-content.component.html:22-24`：渲染灰色转写气泡（role=user）
6. `content-bubble.component.html:250-251`：显示 `inputTranscription.text`

**扩展点**：本项目未启用 Live ASR。如未来接入 Gemini Live：
- 在 `ChatViewModel.applyEvent` 入口加分流：`if (event.inputTranscription != null) { applyTranscription(event, TranscriptionSide.INPUT); return }`
- 新增 `Message.transcription: TranscriptionSide?` 字段（`enum class TranscriptionSide { INPUT, OUTPUT }`）
- 新增 `TranscriptionBubble`（灰色 surfaceVariant，与 ThoughtBubble 类似的"非主内容"气泡）

---

### 1.8 `event.outputTranscription: { text: string }`

→ **当前未消费**。与 § 1.7 **完全对称**，仅 role='bot'。

**扩展点**：见 § 1.7。

---

### 1.9 `event.longRunningToolIds: string[]`

→ **当前未消费**。ADK 异步工具 / OAuth 弹窗需要走这个字段。

**adk-web 完整处理流程**：
- `chat.component.ts:1233-1264`：`if (longRunningToolIds.length > 0)` → 触发 OAuth 弹窗 + long-running response 表单
- `chat.component.ts:2335`：`longRunningToolIds?.includes(fc.id)` → 标记 `functionCall.isLongRunning=true`

**扩展点**：本项目工具调用是同步阻塞在 `InMemoryRunner` 内的，没有长跑分支。如未来接入 `LongRunningFunctionTool`：
- 在 `ChatViewModel.applyEvent` 里：`if (event.longRunningToolIds.isNotEmpty()) { handleLongRunningTools(event) }`
- `handleLongRunningTools`：从 `event.content.parts` 找出匹配的 `functionCall` → 在底部弹 `LongRunningResponseDialog`（adk-web 有 `app-long-running-response`）

---

### 1.10 `event.errorCode / event.errorMessage: string`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.applyEvent`：`event.errorCode != null \|\| !errorMessage.isNullOrBlank()` → `applyError(...)` | 走错误分支 |
| `Messages.fromError(...)` | 构造 `Message(error = ...)` |
| `ErrorBubble` | 红色错误气泡（见 `ui/chat/ErrorBubble.kt`） |

**作用**：**所有错误的统一入口**。注意：`Openai.kt:mapToErrorResponse` 会构造 `LlmResponse(errorMessage = e.message, finishReason = SAFETY / _UNSPECIFIED)`，被 ADK 转成 Event 流转到此处。

> **Bug 隐患**：adk-web 会同时把错误 JSON（`finishReason`、`code`）作为 JSON viewer 展示（`content-bubble.component.html:243-248`）。本项目只显示 `errorMessage` 字符串——**未来如要"展开 JSON 看完整错误详情"，需要在 `Messages.fromError` 里增加 `errorJson: String?` 字段，并在 `ErrorBubble` 加一个 "Details" 折叠区**。

---

### 1.11 `event.output: { result?: any }`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `event-content.component.html:16-21`：`@if (uiEvent.event.output)` → 渲染 `<app-content-bubble type="output">`
- `content-bubble.component.html:237-242`：`<app-custom-json-viewer [json]="uiEvent.event.output!">`
- `content-bubble.component.html:240`：`[appJsonTooltip]="nodeInfo.outputFor || nodePath"`

**作用**：节点输出 JSON 卡片。

**扩展点**：本项目尚未用 `SequentialAgent` / `ParallelAgent` 多节点编排，没有"节点 output"概念。一旦引入：
- 在 `ChatViewModel.applyEvent` 里：`if (event.output != null) appendOutputCard(event)`
- 新增 `OutputBubble`（白底 + JSON viewer + 右上角 tooltip 显示 `outputFor`）

---

### 1.12 `event.branch: string`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat-panel.component.ts:415-417`：`const branchId = event.event?.branch`
- 把同 branch 的事件聚合到一个 `<mat-tab-group>`

**作用**：多分支（agent 多路径执行）模式下，把同分支的事件归到一个 Tab。

**扩展点**：本项目未启用多分支。引入 `BranchAgent` 时：
- 在 `ChatViewModel` 维护 `branches: Map<branchId, List<Message>>`
- `ChatScreen` 改成 `TabRow` + `HorizontalPager`，每 branchId 一页

---

### 1.13 `event.usageMetadata: { promptTokenCount, candidatesTokenCount, totalTokenCount }`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `Openai.kt.toUsageMetadata` 把 OpenAI 的 `CompletionUsage` → ADK `UsageMetadata` | 写入 Event.usageMetadata |

**作用**：**仅作日志 / trace**。当前 Android UI **不展示** token 计数。

> **与 adk-web 一致**：adk-web 也没有渲染 usage metadata，仅 trace / 日志使用。**未来要做"成本 / usage 看板"**：在 `Message` 模型上增加 `usage: UsageMetadata?` 字段；在 reducer 里赋值；在 `MessageBubble` 折叠区显示"Prompt: X / Output: Y / Total: Z"。

---

### 1.14 `event.customMetadata: Map<String, Any>`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1457-1459`：`!!event?.customMetadata?.['a2a:response']` → **判断 A2A 响应**

**作用**：标记 A2A / 其他自定义协议分支。

**扩展点**：本项目不识别 `a2a:response`。如未来接入 A2UI（详见 adk-web 的 [[a2ui-protocol]]）：
- 在 `ChatViewModel.applyEvent` 里：`if (event.customMetadata?.get("a2a:response") != null) applyA2ui(event)`
- A2UI 渲染需要 `a2ui-canvas` Compose 组件，本地尚无

---

### 1.15 `event.timestamp: number`

→ **当前未消费**。Event 层的 timestamp 没用上；UI 用的是 `Message.timestamp = Clock.System.now().toEpochMilliseconds()`（reducer 落库时取当前时间）。

**扩展点**：未来要做"消息列表按时间排序 / 显示完整时间戳"时，把 `Message.timestamp = event.timestamp ?: Clock.System.now().toEpochMilliseconds()` 即可——**优先用 Event 自带，避免 reducer 注入延迟造成排序错位**（adk-web 也没用 Event.timestamp）。

---

### 1.16 `event.finishReason: string` 🆕（adk-web 有但本端 v1 漏掉）

→ **当前未消费**。`Openai.kt:mapToErrorResponse` 里有 `finishReason = SAFETY / _UNSPECIFIED` 转换，但**没有传到底层 Event / UI**。

**adk-web 引用**：`actions.finishReason` 在 type 定义里出现，无渲染逻辑（`event-field-routing.md` § 3.7）。

**扩展点（P0 简单）**：
- 在 `Openai.kt` 改 `mapToErrorResponse`：把 `finishReason` 一并塞进 `errorMessage` 字符串，或者新建一个 `LlmResponse.errorCode = "SAFETY"` 等枚举值透传到 Event；
- 在 `ErrorBubble` 折叠区显示：`Safety / Length / Recitation / ...` 让用户知道"为什么失败"。

---

### 1.17 `event.requestedToolConfirmations: any` 🆕（adk-web 有但本端 v1 漏掉）

→ **当前未消费**。

**adk-web 引用**：仅在 `actions.requestedToolConfirmations` 类型定义中出现，无渲染逻辑。

**扩展点**：本项目目前工具调用为同步执行，没有"用户确认"分支。如未来引入"危险工具二次确认"（如 `BashTool` / `FileDeleteTool`）：
- 在 `ChatViewModel.applyEvent` 里：`if (event.requestedToolConfirmations != null) showConfirmationDialog(event)`
- 新增 `ToolConfirmationDialog`（仿 Material3 `AlertDialog`）

---

## 2. `Event.content.parts[]` 字段

### 2.1 `part.text: string`（含可空）

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.applyPartToMessage`：`part.text != null && text.isNotEmpty() → addTextToParts(message, text, thought)` | 累积到 `Message.textParts` |
| `MessageBubble` 渲染 | 走 `Markdown`（流式）或 `Markdown(content)`（静态） |

**作用**：**最核心——文本内容**。

---

### 2.2 `part.thought: boolean`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.applyPartToMessage`：`thought = part.thought == true` | 传给 `addTextToParts` |
| `ChatViewModel.addTextToParts`：`if (last.thought == thought) 追加段 else 新段` | **段落切分**：同 thought 标志合并，异则新建 `TextPart` |
| `MessageBubble.RenderTextPart`：每段 `TextPart` 按 `thought` 分发 | `thought = true` → `ThoughtBubble`（灰色 surfaceVariant），否则 → 普通 Markdown 段 |

**作用**：**区分思考 vs 普通文本——视觉 + 段落切分**。

> **与 adk-web 等价**：同一 Message 里可能出现多段 alternating thought/answer，reducer 用末段 `thought` 标志决定是追加还是开新段。`ThoughtBubble` 的 `showHeaderLabel` 也与 adk-web 的 `@if (part.thought && type !== 'thought') { <div class="output-chip-header">thought</div> }` 对应。
>
> **进阶**：adk-web 还会在 thought 文本中剥离 `/*PLANNING*/` / `/*ACTION*/` 等前缀（`chat.component.ts:4141 processThoughtText`）。本地尚未做，**详见本地 `chat-streaming-and-thought` 调研后再决定要不要引入**。

---

### 2.3 `part.inlineData: { mimeType, data }` 🆕（本端 v1 合并在 § 2.5，本次独立列出）

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1668-1681`：写入 `uiEvent.inlineData = {data, mimeType, mediaType}`
- `content-bubble.component.html:115-170`：`inlineData.mediaType` switch：
  - **IMAGE** → `<img>` 渲染
  - **AUDIO** → `<audio-player>`（base64 → `<audio>` 标签）
  - **VIDEO** → `<video>`
  - **TEXT** → 文本块
  - **default** → "下载链接"按钮
- `content-bubble.component.html:172-193`：user 角色的简化分支（按 mimeType 前缀分发）

**作用**：所有媒体内容（图、音、视、文、HTML）的载体。本端纯文本 chat，**没有任何 `inlineData` 渲染分支**。

**扩展点**（P2）：
- 在 `Message` 模型加 `inlineData: InlineDataView?`（`data: String, mimeType: String, mediaType: MediaType`）
- 在 `MessageBubble` 增加 `inlineData` 分发：`ImageBubble` / `AudioBubble` / `VideoBubble` / `TextBlobBubble` / `DownloadLinkBubble`
- 注意 `data` 一般是 base64，需要 `Base64.decode` → `BitmapFactory.decode` / `MediaPlayer` setDataSource

---

### 2.4 `part.fileData: { mimeType, fileUri }` 🆕（本端 v1 漏掉）

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:113`：`event?.content?.parts.some(p => p.fileData)` → `shouldShowMessageCard()` 判断
- `content-bubble.component.html:195-203`：`part.fileData.mimeType.startsWith('audio/')` → 触发 audio artifact 加载
- `content-bubble.component.ts:174-223`：解析 `artifact://` URI → fetch artifact → pcm → wav → 渲染音频播放器

**作用**：指向 artifact 服务（而不是 inline base64）的媒体。

**扩展点**（P3）：如未来引入 ADK `ArtifactService`，需要：
- 在 `Message` 加 `artifactUri: String?` 字段
- 在 `ChatViewModel.applyEvent` 里：异步 `fetchArtifact(uri) → inlineData` —— **复用 § 2.3 的渲染分支**，避免重复

---

### 2.5 `part.functionCall: { id, name, args }`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.buildMessageFromParts`：`event.functionCalls().map { it.toView() }` | 写入 `Message.functionCalls` |
| `Openai.kt.toolCalls.forEach { ... Part(functionCall = FunctionCall(...)) }` | 把 OpenAI tool_calls 还原成 ADK `Part.functionCall` |
| `MessageBubble` 渲染 | 顶部 `ToolCallChip` 行 |
| `FunctionCall.toView()` | `name(argsSummary)` 文本，`argsSummary` 由 `summarizeValue` 递归生成 |

**作用**：**工具调用 → chip**。

> **对比 adk-web**：adk-web 的 chip 支持 hover 看完整 JSON args（`event-content.component.ts:102-137 getFunctionCallButtonText` 之上有 tooltips）。本项目扁平化为一行 `name(key=value, ...)`——**要做"hover / 展开 JSON"需要扩展 `FunctionCallView` 增加 `argsJson: String` 字段**。

---

### 2.6 `part.functionResponse: { id, name, response }`

| 当前 Android 端读法 | 影响什么 |
|---|---|
| `ChatViewModel.buildMessageFromParts`：`event.functionResponses().map { it.toView() }` | 写入 `Message.functionResponses` |
| `ToolResponseChip` 渲染 | 浅色小 chip，显示 `name ✓` |

**作用**：工具响应 chip。当前的最小实现——**没有显示 response 内容**（adk-web 有"展开 JSON"功能，且能识别 computer-use 响应做截图对比）。

**扩展点**（P1）：
- `FunctionResponseView` 加 `responseJson: String` 字段
- `ToolResponseChip` 加"展开"按钮 → 折叠 `JsonViewer`
- 如用 OpenAI computer-use 模型，加 `isComputerUseResponse` 分支 → 加载截图

---

### 2.7 `part.executableCode: { language, code }` 🆕（本端 v1 漏掉）

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1719-1720`：写入 `uiEvent.executableCode`
- `content-bubble.component.html:105-106`：`<code>{{ uiEvent.executableCode.code }}</code>`

**作用**：显示代码块。

**扩展点**（P2）：本项目未启用 Code Execution 工具。如未来接入：
- 在 `Message` 加 `executableCode: ExecutableCodeView?` 字段
- 新增 `CodeBlock` Compose（参考 m3-markdown SDK 的 `Code` 组件，源码在 `E:\workplace\multiplatform-markdown-renderer`）
- `MessageBubble` 分发：`@Composable fun RenderExecutableCode(part: ExecutableCodeView)`

---

### 2.8 `part.codeExecutionResult: { outcome, output }` 🆕（本端 v1 漏掉）

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1721-1722`：写入 `uiEvent.codeExecutionResult`
- `content-bubble.component.html:107-111`：显示 `Outcome: SUCCESS/FAILURE` + `Output: ...`

**扩展点**（P2）：与 § 2.7 配套。如用 Gemini Code Execution：
- 在 `Message` 加 `codeExecutionResult: CodeExecutionResultView?`
- 新增 `CodeExecutionResultBubble`

---

### 2.9 `part.a2ui: any` 🆕（本端 v1 漏掉）

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1723-1724`：写入 `uiEvent.a2uiData`
- `content-bubble.component.html:96-102`：渲染 `<app-a2ui-canvas>`（详见 adk-web 的 [[a2ui-protocol]]）

**作用**：A2UI（Agent-to-UI）动态 UI。详见 adk-web 的 a2ui-protocol.md（22 KB 详细协议）。

**扩展点**（P3）：A2UI 是 Google 提出的"agent 动态生成 UI surface"协议。如要支持：
- 引入 `com.google.adk.a2ui` 模块（如果 ADK Kotlin 提供）
- 新增 `A2uiCanvas` Compose 组件
- 与 § 1.14 `customMetadata.a2a:response` 联动判定

---

### 2.10 `Content.role`

`Content(role = Role.USER / Role.MODEL)` 被 `Openai.kt` 用作发送消息时的角色映射（`Role.USER → toUserMessages`、`Role.MODEL → toModelMessages`），**与渲染解耦**——UI 不读 `Content.role`，只读 § 1.2 `event.author`。

---

## 3. `Event.actions` 字段

### 3.1 `event.actions.stateDelta: any`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1420-1428`（`processActionStateDelta`）：合并到 `currentSessionState`
- `event-content.component.html:100-108`：`@if (stateKeys.length > 0)` → 渲染 "State: ..." chip
- `event-content.component.ts:175-185`：`getFilteredStateKeys` / `getFilteredStateDelta` → 过滤 `__llm_request_key__`

**作用**：状态变更。生成 chip + 更新全局 session state。

**扩展点**（P3）：
- `Message` 加 `stateDelta: Map<String, Any>?`
- reducer 里 `if (event.actions?.stateDelta != null) msg.stateDelta = event.actions.stateDelta`
- 新增 `StateDeltaChip` 显示 `key=value` 列表

---

### 3.2 `event.actions.artifactDelta: any`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:1353-1362`：`if (apiEvent.actions?.artifactDelta)` → 触发 `renderArtifact()`：拉取 artifact 数据 → 写入 `uiEvent.inlineData`
- `event-content.component.html:109-119`：渲染 "Artifact: ..." chip

**作用**：工件版本更新。生成 chip + 异步拉取数据填入 inlineData。

**扩展点**（P3）：
- `Message` 加 `artifactDelta: ArtifactDeltaView?`
- 新增 `ArtifactChip` + 异步 fetch artifact 转换到 `inlineData`（**复用 § 2.3 渲染**）

---

### 3.3 `event.actions.agentState.nodes: {...}`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `event-content.component.html:142-155`：`hasWorkflowNodes()` → `<button [appWorkflowGraphTooltip]="...">` → **"Agent State" 按钮**（带工作流图 tooltip）

**作用**：节点状态展示（多节点编排）。

**扩展点**（P4）：见 § 4.1 — `nodePath` 一起做才有意义。

---

### 3.4 `event.actions.endOfAgent: boolean`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `event-content.component.html:156-161`：`@if (hasEndOfAgent())` → 渲染 "<author> completed!" chip

**作用**：子 agent 完成的提示 chip。

**扩展点**（P2）：扩展 `ChatViewModel.authorToRole` 支持多 agent：`role = event.author.toAgentRole()`；新增 `EndOfAgentChip`。

---

### 3.5 `event.actions.route: any`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `event-content.component.html:126-133`：`@if (uiEvent.route)` → 渲染 "route: ..." chip

**扩展点**（P3）：multi-agent routing 时复用 § 3.4 的 chip 框架。

---

### 3.6 `event.actions.transferToAgent: any`

→ **当前未消费**。

**adk-web 完整处理流程**：
- `event-content.component.html:134-141`：`@if (uiEvent.transferToAgent)` → 渲染 "author → targetAgent" chip
- `event-content.component.ts:204-209`：`getTransferTargetName()`

**作用**：agent 转移提示（多 agent 协作）。

**扩展点**（P3）：与 § 3.4 / § 3.5 一起做。

---

### 3.7 `event.actions.functionCall / functionResponse / finishReason / message`

→ **当前未消费**。

**adk-web 引用**：仅在 type 定义里出现，**无渲染逻辑**（与事件本身的 `content.parts[].functionCall` 分开）。

**扩展点**：通常这三个字段是 SDK 内部 bookkeeping，不需要单独渲染。**注意 `finishReason`**——见 § 1.16。

---

## 4. `Event.nodeInfo` 字段

### 4.1 `event.nodeInfo.path: string` 🆕

→ **当前未消费**。

**adk-web 完整处理流程**：
- `chat.component.ts:131`：`uiEvent.nodePath` getter：`this.event?.nodeInfo?.path`
- `event-row.component.html:94-99`（`indentationDepth`）：`nodePath.split('/')` → **缩进线**（按深度画）

**作用**：节点的层级路径（"root/sub_agent_1/llm_node"）。adk-web 用它画左侧缩进线，让多节点编排的层级关系可视化。

**扩展点**（P3）：
- `Message` 加 `nodePath: String?`
- `MessageBubble` 改为支持 `Row { Canvas(...) TextColumn(...) }`，左侧按 `nodePath.count('/')` 画缩进线

---

### 4.2 `event.nodeInfo.messageAsOutput: boolean` 🆕

→ **当前未消费**。

**adk-web 完整处理流程**：
- `content-bubble.component.html:28-42`：`@if (uiEvent.event.nodeInfo?.['messageAsOutput'])` → 切换到"messageAsOutput"渲染模式（JSON tooltip + 文本）
- `content-bubble.component.ts:93-105`：`jsonOutputData` getter → 解析 text 为 JSON

**作用**：节点消息作为输出（带 JSON tooltip 的特殊渲染）。

**扩展点**（P4）：与 § 4.1 一起做才有意义。

---

### 4.3 `event.nodeInfo.outputFor: any` 🆕

→ **当前未消费**。

**adk-web 完整处理流程**：
- `content-bubble.component.html:240`：`[appJsonTooltip]="uiEvent.event.nodeInfo?.['outputFor'] || uiEvent.nodePath"` → output 类型气泡的 tooltip 文本

**作用**：output 类型的 tooltip 显示"这是哪个节点的输出"。

**扩展点**（P4）：与 § 1.11 `event.output` 一起做。

---

### 4.4 `attrEventId / attrInvocationId / attrAssociatedEventIds` 🆕

→ **当前未消费**。

**adk-web 引用**：`chat.component.ts:3115 等` 用于 trace 视图。

**扩展点**（P5）：trace 视图整个项目都没有，不在近期路线。

---

## 5. 派生字段（Android 端 Message 加工层） 🆕

> 与 adk-web 不同，本项目的 reducer **不会**构造 `UiEvent` 对象——而是把 Event 字段映射到 `Message` 数据类。本节列出 `Message` 上每个字段的来源、是否可选、为何命名如此。

| `Message` 字段 | 来源 Event 字段 | 类型 | 何时填充 | 渲染影响 |
|---|---|---|---|---|
| `id` | `event.id` | `String` | 每次 reducer 都设 | Compose `key` |
| `role` | `event.author`（经 `authorToRole`） | `MessageRole`（enum User/Assistant） | 同上 | 气泡左右 + 配色 |
| `invocationId` | `event.invocationId` | `String?` | 同上 | partial 合并键（不在 UI 显示） |
| `partial` | `event.partial` | `Boolean` | 同上 | 流式 Markdown 开关 |
| `textParts` | `event.content.parts[].{text, thought}` 累积 | `List<TextPart>` | `addTextToParts` | 段落级渲染 |
| `functionCalls` | `event.functionCalls().map { it.toView() }` | `List<FunctionCallView>` | `buildMessageFromParts` | 顶部 `ToolCallChip` 行 |
| `functionResponses` | `event.functionResponses().map { it.toView() }` | `List<FunctionResponseView>` | 同上 | 顶部 `ToolResponseChip` 行 |
| `error` | `event.errorCode / event.errorMessage` | `MessageError?` | `applyError` | 红色错误气泡 |
| `timestamp` | **`Clock.System.now()`**，**而非** `event.timestamp` | `Long` | 每次 reducer 入口 | 列表排序、消息时间戳 |

> **派生约定（与 adk-web 的 UiEvent 加工对应）**：
> - adk-web 有 `systemInstructionChanged` / `precedingSystemInstruction` / `currentSystemInstruction` 派生（§ 5 in adk-web 的 [[event-field-routing]]），用来做"性能 / cache miss 提示" chip；
> - 本项目**不派生**这些——因为 `Openai.kt` 是每次 `runAsync` 都重发完整 system instruction，没有这个派生需求。
>
> **未来如要做"性能 / cache miss 提示"**：在 reducer 里维护 `lastSystemInstructionRef: String?`，对比 `event.actions.stateDelta` 携带的 `__system_instruction_hash__` 类似字段（如有），比对失败则设 `message.systemInstructionChanged = true`，渲染黄色 chip + 点击打开 diff 对话框（仿 adk-web 的 SystemInstructionDiffDialogComponent）。

---

## 6. 当前 Android 端已消费的字段子集

把上面"已消费"集中成一张表，方便快速判断"再加新气泡 / 新 chip 时要修改哪个 reducer 分支"。

| Event 字段 | 读它的位置 | 渲染目标 |
|---|---|---|
| `event.id` | `buildMessageFromParts` | `Message.id` / `key` |
| `event.author` | `authorToRole` | `Message.role` → 气泡左右 |
| `event.invocationId` | `mergePartialEvent`, `buildMessageFromParts` | partial 合并键 + `Message.invocationId` |
| `event.partial` | `applyEvent`, `mergePartialEvent`, `RenderTextPart` | 分流 + 流式 Markdown |
| `event.turnComplete` | `appendCompleteEvent` | `_isStreaming = false` |
| `event.errorCode` / `errorMessage` | `applyError` | 错误气泡（`ErrorBubble`） |
| `event.content.parts[].text` | `applyPartToMessage`, `addTextToParts` | `TextPart` 累积 |
| `event.content.parts[].thought` | 同上 | `TextPart.thought` 切换 bubble 样式 |
| `event.functionCalls()` | `buildMessageFromParts`, `FunctionCall.toView` | `ToolCallChip` |
| `event.functionResponses()` | 同上 | `ToolResponseChip` |

> **未消费、但 Event 里存在的字段**（按 adk-web 表格，本端确认存在但未接）：`interrupted`, `inputTranscription`, `outputTranscription`, `longRunningToolIds`, `output`, `branch`, `customMetadata.*`, `actions.{stateDelta, artifactDelta, agentState, endOfAgent, route, transferToAgent, functionCall, functionResponse, finishReason, message}`, `nodeInfo.*`, `usageMetadata`, `timestamp`, `finishReason`, `requestedToolConfirmations`, `parts[].{inlineData, fileData, executableCode, codeExecutionResult, a2ui}`。**这些是后续做"完整对话体验"时的扩展点**，详见 § 7 路线图。

---

## 7. 字段扩展优先级路线图 🆕

> 按"实现难度 / 业务价值"分级。每个标 Px 的条目都对应 § 1-4 中的具体扩展点段落。

| 优先级 | 功能 | 涉及字段 | 难度 | 备注 |
|---|---|---|---|---|
| **P0** 简单 | Turn Complete 灰色 chip | `event.turnComplete` | 低 | MessageBubble 末尾加 chip |
| **P0** 简单 | finishReason 透传 + 错误详情 | `event.finishReason`（§1.16） | 低 | Openai.kt + ErrorBubble 折叠区 |
| **P1** 中 | Interrupted 红色 chip | `event.interrupted`（§1.5） | 低 | 仅当启用 Live ASR 时 |
| **P1** 中 | 工具响应 JSON 展开 | `part.functionResponse.response`（§2.6） | 中 | ToolResponseChip 加折叠 |
| **P1** 中 | Invocation filter chip | `event.invocationId`（§1.3） | 中 | ChatScreen 顶栏 |
| **P2** 中-高 | EndOfAgent chip | `event.actions.endOfAgent`（§3.4） | 中 | 多 agent 协作 |
| **P2** 中-高 | inlineData 媒体渲染 | `part.inlineData`（§2.3） | 中-高 | ImageBubble/AudioBubble/VideoBubble |
| **P2** 中-高 | CodeExecution 工具 | `part.executableCode/codeExecutionResult`（§2.7-2.8） | 中-高 | CodeBlock + Result |
| **P3** 高 | Transfer / Route chip | `event.actions.transferToAgent/route`（§3.5-3.6） | 中 | 多 agent |
| **P3** 高 | StateDelta chip + session state | `event.actions.stateDelta`（§3.1） | 中 | 全局 state |
| **P3** 高 | Artifact chip + 异步加载 | `event.actions.artifactDelta` + `part.fileData`（§3.2, 2.4） | 高 | 复用 inlineData 渲染 |
| **P3** 高 | nodeInfo 缩进线 | `event.nodeInfo.path`（§4.1） | 中 | 多节点编排视觉化 |
| **P3** 高 | A2UI canvas | `part.a2ui` + `customMetadata.a2a:response`（§2.9, 1.14） | 高 | 见 adk-web a2ui-protocol.md |
| **P4** 高 | branch 多 Tab | `event.branch`（§1.12） | 高 | BranchAgent |
| **P4** 高 | output JSON 卡片 | `event.output`（§1.11） | 高 | OutputBubble + JSON viewer |
| **P4** 高 | messageAsOutput 渲染 | `event.nodeInfo.messageAsOutput`（§4.2） | 高 | 与 §1.11 配对 |
| **P4** 高 | AgentState 工作流图 | `event.actions.agentState.nodes`（§3.3） | 高 | 工作流图组件 |
| **P5** 高+ | trace 视图 | `nodeInfo.attr*`（§4.4） | 高+ | 整个 trace 视图 |
| **P5** 高+ | Live ASR + transcription 气泡 | `event.inputTranscription/outputTranscription`（§1.7-1.8） | 高+ | Gemini Live 集成 |

---

## 8. 复习自测清单 🆕

> 复刻 adk-web 自测清单的本地版。读完文档后用这份清单自测。

- [ ] 说出 `event.id` 在本项目里的 3 处用途（Message.id / Compose key / Reducer 关联键）；解释为什么本端不需要 adk-web 的 `eventData Map<id, Event>`。
- [ ] 解释 `event.partial` 的两个分支（`mergePartialEvent` 与 `appendCompleteEvent`）的触发条件，并说出"为什么不partial 事件也能走 streaming 路径"的 bug 隐患。
- [ ] 列出本端当前已消费的 Event 字段（≥10 项），然后列出 5 个最容易扩展的"P0+P1"未消费字段。
- [ ] 解释 `part.thought` 与 `event.partial` 的区别（part 级别 vs event 级别），并说明同一 Message 里 thought/answer 交替出现的切段逻辑。
- [ ] 说出 6 种触发"特殊渲染"的 Event 字段（error / turnComplete / interrupted / transferToAgent / endOfAgent / stateDelta），并各举一个"扩展点"。
- [ ] 列出 3 个 `actions.*` 子字段与对应 adk-web chip 的对应关系（stateDelta / artifactDelta / transferToAgent）。
- [ ] 解释 `event.finishReason` 从 `Openai.kt:mapToErrorResponse` 到 ErrorBubble 的当前数据流，并说明"为什么 finishReason = SAFETY 没有出现在气泡里"。
- [ ] 说出 m3-markdown SDK 在本项目里的角色（位置 + 用途），以及未来在哪里可以复用其 `Code` 组件。

---

## 9. 本项目关键文件清单

| 角色 | 文件 |
|---|---|
| Event 入口（流） | `app/src/main/java/github/ponyhuang/asssistantai/agent/AgentChatRunner.kt` |
| Event 入口（模型侧） | `app/src/main/java/github/ponyhuang/asssistantai/agent/Openai.kt` |
| Error LlmResponse 构造 | `app/src/main/java/github/ponyhuang/asssistantai/agent/Openai.kt:mapToErrorResponse` |
| Reducer（Event → Message） | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/ChatViewModel.kt` |
| 视图模型（Message 加工层） | `app/src/main/java/github/ponyhuang/asssistantai/model/Message.kt` |
| 视图（角色 + 颜色） | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/ChatBubble.kt` |
| 消息分发器（chip / bubble / thought） | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/MessageBubble.kt` |
| 思考段 | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/ThoughtBubble.kt` |
| 工具 chip | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/ToolCallChip.kt` |
| 错误气泡 | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/ErrorBubble.kt` |
| 输入框（乐观 UI 起点） | `app/src/main/java/github/ponyhuang/asssistantai/ui/chat/ChatInputBar.kt` |
| Markdown 渲染（外部） | `E:\workplace\multiplatform-markdown-renderer`（m3-markdown SDK） |

---

## 10. 相关笔记 🆕

> 本节列出与本文件相关、但**不在本文档展开**的引用资料与本地项目文件，方便一次找到所有上下文。

### 10.1 adk-web 等价文档（Angular 前端的同主题文档）

| 主题 | adk-web 记忆文件 | 内容简介 |
|---|---|---|
| Event 字段完整路由 | `C:\Users\huang\.claude\projects\E--workplace-adk-web\memory\event-field-routing.md` | 本文档的"原版"，字段语义对照源 |
| 聊天气泡类型 | `...\chat-bubble-types.md` | 所有气泡/卡片/chip 的字段对照 |
| WebSocket 数据流 | `...\chat-websocket-data-flow.md` | adk-web 从 WS 到 DOM 的完整链路（与本端 Flow<Event> 形态不同，但 Event 字段语义相同） |
| 流式 / thought 合并机制 | `...\chat-streaming-and-thought.md` | partial event 与 thought 文本累积机制 |
| thought 边界识别 | `...\thought-boundary-detection.md` | 多 chunk 同一 thought 的判定 |
| thought 不连续案例 | `...\thought-discontinuity-case.md` | 服务端中断后 thought 显示断开的排查 |
| A2UI 协议详解 | `...\a2ui-protocol.md` | A2UI v0.8 从后端 DataPart 到前端动态组件 |

### 10.2 本项目参考文件（CLAUDE.md 中的指引）

- **`CLAUDE.md`** — 项目根目录，列出构建命令、架构图、关键依赖、测试说明、SDK 源码位置
- **`doc/`** — 本文档所在目录，**目前仅本文件一份**
- **`CLAUDE.md → Local SDK Source Code`** — m3-markdown SDK 源码在 `E:\workplace\multiplatform-markdown-renderer`
- **`CLAUDE.md → Reference Docs`** — ADK Event 字段参考本文档

### 10.3 本文档与外部文档的差异速查

| 维度 | adk-web (Angular) | 本文档 (Android/Kotlin) |
|---|---|---|
| 数据模型 | `UiEvent`（高度加工） | `Message`（最小视图模型） |
| 渲染框架 | Angular Components + Material | Jetpack Compose + Material3 |
| 部分标签样式 | `<mat-tab-group>` / `<mat-chip>` | `TabRow` / `AssistChip` / `SuggestionChip` |
| Markdown 渲染 | 自实现 | m3-markdown SDK（多平台） |
| 流式 Markdown | 自实现 `StreamingMarkdown` | `rememberStreamingMarkdownState` |
| 缩进线 | CSS `border-left` + 动态宽度 | `Canvas.drawLine` 或 `Box` + `Spacer` |
| 工作流图 | 自实现 `workflow-graph-tooltip` | 当前无（**未来需引入 mermaid / 自绘**） |
| 协议字段 | a2a / a2ui 都处理 | 都未启用（路线图 P3-P4） |
| Transcript | Gemini Live 集成 | 路线图 P5 |

---

## 附录 A. 完整字段清单（v2 一览）

| 类别 | 字段 | 本端消费？ | adk-web 消费？ | 扩展路线图 |
|---|---|---|---|---|
| 顶层 | `id` | ✅ | ✅ | — |
| 顶层 | `author` | ✅ | ✅ | — |
| 顶层 | `invocationId` | ✅ | ✅ | P1 |
| 顶层 | `partial` | ✅ | ✅ | — |
| 顶层 | `interrupted` | ❌ | ✅ | P1 |
| 顶层 | `turnComplete` | ✅ | ✅ | P0 |
| 顶层 | `inputTranscription` | ❌ | ✅ | P5 |
| 顶层 | `outputTranscription` | ❌ | ✅ | P5 |
| 顶层 | `longRunningToolIds` | ❌ | ✅ | P3 |
| 顶层 | `errorCode` / `errorMessage` | ✅ | ✅ | P0（finishReason） |
| 顶层 | `output` | ❌ | ✅ | P4 |
| 顶层 | `branch` | ❌ | ✅ | P4 |
| 顶层 | `usageMetadata` | ❌（仅日志） | ❌（仅日志） | — |
| 顶层 | `customMetadata` | ❌ | ✅（A2A 判定） | P3 |
| 顶层 | `timestamp` | ❌ | ❌（仅排序） | — |
| 顶层 | `finishReason` | ❌ | ❌ | P0 |
| 顶层 | `requestedToolConfirmations` | ❌ | ❌ | P5 |
| parts | `text` | ✅ | ✅ | — |
| parts | `thought` | ✅ | ✅ | — |
| parts | `inlineData` | ❌ | ✅ | P2 |
| parts | `fileData` | ❌ | ✅ | P3 |
| parts | `functionCall` | ✅ | ✅ | — |
| parts | `functionResponse` | ✅ | ✅ | P1（JSON 展开） |
| parts | `executableCode` | ❌ | ✅ | P2 |
| parts | `codeExecutionResult` | ❌ | ✅ | P2 |
| parts | `a2ui` | ❌ | ✅ | P3 |
| actions | `stateDelta` | ❌ | ✅ | P3 |
| actions | `artifactDelta` | ❌ | ✅ | P3 |
| actions | `agentState.nodes` | ❌ | ✅ | P4 |
| actions | `endOfAgent` | ❌ | ✅ | P2 |
| actions | `route` | ❌ | ✅ | P3 |
| actions | `transferToAgent` | ❌ | ✅ | P3 |
| actions | `functionCall`/`functionResponse`/`finishReason`/`message` | ❌ | ❌ | — |
| nodeInfo | `path` | ❌ | ✅ | P3 |
| nodeInfo | `messageAsOutput` | ❌ | ✅ | P4 |
| nodeInfo | `outputFor` | ❌ | ✅ | P4 |
| nodeInfo | `attr*` | ❌ | ✅ | P5 |

---

## 附录 B. 文档版本与升级路径

**v1 → v2 升级摘要**：
1. ✅ 拆分 `action / nodeInfo / parts` 子字段，统一"读法 / 影响 / 扩展点"三栏格式
2. ✅ 补全漏掉的 Event 字段：`interrupted`、`finishReason`、`requestedToolConfirmations`、各 part 子类型（`inlineData`、`fileData`、`executableCode`、`codeExecutionResult`、`a2ui`）、action 各子字段、nodeInfo 各子字段
3. ✅ 新增 § 5 派生字段 / `Message` 加工层——把 `Message` 8 个字段逐一映射回 Event 来源
4. ✅ 新增 § 7 字段扩展优先级路线图——P0~P5
5. ✅ 新增 § 8 复习自测清单——8 题
6. ✅ 新增 § 10 相关笔记——链接 adk-web 7 份同主题记忆 + 本地 CLAUDE.md / m3-markdown SDK 位置
7. ✅ 新增 § 0.5 一句话字段路由总结——提到顶部，避免翻到末尾
8. ✅ 新增附录 A 完整字段一览表——按"Event / parts / actions / nodeInfo"四列快速查看消费/未消费状态

**v2 → v3 候选改动**：
- 增加 `Message` 模型 UML 图
- 增加 `ChatViewModel` reducer 流程的伪代码时序图
- 增加常见 bug 排查清单（已知问题 + 修复指引）
- 增加 adk-web 与本端 chip 渲染的截图对照（需 adk-web 截图）
