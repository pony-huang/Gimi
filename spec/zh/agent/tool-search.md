# 工具搜索（按需工具加载）

校对日期：2026-11-12。读者：修改 `ON_DEMAND` 工具访问、`ToolSearchToolset`、工具候选源或厂商工具加载协议的 agent 与开发者。英文版：[English](../../en/agent/tool-search.md)。agent 装配背景见 [Agent 业务执行](agent-execution.md)。

## 概念基准

协议参考是 [Claude 的 Tool Search Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-search-tool)，Gimi 在概念上遵循其原理：

- **延迟加载**——工具定义不再预先全部进入上下文窗口；模型先检索工具目录，只加载命中的工具。这能显著压缩上下文占用，并在工具数超过约 30–50 个后保持工具选择的准确性。
- **常驻搜索工具**——始终声明一个搜索工具本身；当已声明工具无法满足请求时，模型调用它搜索更多工具。搜索工具自身绝不能被延迟。
- **引用展开为声明**——搜索结果以引用形式返回，并在下一个模型步骤物化为完整工具声明；一经发现，后续轮次继续可用，无需重复搜索。
- **加载是上下文决策，不是请求决策**——完整目录始终参与检索；只有模型真正用到的定义才进入上下文。
- **整组加载**——一个工具集可以作为完整单元（连同使用说明）一起加载，避免认证/查询/执行流程被结果数量上限拆散。
- **检索后过滤**——用户开关与授权在排序*之后*应用，保证关闭的工具不会挤掉启用中的工具。

Gimi 与 Anthropic 服务端实现的差异：检索在**客户端**运行（对全量目录做本地向量检索，而非厂商侧 regex/BM25）；且发现状态必须能**跨进程死亡存活**，因为 ADK 会话持久化在 Room 里。

## 代码位置

全部位于 `data/agent/src/main/java/github/ponyhuang/gimi/data/agent/tools/search/`：

| 组件 | 职责 |
|---|---|
| `ToolSearchToolset` | `ON_DEMAND` 模式挂载的 ADK `Toolset`；拥有 `tool_search` 工具、候选发现、排序、预算与选择持久化 |
| `ToolCandidateSource` / `ToolCandidateSources.kt` | 检索候选源 SPI：`LocalToolSource`（本地工具扁平目录）、`McpServerSource`（每个 MCP server 一个，失败隔离）、`OfficialToolCandidateSource`（官方工具逐个 spec）、`ToolsetCandidateSource`（通用适配器，可选 `loadAsGroup`） |
| `ToolVectorSearch` / `ObjectBoxToolVectorSearch` | 全量目录同步 + HNSW 最近邻查询（ObjectBox，余弦距离） |
| `MediaPipeToolEmbeddingModel` | 本地 Universal Sentence Encoder（float32 tflite，100 维），工具文档与查询共用 |
| `ToolVectorEntity` | ObjectBox 实体：scope key、文档 key、内容哈希、可检索文本、向量 |
| `ToolLoadingMode.kt` | `RESULT` 与 `DEEPSEEK_REFERENCE` 两种厂商加载模式 |
| `InlineToolSearchEvents` | （REFERENCE 模式）把选中声明持久化为内联 system 事件 |
| `AnthropicToolLoadingRequestConverter` | 把持久化的内联事件翻译为 Anthropic `tool_addition`/`tool_reference` 块 |

## 执行流程

1. **装配**——`AgentFactory` 以 `toolAccessMode = ON_DEMAND` 构建 agent；各 contribution 的常驻 `toolsets` 转为 `candidateSources`，并追加唯一一个 `ToolSearchToolset`。没有选择记录时，`getTools()` 只返回搜索工具。
2. **指令注入**——`processLlmRequest` 追加 `<tool_search>` 引导块（已声明工具无法提供某个操作或设备信息时先调用 `tool_search`；绝不猜测未声明的工具调用；目录描述为英文时用简洁英文关键词检索）。
3. **检索**——模型调用 `tool_search(query)`：
   - 在 `discoveryMutex` 保护下发现**全量目录**（`loadAllTools`，忽略开关）；单来源失败转为脱敏的 `source_errors` 且不写缓存；`CancellationException` 一律重抛；单个工具声明序列化失败只跳过该候选，不阻断检索。
   - 把目录同步进向量库（SHA-256 内容哈希跳过未变化的重新嵌入；已移除工具删除向量），再做最近邻查询。**过滤到 `loadEnabledTools` 发生在排序之后**，关闭的工具不会挤掉启用中的工具。
   - 跨来源按工具名去重：重名进入 `ambiguous_tools`，绝不暴露。
   - 在 `ToolAccessBudget`（软上限：8 个工具、16 KiB schema JSON）内选择。`loadAsGroup` 来源整组加载启用的成员；**首个命中的组允许超出软预算**，避免拆散认证/查询/执行流程。
