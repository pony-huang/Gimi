# Agent business execution

Reviewed: 2026-11-07. Audience: agents and developers changing chat/agent execution, tool wiring, run lifecycle, or confirmation behavior. Chinese companion: [中文说明](../../zh/agent/agent-execution.md).

Read this before adding an `AgentContribution`, changing run lifecycle or confirmation semantics, or touching the event pipeline. For voice-specific subsystems see [Realtime voice system](../voice/realtime-voice-system.md); for the A2UI generation/rendering pipeline see [A2UI business execution](../a2ui/a2ui-execution.md); for the AppFunctions tool family see [AppFunctions](../appfunctions.md).

## At a glance

An agent in Gimi is not a stored object; it is assembled at runtime from ADK-Kotlin primitives for a specific configuration and cached. One chat turn flows like this:

```text
ChatViewModel (feature/chat)
   |  ChatRunLifecycleCoordinator — run lease/token via AgentRuntimeGate (per-session busy state)
   v
PrepareChatSendUseCase (domain/conversation) — tool config resolution, turn archival
   v
ChatAgentRepository.send (domain interface)
   |  AdkChatAgentRepository (data/agent/conversation)
   v
AgentChatRunner.createExecution -> AgentChatExecution
   |  ADK Runner.runAsync, StreamingMode.SSE, RunConfig.customMetadata
   v
Flow<ADK Event>
   |
   +- live view:   Event.toDomain() -> ChatRunEvent -> AgentEventReducer (partial-merge by Event.id)
   +- history:     data/conversation EventMapper rehydrates Messages from Session.events
   +- persistence: ADK SessionService (Room) is the ONLY store; title/memory via ADK plugins
```

There is **no `:domain:agent` module** — agent business contracts (run events, repository interfaces, the runtime gate) live in `:domain:conversation`; the ADK runtime lives only in `:data:agent`.

## Module ownership

| Module | Owns | Must not own |
|---|---|---|
| `:domain:conversation` | SDK-agnostic run models (`ChatRunEvent`, `Message`, `ChatTurn`), `ChatAgentRepository`/`ConversationRepository` interfaces, `PrepareChatSendUseCase`, `ChatRunEventMapper`, `AgentRuntimeGate`/`AgentSessionIdentity` | Any Android, Compose, Hilt, Room, OkHttp, or ADK type |
| `:data:agent` | Agent assembly (`AgentFactory`, `AgentChatRunner`), all `AgentContribution` implementations, model adapters, A2A + A2UI runtime, chat-mode voice execution | Persistence contracts, UI state |
| `:data:conversation` | `AdkConversationRepository` (Room-backed read side), `EventMapper` (history replay), attachment archiving, tool-approval storage | ADK agent assembly |
| `:feature:chat` | `ChatViewModel` + coordinators (`ChatRunLifecycleCoordinator`, `AgentEventReducer`, recovery/tool-config/session-navigation coordinators) | Repository implementations, ADK types |
| `:domain:assistant` | System-assistant entry (Bluetooth/detached voice) — a second execution source into the same runtime | Its own agent runtime |

Other domain modules (`mcp`, `skills`, `plugin`, `modelcatalog`, `memory`, `sandbox`, `a2ui`, `toolauthorization`, …) supply contracts consumed by contributions. `:plugin-api` + `:plugins:*` are dynamic APK plugins loaded by the plugin contribution.

## Agent assembly

Three cooperating pieces, all in `data/agent/src/main/java/github/ponyhuang/gimi/data/agent/`:

