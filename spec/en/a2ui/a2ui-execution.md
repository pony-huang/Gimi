# A2UI business execution

Reviewed: 2026-11-07 against branch `feature/realtime-voice`. Audience: agents and developers touching the A2UI generation chain, protocol validation, chat catalog components, or upgrading the androidx a2ui artifacts. Chinese companion: [中文说明](../../zh/a2ui/a2ui-execution.md).

Read this before adding or changing a catalog component, altering the `generate_a2ui` tool contract, changing surface lifecycle/retry semantics, or upgrading protocol artifacts. This document absorbs the former "Compose A2UI (agentic UI) reference" section of the root `AGENTS.md`.

## Protocol background and canonical references

A2UI (agentic UI) lets the LLM agent produce rich interactive UI as native Compose components by emitting JSON. Agents never generate or execute arbitrary UI code; the **component catalog is the contract** declaring which components, properties, and functions they may use. The runtime and UI layers are design-system agnostic — this project renders with a custom chat catalog.

Canonical references (re-fetch before adopting or upgrading; the protocol evolves):

- Compose renderer page: <https://developer.android.com/develop/ui/compose/agentic.md.txt> (retrieved 2026-10-19)
- A2UI protocol home: <https://a2ui.org/>; spec: <https://a2ui.org/specification/v0.9.1-a2ui/>; catalogs: <https://a2ui.org/concepts/catalogs/>

Artifacts (all `1.0.0-alpha01`, see `gradle/libs.versions.toml`): `androidx.a2ui:a2ui-engine` (parsing/schema validation, `:data:a2ui` only), `androidx.a2ui:a2ui-model` (`:data:a2ui`, `:feature:chat`), `androidx.a2ui.compose:compose-runtime` and `compose-ui` (fine-grained snapshot updates, catalogs, progressive rendering; `:feature:chat` only), `androidx.compose.material3:material3-a2ui` (Basic Catalog + `A2uiSurface` host; `:feature:chat` only).

## At a glance

```text
LLM emits generate_a2ui tool call (intent / target_surface_id / changes / component_names)
   |  A2uiToolContribution — declared only when a2uiSupported && settings enabled
   v
A2uiGeneration — replay prior surface state, scope the catalog to used components,
   |             isolated UI-only sub-agent renders JSON (no business tools), 1 self-correction retry
   v
A2uiCreateScope — returns "pending" immediately; generation runs on a side scope
   v
AndroidxA2uiProtocol.validate (data/a2ui) — official engine parse + schema + graph checks
   v  (persisted as an Event authored "a2ui_generation"; PENDING/COMPLETED/FAILED/CANCELLED)
EventMapper / ChatRunEventMapper — surface it as a generate_a2ui function-response view
   v
ChatA2uiCoordinator (feature/chat) — derives A2uiRecords from messages, replays, feeds diffs
   v
A2uiSurface (material3-a2ui) renders the card inside its owning MessageBubble
   ^
   +--- user interaction -> A2uiInteraction -> sent as a new chat turn (a2ui_interaction envelope)
   +--- FAILED/CANCELLED card -> Retry button -> ChatAgentRepository.retryA2ui
```

## Module ownership

| Module | Owns | Must not own |
|---|---|---|
| `:domain:a2ui` | `A2uiGenerationTask` (+ status/codec), `A2uiCapabilities`, `A2uiDocument`, `A2uiInteraction`, `A2uiRecord`, `A2uiProtocol` interface, interaction/tool-result codecs — pure JVM (kotlinx-serialization only) | Compose, Android, Hilt, engine types |
| `:data:a2ui` | `AndroidxA2uiProtocol` (validate + replay via the official engine), `CatalogSchema`, `CatalogComponentReferences`, `ProtocolJsonReader` | Compose; executing host functions |
| `:data:agent/a2ui` | `A2uiToolContribution`, `A2uiGeneration` (+ scoped catalog, routing instructions), `A2uiCreateScope`, `A2uiCreateRetry` | Business tool execution |
| `:feature:chat/a2ui` | `ChatA2uiCatalog` custom components, `ChatA2uiCoordinator` (session sync/submissions), theming, surface host, previews | Agent assembly; protocol parsing |

Conversation plumbing: `ChatRunModels` carries a2ui views; `A2uiTaskMessages` replaces task snapshots by `surfaceId` and flips leftover PENDING tasks to CANCELLED when no run is active; `data/conversation EventMapper` converts `a2ui_generation`-authored events into synthetic `generate_a2ui` function responses so call/response chips pair.

## Generation chain (data/agent/a2ui)