4. **持久化**——选中的工具名写入 session state（`gimi.tool_search.loaded_tools`）。`latestSelection` 优先读这份持久化状态（跨进程死亡存活，确认恢复也依赖它），仅在 state delta 尚未合并进 session 的窗口期回退扫描当前 invocation 的 `tool_search` 响应。
5. **下一个模型步骤**——`getTools()` 改为返回 `搜索工具 + 选中工具`；后续请求继续携带这些声明，保持请求前缀稳定（前缀缓存友好），模型无需每轮重新检索。结果载荷包含 `loaded_tools`、`omitted_match_count`、`ambiguous_tools`、`source_errors` 与 `selection_changed`；空匹配是正常的 `selection_changed=false` 结果，不是错误。

## 厂商加载模式

`toolLoadingMode(runtime)` 决定选中工具如何送达厂商：

- **`RESULT`**（除适配版 DeepSeek 外的全部端点）——搜索结果是普通函数响应；选中工具在请求顶层 `tools` 中声明。不涉及任何厂商协议。
- **`DEEPSEEK_REFERENCE`**（DeepSeek `deepseek-flash` 经 Anthropic 协议访问官方 `api.deepseek.com/anthropic` 端点）——模拟 Anthropic 的服务端工具搜索：
  - `processLlmRequest` 把启用目录的声明以 system instruction part 元数据（`gimi.deferred_tool_catalog`）注入，而不是普通 tool config。
  - `InlineToolSearchEvents.appendPending` 把选中声明记录为持久化的 system 事件（待提交快照随结果事件落库；清除与新增在同一次事件写入完成，避免重试重复插入）。
  - `AnthropicToolLoadingRequestConverter` 重建请求：内联 system 事件转成携带 `tool_reference` 名称的 `tool_addition` 块；目录声明以 `defer_loading=true` 放入 `tools`；空的 `tool_search` 工具结果内容被置空。DeepSeek 只支持 `tool_addition`（不支持移除），因此当历史已加载工具不再生效时，转换器对该次请求回退为普通声明并去掉历史加载标记——持久化历史绝不被改写。

## 不变量

1. **全量目录驱动检索，启用目录驱动暴露。** `loadAllTools` 必须忽略开关；会话配置只允许在排序后过滤。失败绝不能伪装成空目录写入缓存（伪装的空成功会污染索引）。
2. **搜索工具永不被延迟或移除。** `getTools()` 始终包含它。
3. **选择必须跨进程死亡存活。** session state 是真源；invocation 事件扫描只是合并窗口期的回退。任何逻辑不得只依赖进程内字段。
4. **向量库是可重建缓存。** scope key = 来源 id + service/model + 嵌入模型版本；永不迁移、可安全删除。文档与查询必须使用同一嵌入模型版本，否则向量距离没有意义。
5. **请求前缀稳定**——一旦选中，工具跨轮持续声明；只有选择本身变化才允许声明变动。
6. **`tool_search` 是只读工具**，参与 `AgentToolRetryPolicy` 重试预算；执行类工具不进重试预算。
7. **厂商怪癖留在转换器里。** 通用 Claude 请求转换器不得依赖搜索内部实现；协议翻译只存在于 `AnthropicToolLoadingRequestConverter`。

## 改动清单

- 新增候选源 → 实现 `ToolCandidateSource`，由所属 `AgentContribution.candidateSources(spec)` 暴露；慎重决定 `loadAsGroup`（组 = 整个工具集一起加载，不受单工具预算截断）。
- 预算/选择改动 → `ToolSearchToolsetTest` 覆盖排序、分组、预算例外、重名与持久化。
- 加载模式/协议改动 → `InlineToolSearchPersistenceTest`、`ClaudeRequestPayloadTest`、`ToolSearchToolsetTest`；验证两种模式都保持前缀稳定，且 REFERENCE 模式绝不改写持久化历史。
- 嵌入模型更换 → 必须提升 `ToolEmbeddingModel.version`（它是 scope key 的一部分）并让向量库重建；不得复用旧向量。