1. **`AgentBuildSpec`** — immutable build input: model `selection`, `toolAccessMode` (`ALWAYS_AVAILABLE`/`ON_DEMAND`), `reasoningEffort`, `thinkingEnabled`, `pluginRuntime` snapshot, `instructionSuffix`, `a2uiSupported`.
2. **`AgentContribution` SPI** — a contribution is a self-declared capability owner: `id`, `revision()` (cache-key input; return null when there is no externally mutable config), `toolsets(spec)` (named tool bundles declared on `LlmAgent.toolsets` — resident under `ALWAYS_AVAILABLE`, mostly converted to retrieval candidates under `ON_DEMAND`), `tools(spec)` (tools attached directly), `candidateSources(spec)` (retrieval candidates feeding `ToolSearchToolset` in `ON_DEMAND`), `subAgents(spec)` (non-LLM sub-capabilities the model delegates to via `transfer_to_agent`), `toolCatalog(context)` (flat enumeration for bypass consumers such as recommendation/capability listings — decoupled from agent build). `AgentContributionRegistry` aggregates `Set<AgentContribution>` (Hilt `@IntoSet`), sorts by `id` for determinism, and fails fast on duplicate ids. Existing contribution types (ids): app function (`appfunctions`), a2ui, sandbox (E2B), local, mcp, official, skill, plugin, base (memory/user-input), model catalog (revision-only), remote agent (sub-agents only), delegate tasks (subtasks), direct phone (`direct_phone`, sub-agent only, mounted only while its backend reports availability). What each loads is an implementation detail — read the owning class when it matters.
3. **`AgentFactory` + `AgentChatRunner`** — the factory resolves the model (`AgentLLMModelFactory`, fallback chain: explicit selection → chat-screen selection → first enabled service; errors with a Settings pointer when none), builds the ADK `LlmAgent` from `AgentPrompts.defaultAssistantInstruction() + spec.instructionSuffix`, thinking config, aggregated tools/toolsets/sub-agents, and global `RemoteAgentFailureRecovery` callbacks. In `ON_DEMAND` mode it adds a `ToolSearchToolset` (vector search over an ObjectBox + embeddings store — treated as a rebuildable cache, never migrated; implementation principles in [Tool search](tool-search.md)). The runner LRU-caches at most 10 `SharedRuntime`s keyed on `AgentKey(selection, toolAccessMode, reasoningEffort, thinkingEnabled, instructionSuffix, a2uiSupported, revision)` — the `revision` is a single combined value: `AgentBuildConfigurationSnapshot` captures all contribution `revision()` outputs at cache-lookup time, and its `pluginRuntime` snapshot is what actually gets assembled — and returns a per-turn `AgentChatExecution`.

**Registration**: one `@Binds @IntoSet` line per contribution in `di/AgentContributionModule.kt` (subtasks binds in its own module). Adding a tool/config source = implement `AgentContribution` + one binding line; no orchestration code changes. This abstract-module multibinding is the established, working pattern — the gotcha below about `@Binds` failing to aggregate concerns plain singleton bindings elsewhere in data modules. After adding a binding, verify the contribution actually joins the set (e.g. registry duplicate-id failure or tool-catalog output).

## Run lifecycle, confirmation, and user input

- **Lease**: every run (chat, voice, system assistant) acquires an `AgentRuntimeGate` lease; per-session busy state and task phases live there. Stop semantics: stop rejects pending confirmations; input requests have no reject and stay pending.
- **Tool confirmation** rides the ADK `adk_request_confirmation` long-running protocol. `AgentChatExecution.respondToToolConfirmation` collects ALL pending decisions before resuming (ADK 1.2.0 quirk: resuming the invocation once per decision instead of once with all decisions ends the invocation early). UI side: `ChatRunLifecycleCoordinator` queues `pendingToolConfirmations`, supports "always allow" via `ToolApprovalRepository` and per-turn auto-approval.
- **Enable/disable**: `LocalToolContribution.revision()` tracks `ToolAuthorizationRepository.revision`, so toggles rebuild cached agents; per-request filtering happens inside toolsets via `ToolRunMetadata` carried in `RunConfig.customMetadata`.
- **User input protocol** (`conversation/UserInputToolProtocol.kt`): `adk_request_input` → `{"input": ...}`, `get_user_choice` → `{"choice": ...}`; the reply is a role=user `FunctionResponse` with the original call id (the tool does not re-run). Because ADK does not echo the resume as an event, the UI synthesizes the response message locally. Names' source of truth: `UserInputToolCallNames` in `domain/conversation`.
- **Persistence**: history lives entirely in ADK's `SessionService` (Room). `retry=true` resumes the original saved invocation (no duplicated user events on disk).