- **Tool contract**: `generate_a2ui(intent, target_surface_id, changes, component_names)`. `processLlmRequest` injects a dynamic routing instruction (built from the capabilities schema): when A2UI is mandatory vs Markdown, per-component routing (containers, tables, weather, music, option deck), and how to handle `a2ui_interaction` submissions.
- **Catalog scoping**: the generation schema contains only base components + requested components + their `$ref` closure; unreferenced `$defs` are stripped (otherwise the schema's any-component construct would drag the full catalog into every validation). Prior components stay alive on updates via `previousComponentNames`.
- **Sub-agent**: isolated UI-only agent (own model, no business tools), forced single `render_a2ui` call (`Mode.ANY`). On validation failure, one self-correction retry appends the error text; a second failure returns a tool failure. Network/SDK errors are never retried.
- **Async create**: `A2uiCreateScope` returns `pending` immediately and generates on a side scope; after the runner flow ends, results are archived as events — cancellation persists CANCELLED via `NonCancellable`, failures persist FAILED (so the retry entry stays).
- **Retry**: only for FAILED/CANCELLED tasks; reruns generation from the archived `changes` with no parent run and no business tools.

## Validation chain (data/a2ui)

`AndroidxA2uiProtocol.validate`, in order: non-blank surfaceId → official message parser → shared surfaceId → delete revisions carry exactly one delete message → otherwise exactly `createSurface`/`updateDataModel`(root `/`)/`updateComponents` → catalogId match (no negotiation — one catalog) → 1..256 unique component ids with `root` → graph integrity (children exist, no cycles) → full schema validation through the official `A2uiCoreMessageProcessor` under a 5 s timeout. **Timeouts are normalized to `IllegalArgumentException`** so they feed the sub-agent's correction loop instead of silently cancelling the caller's scope. `replay(records)` folds revisions/submissions into final documents (a submission overwrites the data-model root so restored history keeps last submitted form values).

## Rendering chain (feature/chat/a2ui)

- **Catalog**: `chatA2uiCatalog` — catalogId `https://gimi.app/a2ui/catalogs/chat/v1`, inline, combining Material Basic Catalog primitives with custom components: `ChatA2uiText/Button`, containers (`Disclosure`/`Tabs`/`DetailSheet`), `WeatherToday`, `MusicPlayer`, `DataTable`, `OptionDeck`, media renderers (coil3 image, video/audio players), gallery preview. Custom components override `isReady` for per-component loading states; `ChatA2uiChild` renders Success/Loading/Error.
- **Coordinator**: one session-scoped official `A2uiMessageProcessor`; submissions gated by `canSubmit` (agent not running). `sync` diffs replayed records: data-only changes patch just `updateDataModel` (deleting a displayed surface crashes Compose on required bindings); otherwise delete + re-push. Outbound client events become `A2uiInteraction` chat turns (not persisted to history as drafts).
- **Host UI**: stateless surface host in `ChatA2uiContent` keyed by surface id, plus `A2uiTaskStatus` placeholder cards (spinner/failed/cancelled + retry). Surfaces are filtered by owning message in `ChatScreen`/`MessageBubble`. `ChatA2uiTheme` overrides typography/shapes only — app color scheme and font scaling pass through.

## Adding a custom catalog component

1. Implement the component in `:feature:chat/a2ui` (component + data contract + `*Previews.kt` light/dark) and register it in `ChatA2uiCatalog` — its definition then flows into the exported capabilities schema shared by generation and validation.
2. If the LLM needs routing rules beyond what the schema expresses, add a conditional instruction block in `A2uiGenerationInstructions`/`A2uiGeneration` (injected only when the scoped catalog contains the component).
3. Generation scoping picks the component up automatically: it enters the schema when the model requests it via `component_names` and reaches the validator through the `$ref` closure — no separate registration in `:data:a2ui` unless it uses new common-type shapes (then check `CatalogSchema`'s `common_types` map).
4. Run `ChatA2uiCatalogValidationTest`, `A2uiComponentGraphTest`, and the data/a2ui protocol tests; verify a preview renders in light and dark.

## Cross-cutting rules

1. **Untrusted input**: agent JSON is parsed and schema-validated by the official engine before reaching UI state; error states are surfaced (per-component Loading/Error); business actions remain normal authorized tools — submissions only carry structured interaction data.
2. **Layer boundaries**: only `:feature:chat` touches Compose a2ui artifacts; `:data:a2ui` uses the engine without Compose. The single deliberate reverse edge is `:feature:chat` `testImplementation(project(":data:a2ui"))` so JVM tests validate the real catalog↔validator contract.
3. **i18n**: host strings under `feature/chat` `chat_a2ui_*` (Chinese default + `values-en`); prompts require visible labels in the user's language; catalog functions use ICU `MessageFormat`.
4. **Preview-first**: every custom component ships a `*Previews.kt` with light/dark fixtures; no automated UI interaction tests.
5. **R8**: no a2ui-specific proguard rules today — androidx artifacts carry consumer rules.

## Upgrade checklist

1. Re-fetch the Compose renderer page and a2ui.org spec; confirm the current protocol version instead of assuming 0.9.1.
2. Keep all five artifacts version-aligned; check for interface deprecations (e.g. `A2uiComponent` → `A2uiComponentV2` style) and catalog-negotiation changes.
3. Update the hardcoded `"v0.9.1"` strings (`A2uiGeneration.kt` message builder, `ChatA2uiCoordinator.kt` delete message) and the catalogId if the catalog schema changes.
4. Verify `CatalogSchema`'s `common_types` map still covers the official definitions; re-run `ChatA2uiCatalogValidationTest`, `A2uiComponentGraphTest`, and the data/a2ui protocol tests.

## Gotchas

- The capability flag rides `AgentKey` (`a2uiSupported`), so capability-less executions (e.g. live voice) never declare `generate_a2ui`; the settings toggle enters the a2ui contribution's `revision()` to rebuild cached runtimes.
- OptionDeck events carry `round` starting at 0; `round > 0` means a locally restarted deck repeating itemIds — treat as re-review before re-running side-effect tools.
- `ChatA2uiSubmissionTest` mockk-substitutes `ProtocolJsonReader` into the official parser (JVM lacks Android JsonReader) — brittle but deliberate; do not "simplify" it away.
- Completion events are authored `a2ui_generation`, not by the model; history mapping special-cases that author.
