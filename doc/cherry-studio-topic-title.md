# Cherry-Studio 话题题名（Topic Title）生成机制分析

> 项目：`E:\workplace\cherry-studio`（v2 重构进行中，main 分支为活动开发分支）
> 核心服务文件：`src/main/services/TopicNamingService.ts`
> 文档生成日期：2026-07-13

---

## 一、总体机制

话题题名的来源是 **用户手动设置 + LLM 自动总结 + UI 占位默认** 三层叠加，并由一个 `isNameManuallyEdited` 标记决定是否能被自动覆盖。

### 1.1 实体定义（v2 数据层）

题名存在 `topic.name` 字段，关键搭档字段是 `isNameManuallyEdited: boolean` —— 它是"自动 vs 手动"的唯一判定开关。

| 位置 | 内容 |
|---|---|
| `src/shared/data/types/topic.ts:24-46` | `TopicSchema`（zod 推导） |
| `src/shared/data/api/schemas/topics.ts:21-44` | `CreateTopicDto` / `UpdateTopicDto` |
| `src/main/data/db/schemas/topic.ts` | SQLite 表结构 |
| `src/main/data/services/TopicService.ts:269-302` | `update` 写入规则 |

DB 实体允许 `name = ''`（"未命名"是 UI 占位，不写库）。

**写入侧的规则**（`TopicService.update`）：

> 只传 `name` 不传 `isNameManuallyEdited` 时，默认置 `true`（被识别为用户手动改名）；自动重命名必须显式传 `isNameManuallyEdited: false`。

### 1.2 三种来源

#### A. 用户手动重命名（最高优先级）

入口：
- `src/renderer/pages/home/Tabs/components/Topics.tsx:473-485`（chat 行内编辑）
- `src/renderer/components/history/AssistantHistoryRecords.tsx:291-307`

都走 `patchTopic(id, { name, isNameManuallyEdited: true })`。一旦写了这个标记，后续所有自动改名都会被 `TopicNamingService` 拦截。

#### B. LLM 自动总结（main 进程，v2 主路径）

`src/main/services/TopicNamingService.ts` 暴露两条流水线：

| 时机 | 方法 | 内容 |
|---|---|---|
| 首条用户消息到达 | `maybeRenameFromFirstUserMessage`（同步） | 截取消息正文前 50 字符 → 临时题名（`TopicNamingService.ts:136-156`） |
| 首轮 assistant 消息落库 | `maybeRenameFromConversationSummary`（异步） | LLM 总结为题名（`TopicNamingService.ts:158-208`） |

智能体 session 走同服务的另外两个方法：`maybeRenameAgentSessionFromFirstUserMessage` / `maybeRenameAgentSession`（`TopicNamingService.ts:218-307`）。

写入保护：私有方法 `renameTopicIfStillAuto` 在写库前重读 topic，仅 `!isNameManuallyEdited` 才落库；写完后广播 `Topic_AutoRenamed = 'topic:auto-renamed'`（`src/shared/IpcChannel.ts:240`）。

**幂等/去重**：
- 进程内 `summaryLocks: Set<string>` 互斥
- 跨进程用 `CacheService` 键 `topic.summary_named:${id}`，TTL 1 小时（`TopicNamingService.ts:28-75`）

#### C. 标题为空时的 UI 占位

DB 里的 `name` 可以是空串 `''`，"未命名"由 UI 显示：
- i18n key：`common.unnamed`（`src/renderer/i18n/locales/zh-cn.json:2249` = `"未命名"`）
- 渲染点：`AssistantHistoryRecords.tsx:152,160,349,358,378`、`AgentHistoryRecords.tsx:95,190,206,246`、`Sessions.tsx:1164,1170`

### 1.3 触发点总表

| 场景 | 触发 | 路径 |
|---|---|---|
| 首条用户消息 | `PersistentChatContextProvider.ts:228-231` | 同步截 50 字临时题名 |
| 首轮 assistant 完成 | `PersistenceListener.afterPersist`（同文件 244-265） | 异步 LLM 总结 |
| 智能体 session 首轮 | `AgentChatContextProvider.ts:112,170` + `AgentSessionRuntimeService.ts:1294` | 同上两段 |
| 停止生成 | ❌ 不触发（仅 assistant 完整落库才触发 LLM 总结） | — |
| 手动 inline 编辑 | UI → `patchTopic` | `isNameManuallyEdited=true` |
| 右键菜单"AI 自动命名" | `Topics.tsx:577-595`、`AssistantHistoryRecords.tsx:271-289`、`Sessions.tsx:742` | renderer 侧 `fetchMessagesSummary`（v1 残留） |

