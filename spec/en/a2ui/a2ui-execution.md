# A2UI business execution

Audience: agents and developers touching the A2UI generation chain, protocol validation, chat catalog components, or upgrading the androidx a2ui artifacts. Chinese companion: [中文说明](../../zh/a2ui/a2ui-execution.md).

Read this before adding or changing a catalog component, altering the `generate_a2ui` tool contract, changing surface lifecycle/retry semantics, or upgrading protocol artifacts. This document absorbs the former "Compose A2UI (agentic UI) reference" section of the root `AGENTS.md`.

## Protocol background and canonical references

A2UI (agentic UI) lets the LLM agent produce rich interactive UI as native Compose components by emitting JSON. Agents never generate or execute arbitrary UI code; the **component catalog is the contract** declaring which components, properties, and functions they may use. The runtime and UI layers are design-system agnostic — this project renders with the AndroidX Material 3 Basic Catalog.

Canonical references (re-fetch before adopting or upgrading; the protocol evolves):

- Compose renderer page: <https://developer.android.com/develop/ui/compose/agentic.md.txt> (retrieved 2026-10-19)
- A2UI protocol home: <https://a2ui.org/>; spec: <https://a2ui.org/specification/v0.9.1-a2ui/>; catalogs: <https://a2ui.org/concepts/catalogs/>

Artifacts (all `1.0.0-alpha02`, see `gradle/libs.versions.toml`): `androidx.a2ui:a2ui-engine` (parsing/schema validation, `:data:a2ui` only), `androidx.a2ui:a2ui-model` (`:data:a2ui`, `:feature:chat`), `androidx.a2ui.compose:compose-runtime` and `compose-ui` (fine-grained snapshot updates, catalogs, progressive rendering; `:feature:chat` only), `androidx.compose.material3:material3-a2ui` (Basic Catalog + `A2uiSurface` host; `:feature:chat` only).

## At a glance

```text
LLM emits generate_a2ui tool call (intent / target_surface_id / changes / component_names)
   |  A2uiToolContribution — declared only when a2uiSupported && settings enabled
   v
A2uiGeneration — replay prior surface state, scope the catalog to used components,
   |             isolated UI-only sub-agent streams a preview and final snapshot on create
   v
A2uiCreateScope — returns "pending" immediately; generation runs on a side scope and can publish a validated preview
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
| `:feature:chat/a2ui` | `ChatA2uiCatalog` wiring, `ChatA2uiCoordinator` (session sync/submissions), surface host, previews | Agent assembly; protocol parsing |

Conversation plumbing: `ChatRunModels` carries a2ui views; `A2uiTaskMessages` replaces task snapshots by `surfaceId` and flips leftover PENDING tasks to CANCELLED when no run is active; `data/conversation EventMapper` converts `a2ui_generation`-authored events into synthetic `generate_a2ui` function responses so call/response chips pair.

## Generation chain (data/agent/a2ui)

- **Tool contract**: `generate_a2ui(intent, target_surface_id, changes, component_names)`. `processLlmRequest` injects a dynamic routing instruction (built from the capabilities schema): when A2UI is mandatory vs Markdown, routing for standard containers, inputs and media, and how to handle `a2ui_interaction` submissions.
- **Catalog scoping**: the generation schema contains only base components + requested components + their `$ref` closure; unreferenced `$defs` are stripped (otherwise the schema's any-component construct would drag the full catalog into every validation). Prior components stay alive on updates via `previousComponentNames`.
- **Sub-agent**: async creates request JSON Lines with a compact preview snapshot followed by a final snapshot. Each is validated independently. Invalid streamed output gets one correction via the single `render_a2ui` call used for other generation paths. Network/SDK errors are never retried. Async creation has a 45-second deadline and a timeout becomes a retryable failure.
- **Async create**: `A2uiCreateScope` returns `pending` immediately and generates on a side scope; after the runner flow ends, results are archived as events — cancellation persists CANCELLED via `NonCancellable`, failures persist FAILED (so the retry entry stays).
- **Retry**: only for FAILED/CANCELLED tasks; reruns generation from the archived `changes` with no parent run and no business tools.

## Direct creation and latency

- **First useful content** is the first visible card content a user can act on or read as a result. For a device control, it means the live state is visible and the control is usable. A loading placeholder has its own timestamp.
- `generate_a2ui` can create a card directly from `template_id=device_controls` or from `components` and optional `data` JSON strings. Both paths use the normal A2UI 0.9.1 validation and history paths, make zero UI model requests, and submit the entire valid tree to the renderer immediately.
- An invalid direct composition returns a validation error and a single-use `repair_id` for the same surface and turn. A failed correction falls back to the existing UI generation path when `changes` and supported `component_names` are available. The existing generation validation and retry limits apply. Invalid drafts never become card documents.
- Enable local timing with `adb shell setprop log.tag.GimiA2uiTiming DEBUG`. Elapsed times are relative to chat submission and include tool entry, UI model requests and first output, format correction, placeholder and surface frames, and final update delivery. A visible, actionable device control additionally records `first_useful_device_frame`. The log includes only stages, durations and a short surface correlation suffix.
- Use the measurements before piloting incremental updates on weather cards. A useful first weather result contains a location and confirmed current temperature.

## Validation chain (data/a2ui)

`AndroidxA2uiProtocol.validate`, in order: non-blank surfaceId → official message parser → shared surfaceId → delete revisions carry exactly one delete message → otherwise exactly `createSurface`/`updateDataModel`(root `/`)/`updateComponents` → catalogId match (no negotiation — one catalog) → 1..256 unique component ids with `root` → graph integrity (children exist, no cycles) → full schema validation through the official `A2uiCoreMessageProcessor` under a 5 s timeout. **Timeouts are normalized to `IllegalArgumentException`** so they feed the sub-agent's correction loop instead of silently cancelling the caller's scope. `replay(records)` folds revisions/submissions into final documents (a submission overwrites the data-model root so restored history keeps last submitted form values).

## Rendering chain (feature/chat/a2ui)

- **Catalog**: `chatA2uiCatalog` provides AndroidX Material 3 Basic Catalog with app media, URL handling and a bounded List viewport. `chatA2uiCustomCatalog` owns the twelve Gimi cards and controls (including `MusicPlayerCard` for both NetEase Cloud Music and Spotify, featuring a NetEase-red base theme with dynamic cover-extracted ambient palette, in-card Media3 streaming or Spotify Connect remote control, queue popup sheet, favorite events, and `orpheus://` / `spotify:` client deep-link fallback). Chat exports the versioned union for generation and validation, and registers both the union and the official catalog for rendering. Each surface continues to use one A2UI 0.9.1 catalog ID. Use Agenda for timed schedules and Timeline for milestones. Switch writes to a bound data-model path; applying a business change still requires a Button submission.
- **Coordinator**: one session-scoped official `A2uiMessageProcessor`; submissions gated by `canSubmit` (agent not running). `sync` diffs replayed records: data-only changes patch just `updateDataModel` (deleting a displayed surface crashes Compose on required bindings); additive component revisions update the existing surface when retained ids and types match; other structural changes delete and re-push. Outbound client events become `A2uiInteraction` chat turns (not persisted to history as drafts). Historical surfaces that reference a different catalog ID are skipped by the renderer.
- **Host UI**: stateless surface host in `ChatA2uiContent` keyed by surface id, plus `A2uiTaskStatus` placeholder cards (spinner/failed/cancelled + retry). Surfaces are filtered by owning message in `ChatScreen`/`MessageBubble`. The surface inherits the chat Material theme. Newly received multi-component cards can show their structure first and then receive the full validated component tree through the official processor. Historical surfaces render directly.

