# Tool search (on-demand tool loading)

Reviewed: 2026-11-12. Audience: agents and developers changing `ON_DEMAND` tool access, `ToolSearchToolset`, tool candidate sources, or vendor tool-loading protocols. Chinese companion: [中文说明](../../zh/agent/tool-search.md). Agent assembly context: [Agent business execution](agent-execution.md).

## Concept of record

The protocol reference is [Claude's Tool Search Tool](https://platform.claude.com/docs/en/agents-and-tools/tool-use/tool-search-tool). Its principles, which Gimi follows conceptually:

- **Deferred loading** — tool definitions are not preloaded into the context window; the model searches a catalog and only matching tools are loaded. This cuts context bloat and keeps tool-selection accuracy stable beyond ~30–50 tools.
- **A resident search tool** — one tool (the search tool itself) is always declared; the model calls it when the declared tools cannot satisfy the request. Never defer the search tool.
- **References expand into declarations** — matches come back as references that materialize into full tool declarations for the next model step; once discovered, they stay available in later turns without re-searching.
- **Loading is a context decision, not a request decision** — the full catalog still participates in search; only what the model actually uses enters the context.
- **Group loading** — a toolset can load as a complete unit (with usage instructions) so auth/query/execute flows are not split across a result-size cap.
- **Post-search filtering** — user toggles and authorizations are applied *after* ranking, so disabled tools never displace enabled ones.

Gimi differences from Anthropic's server-side implementation: search runs **client-side** (local embeddings over the full catalog, not regex/BM25 on the provider), and discovery state must survive **process death** because ADK sessions are persisted in Room.

## Where it lives

All in `data/agent/src/main/java/github/ponyhuang/gimi/data/agent/tools/search/`:

| Piece | Role |
|---|---|
| `ToolSearchToolset` | The ADK `Toolset` mounted in `ON_DEMAND` mode; owns the `tool_search` tool, discovery, ranking, budget, and selection persistence |
| `ToolCandidateSource` / `ToolCandidateSources.kt` | SPI for retrieval candidates: `LocalToolSource` (flat local catalog), `McpServerSource` (one per MCP server, for failure isolation), `OfficialToolCandidateSource` (official per-tool specs), `ToolsetCandidateSource` (generic adapter, optional `loadAsGroup`) |
| `ToolVectorSearch` / `ObjectBoxToolVectorSearch` | Full-catalog sync + HNSW nearest-neighbor query (ObjectBox, cosine distance) |
| `MediaPipeToolEmbeddingModel` | Local Universal Sentence Encoder (float32 tflite, 100 dims) shared by documents and queries |
| `ToolVectorEntity` | ObjectBox entity: scope key, document key, content hash, searchable text, embedding |
| `ToolLoadingMode.kt` | `RESULT` vs `DEEPSEEK_REFERENCE` vendor loading mode |
| `InlineToolSearchEvents` | Persists selected declarations as inline system events (REFERENCE mode) |
| `AnthropicToolLoadingRequestConverter` | Translates persisted inline events into Anthropic `tool_addition`/`tool_reference` blocks |

## Execution flow

1. **Assembly** — `AgentFactory` builds the agent with `toolAccessMode = ON_DEMAND`; contributions' resident `toolsets` become `candidateSources`, and a single `ToolSearchToolset` is appended. `getTools()` returns just the search tool until a selection exists.
2. **Instruction** — `processLlmRequest` appends a `<tool_search>` guidance block (call `tool_search` when declared tools can't provide an action or device info; never guess undeclared tools; English keywords when descriptions are English).
3. **Search** — the model calls `tool_search(query)`:
   - Discover the **full catalog** (`loadAllTools`, toggles ignored) under a `discoveryMutex`; per-source failures become sanitized `source_errors` and are not cached; `CancellationException` always rethrows; single tools whose declaration fails to serialize are skipped, not fatal.
   - Sync the catalog into the vector store (content-hash SHA-256 skips unchanged re-embedding; removed tools are deleted), then nearest-neighbor search. **Filtering to `loadEnabledTools` happens after ranking** so disabled tools can't crowd out enabled ones.
   - Deduplicate by tool name across sources: duplicates become `ambiguous_tools` and are never exposed.
   - Select within `ToolAccessBudget` (soft caps: 8 tools, 16 KiB schema JSON). `loadAsGroup` sources load their whole enabled group; the **first hit may exceed the budget** rather than splitting an auth/query/execute flow.
4. **Persistence** — selected tool names are written to session state (`gimi.tool_search.loaded_tools`). `latestSelection` prefers this persisted state (survives process death, drives confirmation resume) and falls back to scanning the current invocation's `tool_search` responses during the state-delta merge window.
5. **Next model step** — `getTools()` now returns `searchTool + selected tools`; later requests keep carrying them so the request prefix stays stable (prefix-cache friendly) and the model never re-searches each turn. The result payload reports `loaded_tools`, `omitted_match_count`, `ambiguous_tools`, `source_errors`, and `selection_changed`; empty matches are a normal `selection_changed=false` result, not an error.

## Vendor loading modes

`toolLoadingMode(runtime)` decides how selections reach the vendor:

- **`RESULT`** (everything except adapted DeepSeek) — the search result is an ordinary function response; selected tools are declared in the request's top-level `tools`. No vendor protocol involvement.
- **`DEEPSEEK_REFERENCE`** (DeepSeek `deepseek-flash` on the official `api.deepseek.com/anthropic` endpoint via the Anthropic protocol) — emulates Anthropic's server-side tool search:
  - `processLlmRequest` injects the enabled catalog's declarations as system-instruction part metadata (`gimi.deferred_tool_catalog`) instead of ordinary tool configs.
  - `InlineToolSearchEvents.appendPending` records selected declarations as a persisted system event (pending snapshot rides with the result event; clear-and-append is atomic to avoid duplicate inserts on retry).
  - `AnthropicToolLoadingRequestConverter` rebuilds the request: inline system events become `tool_addition` blocks carrying `tool_reference` names; catalog declarations are sent in `tools` with `defer_loading=true`; empty `tool_search` tool results are blanked. DeepSeek supports only `tool_addition` (no removal), so when a historical loaded tool is no longer active the converter falls back to plain declarations for that request and strips historical markers — persisted history is never rewritten.

## Invariants

1. **Full catalog drives search; enabled catalog drives exposure.** `loadAllTools` must ignore toggles; only post-ranking filtering may apply session config. Never cache failures as empty catalogs (a disguised empty success would poison the index).
2. **Search tool is never deferred/removed.** `getTools()` always includes it.
3. **Selection survives process death.** Session state is the source of truth; invocation-event scanning is only a merge-window fallback. Nothing may rely on in-process fields alone.
4. **Vector store is a rebuildable cache.** Scope key = source ids + service/model + embedding-model version; never migrated, safe to delete. Documents and queries must use the same embedding model version or distances are meaningless.
5. **Request prefix stability** — once selected, tools stay declared across turns; adding/removing a selection is the only sanctioned churn.
6. **`tool_search` is read-only** and participates in `AgentToolRetryPolicy`; execution tools never enter the retry budget.
7. **Vendor quirks stay in the converter.** The generic Claude request converter must not depend on search internals; protocol translation lives in `AnthropicToolLoadingRequestConverter`.

## When-changing checklist

- New candidate source → implement `ToolCandidateSource`, expose it from the owning `AgentContribution.candidateSources(spec)`; decide `loadAsGroup` deliberately (group = whole toolset loads together, immune to the per-tool budget).
- Budget/selection changes → `ToolSearchToolsetTest` covers ranking, grouping, budget exceptions, ambiguity, and persistence.
- Loading-mode or protocol changes → `InlineToolSearchPersistenceTest`, `ClaudeRequestPayloadTest`, `ToolSearchToolsetTest`; verify both modes keep the prefix stable and REFERENCE mode never mutates persisted history.
- Embedding model change → bump `ToolEmbeddingModel.version` (it is part of the scope key) and let the store rebuild; do not reuse old vectors.