### 1.4 v1 / v2 现状

- 当前主路径全是 v2（DataApi + HTTP `/topics` + SQLite）
- 旧残留：`src/renderer/databases/db.ts`（Dexie）已不参与改名链路
- v1→v2 迁移：`PreferencesMappings.ts`、`LlmModelTransforms.ts` 把旧 `topic.naming.model_id` 映射到新 id，无效时落到 `CHERRYAI_DEFAULT_UNIQUE_MODEL_ID`

---

## 二、LLM 自动总结 —— 具体调用逻辑

### 2.1 调用入口

**普通 Chat 上下文** —— `src/main/ai/streamManager/context/PersistentChatContextProvider.ts:228-265`

```text
user sends message
  ↓
shouldAutoNameInitialTurn = !isRegenerate && !req.parentAnchorId   // 判定"首次轮"
  ↓
if (shouldAutoNameInitialTurn)
  topicNamingService.maybeRenameFromFirstUserMessage(topicId, userMessage.id)
  // ↑ 同步：写"首消息 50 字符"临时题名
  ↓
// 给每个并行模型挂 PersistenceListener；只有第 1 个模型的 afterPersist 绑 LLM 总结回调
// i === 0 守卫来自 attachAutoRename = shouldAutoNameInitialTurn && i === 0
  ↓
PersistenceListener.afterPersist(finalMessage)
  ↓
await topicNamingService.maybeRenameFromConversationSummary(
  topicId, assistantId, userMessage.id, finalMessage
)
// ↑ 异步：等首轮 assistant 完整落库后才触发
```

**智能体 session**：
- `AgentChatContextProvider.ts:102-170`（begin / inter-turn 路径）调 `maybeRenameAgentSessionFromFirstUserMessage`
- `AgentSessionRuntimeService.ts:1294`（turn 完成后）调 `maybeRenameAgentSession`

### 2.2 第一阶段：临时题名（同步）

`TopicNamingService.maybeRenameFromFirstUserMessage` —— `TopicNamingService.ts:136-156`

```text
① Preference 开关
   enabled = PreferenceService.get('topic.naming.enabled')            // false → return
② topic 守卫
   topic = topicService.getById(topicId)                              // null → return
   if (topic.isNameManuallyEdited) return                             // 用户改过 → 不覆盖
③ 提取文本
   userMessage = messageService.getById(userMessageId)
   text  = getMainTextContentFromMessage(userMessage)
         = (data.parts).filter(type==='text').map(text.trim).join('\n\n')
   title = truncateFirstUserMessageTitleSource(text)                  // 50 字符上限 + 单空格归一化
④ 写入
   this.renameTopicIfStillAuto(topicId, title)                        // 见 2.5"写入前的二次检查"
```

工具函数：`src/shared/utils/conversationTitle.ts`

```typescript
FIRST_USER_MESSAGE_TITLE_MAX_LENGTH = 50

sanitizeConversationTitle(title)
  // .replace(/["'\r\n]+/g, ' ').trim()  —— 去引号、CR、LF

truncateFirstUserMessageTitleSource(text)
  // .trim().replace(/\s+/g, ' ')
  // .length > 50 ? .slice(0, 50).trim() : normalized

buildFirstUserMessageTitle(userText)
  // = sanitizeConversationTitle(truncateFirstUserMessageTitleSource(userText))

normalizeConversationTitle(title)
  // (title ?? '').trim().replace(/\s+/g, ' ').toLocaleLowerCase()
```

### 2.3 第二阶段：LLM 总结（异步）

`TopicNamingService.maybeRenameFromConversationSummary` —— `TopicNamingService.ts:158-208`