## Music card function calls

- **A2UI interaction** (`A2uiInteraction`): A surface event that carries the user's action and context into a new Agent turn. The music recommendation button emits `recommend_similar_music` through this path.
- **Direct function call**: An explicitly registered operation with arguments and a result. The call itself needs no new model turn. A NetEase heart click calls the enabled plugin's favorite tool and changes the heart after success. Agent tool `seek_a2ui_music_player` targets one mounted, seekable player in the calling session and surface.
- The two direct calls use Gimi's in-process wiring with the existing AndroidX 0.9.1 renderer. A future v1.0 transport can reuse the operation contracts. Rendering and replay never execute these calls.

## Live device controls

- `MediaVolumeSlider` and `FlashlightSwitch` read live device state from the host. Neither accepts a generated value. Rendering a new or historical surface only subscribes to state.
- Releasing the volume slider applies one authorized write and reads the resulting media volume. Visible sliders also track changes from hardware controls or Agent tool calls.
- Toggling the flashlight checks tool authorization and camera permission. The switch reflects the system torch callback, including changes initiated outside Gimi. The permission button requests camera access without applying a pending toggle.
- Normal Agent tool calls retain their existing confirmation flow. A separate ordinary Button event can submit a request for the Agent to choose a setting.

## Cross-cutting rules

1. **Untrusted input**: agent JSON is parsed and schema-validated by the official engine before reaching UI state; error states are surfaced (per-component Loading/Error); business actions remain normal authorized tools — submissions only carry structured interaction data.
2. **Layer boundaries**: only `:feature:chat` touches Compose a2ui artifacts; `:data:a2ui` uses the engine without Compose. The single deliberate reverse edge is `:feature:chat` `testImplementation(project(":data:a2ui"))` so JVM tests validate the real catalog↔validator contract.
3. **i18n**: host strings under `feature/chat` `chat_a2ui_*` (Chinese default + `values-en`); prompts require visible labels in the user's language; the Basic Catalog supplies standard formatting functions.
4. **Preview-first**: the Basic Catalog gallery and task states have light/dark previews; no automated UI interaction tests.
5. **R8**: no a2ui-specific proguard rules today — androidx artifacts carry consumer rules.

## Upgrade checklist

1. Re-fetch the Compose renderer page and a2ui.org spec; confirm the current protocol version instead of assuming 0.9.1.
2. Keep all five artifacts version-aligned; check for interface deprecations (e.g. `A2uiComponent` → `A2uiComponentV2` style) and catalog-negotiation changes.
3. Update the hardcoded `"v0.9.1"` strings (`A2uiGeneration.kt` message builder, `ChatA2uiCoordinator.kt` delete message) and verify the catalog ID supplied by the AndroidX factory.
4. Verify `CatalogSchema`'s `common_types` map still covers the official definitions; re-run `ChatA2uiCatalogValidationTest`, `A2uiComponentGraphTest`, and the data/a2ui protocol tests.

## Gotchas

- The capability flag rides `AgentKey` (`a2uiSupported`), so capability-less executions (e.g. live voice) never declare `generate_a2ui`; the settings toggle enters the a2ui contribution's `revision()` to rebuild cached runtimes.
- `ChatA2uiSubmissionTest` mockk-substitutes `ProtocolJsonReader` into the official parser (JVM lacks Android JsonReader) — brittle but deliberate; do not "simplify" it away.
- Completion events are authored `a2ui_generation`, not by the model; history mapping special-cases that author.