## Voice mode and remote agents (brief)

- **Chat-mode voice** (`data/agent/voice/VoiceAgentConversation.kt`) feeds recognized text into the SAME cached runtime and session as text chat — no separate agent. Differences: per-call runtime lease, tool confirmations auto-approved by design, `adk_request_input`/`get_user_choice` pause until the user's next utterance, text deltas stream to TTS, and it runs with a read-aloud `instructionSuffix` and no A2UI capabilities (so a different cache key). Realtime (Live-API) voice is a separate subsystem — see [Realtime voice system](../voice/realtime-voice-system.md).
- **Remote agents (A2A)** (`data/agent/a2a/`): `RemoteAgentContribution` exposes configured remote agents as `subAgents()` (model delegates via `transfer_to_agent`). Card fetch: OkHttp, cancellable, 15 s timeout, no retries/redirects, size cap, bearer header. `ResilientRemoteAgent` converts remote failures into non-terminal local failure events so the parent turn continues. Remote agent instances must be created fresh per tree assembly — never reuse an attached one.

## Invariants

1. **Domain purity**: `:domain:conversation` (and all domain modules) depend only on Kotlin/coroutines; `ChatRunEvent`/`ChatAgentRepository` are the SDK-agnostic seam. ADK types stop at `:data:agent`/`:data:conversation`.
2. **Cache-key discipline**: build-level knobs go in `AgentKey`; session-level knobs (tool checkboxes, confirmation toggles) go in `ToolRunMetadata` via `RunConfig.customMetadata` — never the cache key. Any config a contribution consumes must be reflected in its `revision()` or stale cached agents will serve.
3. **Event-mapping parity**: streaming (`ChatRunEventMapper`) and history replay (`EventMapper`) MUST share id rules: `Message.id = Event.id`, part ids derived from message-internal part ordinal (thought and text share index 0). Never re-sort after DAO read (DAO order = append order = emit order). Per-part degradation only — one bad event must never blank a session.
4. **Credentials never enter `ModelRuntimeMetadata`** (ADK merges custom metadata into persisted events); they are re-resolved per tool execution.
5. **JSON-native tool payloads**: tools return structured `Map<String, Any?>`, not serialized strings.
6. **Cancellation propagates**; `NonCancellable` is reserved for persistence/finalization boundaries (A2UI task finalization, draft cleanup, lease release).
7. **Retries are bounded and read-only**: `AgentToolRetryPolicy` covers only read-only tools (`list_skills`, `load_skill*`, `tool_search`); execution tools never enter the retry budget.

## Gotchas

- `di/AgentModule.kt` is an `object` with `@Provides` (~17 singletons); the runner is lambda-parameterized rather than constructor-injected. Data-module Hilt gotchas: prefer `object` + `@Provides`; `@Binds` in data modules has silently failed to aggregate before (stale `kspCaches` are stubborn).
- ADK 1.2.0 confirmation batching quirk (above) — "fixing" it to resume per-batch regresses silently.
- Voice auto-approval of confirmations is a documented design decision, not a bug.
- A2A card fetch forbids redirects/retries (auth-header leak risk); remote error *events* are treated as exceptions because SDK behavior varies.

## When-changing checklist

- New tool/config source → new `AgentContribution` + one `@Binds @IntoSet`; unique `id`; `revision()` must change on any consumed-config change.
- Lifecycle/confirmation changes → keep the collect-all-decisions-then-resume behavior; check `AgentRuntimeGate` lease symmetry (acquire/release, `NonCancellable` release).
- Event pipeline changes → update streaming and history mappers together; mapping-parity tests exist (`EventMapper*Test`, `ChatRunEventMapper*Test`).
- Prompt changes → `AgentPromptsTest` guards the default instruction; `instructionSuffix` changes create new cache entries.
- Cache/assembly changes → see `AgentChatRunnerCacheTest`, `AgentFactoryToolAccessTest`, `AgentContributionRegistryTest`.