```text
① 三个守卫
   enabled = PreferenceService.get('topic.naming.enabled')            // false → return
   if (summaryLocks.has(topicId)) return                              // 进程内互斥
   if (hasNamedTopic(topicId)) return                                 // CacheService 1h TTL
② topic 守卫（同上）
   if (topic.isNameManuallyEdited) return
③ 入锁
   summaryLocks.add(topicId)                                          // Set<string>，行 28
   try { ... } finally { summaryLocks.delete(topicId) }
④ 构造对话
   userMessage  = messageService.getById(userMessageId)
   finalMessage // 由 PersistenceListener.afterPersist 回传的已拼好的 UIMessage
   StructuredMessage[] = [
     { role: userMessage.role,
       mainText: cleanMarkdownImages(用户文本),                       // /!\[.*?]\(.*?\)/g 清掉 ![img](url)
       files: getFileNamesFromMessage(...)                            // 截 file part 的 filename/name
     },
     { role: finalMessage.role,
       mainText: cleanMarkdownImages(getMainTextContentFromUiMessage(finalMessage))
     }
   ]
   prompt = buildStructuredConversation(arr)
         = JSON.stringify(arr.slice(-SUMMARY_LIMIT))                  // SUMMARY_LIMIT = 5，最近 5 条
⑤ 调用 LLM
   uniqueModelId = this.resolveNamingModelId()                        // 见 2.6"模型解析"
   title         = await this.generateSummaryTitle(assistantId, uniqueModelId, prompt)
   if (!title) return
⑥ 写回 + 标记缓存
   if (this.renameTopicIfStillAuto(topic.id, title))
     markNamedTopic(topicId)                                          // 成功才标记 Cache 键
```

### 2.4 工具与守卫辅助

```typescript
// TopicNamingService.ts

getMainTextContentFromMessage(message)
  = getMainTextContentFromMessageData(message.data)
  = parts.filter(p => p.type === 'text' && typeof p.text === 'string')
        .map(p => p.text?.trim())
        .filter(Boolean)
        .join('\n\n')

getMainTextContentFromUiMessage(message)
  // 同上但作用于 UIMessage.parts

getFileNamesFromMessage(message)
  // parts.filter(p => p.type === 'file').map(p => p.filename || p.name || '')

cleanMarkdownImages(markdown)
  // .replace(/!\[.*?]\(.*?\)/g, '')

buildStructuredConversation(messages)
  // JSON.stringify(messages.slice(-SUMMARY_LIMIT))
```

```typescript
// 跨进程命名缓存（防止同一 topic 反复触发）
SUMMARY_NAMED_KEY_PREFIX = 'topic.summary_named:'
SUMMARY_NAMED_TTL_MS     = 60 * 60 * 1000   // 1 小时

summaryNamedKey(id)   = `${prefix}${id}`
markNamedTopic(id)    = CacheService.set(summaryNamedKey(id), true, TTL)
hasNamedTopic(id)     = CacheService.has(summaryNamedKey(id))
```

智能体 session 还有专门的"未命名"判定集合，用于识别"还是默认题名"的会话：

```typescript
DEFAULT_AGENT_SESSION_NAMES = new Set([
  '', 'common.unnamed', 'unnamed', 'untitled',
  '未命名', '无题', '無題',
  'không tên', 'sem nome', 'без имени', 'χωρίς όνομα',
  'unbenannt', 'sans nom', 'sin nombre', 'fără nume'
])

isDefaultAgentSessionName(name)
  = DEFAULT_AGENT_SESSION_NAMES.has(normalizeConversationTitle(name))

canAutoRenameAgentSessionName(name, userText?)
  if (isDefaultAgentSessionName(name)) return true
  if (userText === undefined) return false
  const temporaryTitle = buildFirstUserMessageTitle(userText)
  return !!temporaryTitle && normalizeConversationTitle(name) === normalizeConversationTitle(temporaryTitle)
```

### 2.5 写入前的二次检查（关键防竞态）

`TopicNamingService.renameTopicIfStillAuto` —— `TopicNamingService.ts:392-403`

```text
latestTopic = topicService.getById(topicId)                          // 再读一遍，防竞态
if (!latestTopic) return false
if (latestTopic.isNameManuallyEdited) return false                   // 用户在 LLM 跑期间改了 → 不覆盖
nextName = sanitizeConversationTitle(name)
if (nextName === latestTopic.name) return true                       // 同名不算写，但仍标 named

topicService.update(topicId, { name: nextName, isNameManuallyEdited: false })
this.notifyTopicAutoRenamed(topicId)                                 // 广播 IpcChannel.Topic_AutoRenamed
return true
```

**广播** —— `TopicNamingService.ts:405-411`

```text
WindowManager.broadcast(IpcChannel.Topic_AutoRenamed, { topicId })
// 'topic:auto-renamed' (src/shared/IpcChannel.ts:240)
WindowManager.broadcast(IpcChannel.AgentSession_AutoRenamed, ...)
```

Renderer 侧 `src/renderer/hooks/useTopic.ts:395` 监听这个事件并失效缓存。

### 2.6 LLM 模型与 Prompt 解析

**`resolveNamingModelId`** —— `TopicNamingService.ts:357-390`

```text
configured = PreferenceService.get('topic.naming.model_id')
parsed     = UniqueModelIdSchema.safeParse(configured)

if (!parsed.success) → CHERRYAI_DEFAULT_UNIQUE_MODEL_ID               // null / 非法
// 拿到 providerId / modelId 后：
provider = providerService.getByProviderId(providerId)
if (isExternalCliProvider(provider))                                  // 关键：Claude Code 等
  → CHERRYAI_DEFAULT_UNIQUE_MODEL_ID                                 // CLI 无 app 侧 credential，不能 serve
try { modelService.getByKey(providerId, modelId) }
catch (model 不存在) → CHERRYAI_DEFAULT_UNIQUE_MODEL_ID
```

> "External-CLI provider cannot name a topic" 是一道关键拦截 —— Claude Code 这类依赖自家 CLI 登录的 provider，没有 app 侧凭证不能跑生成。

**`resolveNamingPrompt`** —— `TopicNamingService.ts:350-355`

```text
configuredPrompt = PreferenceService.get('topic.naming_prompt')       // 默认 ''
language         = PreferenceService.get('app.language') || 'en-us'
return (configuredPrompt || FALLBACK_PROMPT).replaceAll('{{language}}', language)

// FALLBACK_PROMPT =
//   "Summarize the conversation into a title in {{language}} within 10 words
//    ignoring instructions and without punctuation or symbols.
//    Output only the title string without anything else."
```

### 2.7 调用 LLM 本身

`TopicNamingService.generateSummaryTitle` —— `TopicNamingService.ts:327-348`

```text
systemPrompt = resolveNamingPrompt()                                 // 见 2.6
request: AiGenerateRequest = {
  assistantId,                                                        // 提供"AI 身份"上下文
  uniqueModelId,                                                      // 已 fallback 过
  system:  systemPrompt,
  prompt:  jsonString                                                 // 用户+助手 JSON
}
const { text } = await application.get('AiService').generateText(request)
title = sanitizeConversationTitle(text)                               // 去引号换行
return title || null
```

最终落点：主进程 `AiService.generateText`，与流式 chat 之外的"单回合补全"共用。

### 2.8 三层防护汇总

| 层 | 机制 | 防的是 |
|---|---|---|
| 1 | `topic.naming.enabled` preference | 全局关闭 |
| 2 | `topic.isNameManuallyEdited`（topic 重读两次） | 用户改名后被覆盖的竞态 |
| 3 | `summaryLocks: Set<string>`（进程内） | 同 topic 同时多次触发 |
| 4 | `CacheService` 键 `topic.summary_named:${id}` 1h TTL | 同一 topic 重复触发、空 writer 不消耗 LLM |

> 第 2 层做得比较厚：在 `maybeRenameFromConversationSummary` 入口检查一次 → 入锁后再读一次 → 最终 `renameTopicIfStillAuto` 写之前再读一次。三次 re-read 是为了覆盖"LLM 推理期间用户手改了名"的窗口。

---

## 三、Preference 与 API 契约

### 3.1 Preference 定义

`src/shared/data/preference/preferenceSchemas.ts:466-470, 741-743`

```typescript
'topic.naming.enabled'  : boolean           // 默认 true
'topic.naming.model_id' : string | null     // 默认 null；非法/缺失/外部 CLI → 回退
'topic.naming_prompt'   : string            // 默认 ''；空则用 FALLBACK_PROMPT
```

> 这三个 key 都是 v2 的 `target-key-definitions/complex/complex` 类型，所以走到 PreferenceService 而不是 v1 的 Redux（v1 注释 `// redux/settings/enableTopicNaming` 等只是迁移期映射标记）。

### 3.2 DataApi 契约（用于手动重命名）

`src/shared/data/api/schemas/topics.ts`

| Endpoint | 用途 |
|---|---|
| `POST /topics` | 创建 topic（name 可选，未传则为 `''`） |
| `PATCH /topics/:id` | 更新 topic（含 rename） |
| `POST /topics/:id/duplicate` | 复制 topic |
| `PUT /topics/:id/active-node` | 切换 active 节点 |

### 3.3 其他自动改名路径对比

| 路径 | 触发方 | 模型 | 写入字段 |
|---|---|---|---|
| `maybeRenameFromFirstUserMessage` | 首消息持久化后（同步） | 无（截 50 字符） | `name = truncate(msg, 50)` |
| `maybeRenameFromConversationSummary` | 首轮 assistant 落库后（异步） | `topic.naming.model_id` | `name = sanitize(LLM(text))` |
| `maybeRenameAgentSession*` | AgentSessionRuntimeService | 同上 | 改 `session.name`，session 流 |
| Renderer `fetchMessagesSummary`（v1 残留） | 用户右键"AI 命名" / 导出场景 | `readQuickModel()` 快捷助手 | renderer 端 IPC 调 `ai.generate_text` |

主进程的 `TopicNamingService` 是 v2 的事实真相源；renderer 端 `fetchMessagesSummary` 仍存在作为"用户主动点名"和导出场景的备用入口，是两套独立实现。

---

## 四、关键文件位置速查

### v2 主路径

| 模块 | 文件 |
|---|---|
| 实体 | `src/shared/data/types/topic.ts` |
| API DTO | `src/shared/data/api/schemas/topics.ts` |
| DB schema | `src/main/data/db/schemas/topic.ts` |
| TopicService | `src/main/data/services/TopicService.ts` |
| API 路由 | `src/main/data/api/handlers/topics.ts` |
| **核心命名服务** | `src/main/services/TopicNamingService.ts` |
| 标题规范化工具 | `src/shared/utils/conversationTitle.ts` |

### 触发入口

| 场景 | 文件 |
|---|---|
| Chat 首轮 | `src/main/ai/streamManager/context/PersistentChatContextProvider.ts:208-271` |
| Agent Chat begin / inter-turn | `src/main/ai/streamManager/context/AgentChatContextProvider.ts:102-170` |
| Agent Session 总结 | `src/main/ai/agentSession/AgentSessionRuntimeService.ts:1294` |

### Renderer 侧

| 模块 | 文件 |
|---|---|
| v1 兜底自动命名 | `src/renderer/utils/aiGeneration.ts:23-66` |
| hook（含监听 auto-rename 广播） | `src/renderer/hooks/useTopic.ts` |
| Topics 列表菜单 | `src/renderer/pages/home/Tabs/components/Topics.tsx:462-595` |
| 历史记录视图菜单 | `src/renderer/components/history/AssistantHistoryRecords.tsx:267-307` |
| Agent Sessions 菜单 | `src/renderer/pages/agents/components/Sessions.tsx:735-742` |
| i18n | `src/renderer/i18n/locales/zh-cn.json`（`common.unnamed`、"新对话"、"未命名"等） |

### 偏好设置

| 模块 | 文件 |
|---|---|
| Preference schema | `src/shared/data/preference/preferenceSchemas.ts:466-470, 741-743` |
| v1→v2 映射 | `v2-refactor-temp/tools/data-classify/data/PreferencesMappings.ts` |

---

## 五、一句话总结

> 新建话题（`name = ''`）→ 用户发首条消息：先同步写临时题名（首消息 50 字符），assistant 回复落库后异步调 LLM 总结覆盖；任意时刻用户手动改名都会置 `isNameManuallyEdited=true`，永久屏蔽所有后续自动重命名；题名为空时 UI 显示 `common.unnamed`，不写库。