# Repository Guidelines

## Structure, Ownership, and Architecture

### Module map

`settings.gradle.kts` is the source of truth for this multi-module Android app. Responsibilities:

- `:app` — composition root for startup, top-level navigation, Android services, and cross-capability Hilt wiring; never a catch-all for UI, domain, data, or provider logic.
- `:core:common`, `:core:audio`, `:core:network`, `:core:testing` — narrow shared infrastructure.
- `:core:designsystem` — theme tokens and stateless, business-agnostic Compose components.
- `:domain:<capability>` — domain models, repository interfaces, and use cases.
- `:data:<capability>` — repository implementations, Room/Preferences/Keystore storage, Android gateways, network clients, and third-party SDK adapters.
- `:feature:<capability>` — feature UI, routes, contracts, ViewModels, and feature-specific components.
- `gradle/libs.versions.toml` — dependency/version catalog. Project working notes and temporary artifacts live under ignored `temp/`; long-lived documentation belongs in `spec/<lang>/` only after that directory is introduced.

### Capabilities

Current domain capabilities are `modelcatalog`, `assistant`, `conversation`, `speech`, `mcp`, `workfiles`, `permissions`, `toolauthorization`, `skills`, `appupdate`, `plugin`, `recommendation`, `memory`, `workspace`, and `appearance`. Provider/runtime-only data capabilities also include `agent`. Current features are `modelsettings`, `settings`, `mcp`, `workfiles`, `permissions`, `toolauthorization`, `skills`, `chat`, `assistant`, `plugin`, `recommendation`, `memory`, and `workspace`.

### Ownership and placement

Module ownership overrides legacy package names. Use packages matching the owning capability; never recreate catch-all trees such as `app/ui`, `app/data`, or `app/model`. Place new work as follows:

1. Feature-only presentation or interaction state → `:feature:<capability>`.
2. Business model, rule, repository contract, or reusable workflow → `:domain:<capability>`.
3. Persistence, network, Android API, secure storage, or SDK adapter → `:data:<capability>`.
4. Stable, business-agnostic shared infrastructure or UI with at least two real consumers → the narrowest `:core:*`.
5. Startup, navigation graph, service registration, or cross-capability composition → `:app`.

If no capability owns the change, create a focused domain/data/feature slice; do not use a nearby unrelated module or create empty placeholder modules.

### Dependency directions

Required runtime and dependency directions:

```text
Route -> Stateless Screen
  |          ^
  v          |
ViewModel <- UiAction
  |
  v
UseCase / Domain Repository Interface
  ^
  |
Data Repository Implementation -> Room / Secure Settings / Remote Gateway

:app -> :feature:* + :data:* + required domain/core modules
:feature:* -> :domain:* + :core:designsystem
:data:* -> :domain:* + required core infrastructure
:domain:* -> Kotlin/coroutines and explicitly shared domain contracts only
feature A -X-> feature B
domain/data/core -X-> feature or app
```

### Mandatory boundaries

- Feature modules never depend on one another. Move shared business contracts to domain and genuinely business-agnostic UI to `:core:designsystem`.
- A feature may depend on another narrow core module (e.g. `:core:common`, `:core:audio`) only when the dependency is business-agnostic and explicitly documented; otherwise expose a domain contract.
- Feature modules never depend on `:app`; expose contracts from the owning domain or core module and perform composition in `:app`.
- Domain code never depends on Android, Compose, Hilt, Room, OkHttp, provider SDKs, or data models; domain repositories are interfaces.
- Keep SDK, HTTP, database, Keystore, Preferences, and Android framework details behind data/core gateways or repository implementations.
- ViewModels depend on use cases or domain repository interfaces, never concrete repositories, DAOs, Context, Toast, navigation controllers, OkHttp, Room, or provider SDK objects.
- Use a use case for branching business rules, cross-repository operations, or reusable workflows; avoid trivial pass-through use cases unless they establish a necessary contract boundary.
- Prefer constructor injection and Hilt. Bind domain interfaces in the owning data module; compose capabilities in `:app`.
- Avoid generic `Manager`, `Helper`, `Utils`, or `BaseViewModel` dumping grounds. Name one responsibility and use the narrowest owner.
- Suspending code should let cancellation propagate naturally: avoid broad catches, catch recoverable exception types, and use the shared cancellation-aware recovery helper only at boundaries that require a fallback.
- Do not promote code to `core` merely because two files look alike.
- Resolve graph conflicts through contracts/interfaces, not shortcut dependencies. Document any temporary exception with its owner and removal condition.

## Compose, UI, i18n, and Style

### UI and state flow

- Each screen exposes one immutable `*UiState`; user intent enters through a `*Action`/`*UiAction` contract or explicit synchronous callbacks.
- Route composables collect state lifecycle-aware and own navigation, Toast/Snackbar delivery, activity results, permissions, URL opening, and other Android/UI side effects.
- Screens and business components are stateless: state enters as parameters and events leave as callbacks. They do not fetch ViewModels, repositories, or Context, or launch business coroutines.
- Keep loading, testing, refreshing, notices, and other ephemeral business-operation state in the ViewModel.
- `:core:designsystem` components reference no ViewModels, repositories, domain models, navigation, Toast, network calls, or business coroutines; keep them independently previewable with fixture data. Accepted exception: `ui/settings/llmmodel/LLMModelServiceIcon` and its `ic_model_provider_*` drawables are shared brand assets consumed by both `:feature:chat` and `:feature:modelsettings`; they stay in `:core:designsystem` because feature-to-feature dependencies are forbidden.

### Preview-first UI design review

- Any UI adjustment or UI iteration must begin with a preview design draft before implementation: a `@Preview @Composable`-based draft (using fake/fixture data, themable for light and dark) that renders the proposed layout, new states, and interaction entry points.
- At the start of each UI task, ask whether the user wants rendered design images for remote review, unless they have already stated a preference for that task. Wait for the answer before choosing the image-delivery branch; draft preparation may continue meanwhile.
- If requested, render the actual Compose draft, visually inspect the captures, and return viewable images in the conversation before asking for design approval. Include light/dark themes and the relevant changed states; keep image artifacts under `temp/`. Source files, file links, and compilation success alone do not fulfill image delivery. If rendering is unavailable, explain the specific blocker and agree on an alternative with the user.
- If declined, skip image rendering and delivery and present the draft with a concise description. Declining images does not waive the design approval below unless the user explicitly says so.
- Present the draft to the user for review; implementation work may start only after the user explicitly confirms the design. Do not skip this step for small visual tweaks unless the user says otherwise.
- Keep the draft isolated from production wiring (no ViewModel/repository changes yet); once approved, merge it into the real screens, wire up state, and delete the draft file and any preview-only dependencies it required.

### Theming and insets

- Preserve existing theme tokens, dimensions, copy, accessibility semantics, and insets unless explicitly changing them. Use Material 3/design-system tokens instead of hard-coded UI colors.
- Edge-to-edge UI must keep controls and primary content clear of status bars, cutouts, navigation bars, and gesture areas using appropriate insets such as `statusBarsPadding`, `navigationBarsPadding`, or `safeDrawingPadding`; verify visually.

### i18n

- Default locale is Chinese (`values/`); English is in `values-en/`. Each module owns both `strings.xml` files; shared business-agnostic copy belongs in `:core:designsystem`.
- All user-facing `Text`, content descriptions, dialog/field copy, and surfaced Toasts must use `stringResource(R.string.xxx)`. Doc comments and `@Preview` fixtures are exempt.

### Code style

- Use idiomatic Kotlin, four-space indentation, nearby trailing-comma style, PascalCase for types/composables/type files, and camelCase for functions/properties/locals. Match nearby formatting and keep imports organized; no formatter or linter is configured.
- Add concise Chinese comments to complex logic, explaining business invariants, lifecycle/call timing, and why the implementation is necessary rather than restating individual lines.
- Every Kotlin `data class` and `data object` must have KDoc describing its responsibility and the meaning of its data. Document non-obvious properties with `@property` entries or focused property comments.

### Compose A2UI (agentic UI) reference

- Canonical references: Compose renderer page <https://developer.android.com/develop/ui/compose/agentic.md.txt> (retrieved 2026-10-19; the page and its protocol versions evolve, so re-fetch it before adopting or upgrading); A2UI protocol home <https://a2ui.org/>; protocol spec <https://a2ui.org/specification/v0.9.1-a2ui/>; catalogs <https://a2ui.org/concepts/catalogs/>.
- Purpose: the Jetpack Compose A2UI renderer implements the A2UI protocol so agents can produce rich, interactive UI as native Compose components by emitting JSON. Agents never generate or execute arbitrary UI code; the component catalog is the contract that declares which components, properties, and functions they may use.
- Artifacts and responsibilities:
    - `androidx.a2ui.compose:compose-runtime` — fine-grained updates for streaming agent payloads through Compose Snapshot state; only affected components recompose.
    - `androidx.a2ui.compose:compose-ui` — catalog and component implementation APIs, including progressive rendering, per-subcomponent loading states, animated transitions, and two-way data binding back to the agent.
    - `androidx.a2ui:a2ui-model` and `androidx.a2ui:a2ui-engine` — protocol parsing and message processing, schema validation before payloads reach state models, component error states, and automatic error reporting so the agent can self-correct.
    - `androidx.compose.material3:material3-a2ui` — optional ready-made Basic Catalog based on Material 3, with progressive rendering and Material theming.
    - `androidx.a2ui.compose:compose-ui-testing` — dedicated testing APIs.
- Architecture: the runtime and UI layers are design-system agnostic. Build a custom catalog for this project's design system or adopt the provided Basic Catalog.
- Protocol compatibility: the renderer supports A2UI 0.9.1. Catalog schemas are tied to protocol versions, and clients can declare multiple supported catalog versions for capability negotiation with the agent. AndroidX binary compatibility rules apply: non-breaking additions enter core interfaces with default implementations, while incompatible changes deprecate old interfaces (for example `A2uiComponent`) and add new ones (`A2uiComponentV2`) alongside them.
- Adoption guardrails: keep A2UI inside a dedicated feature or business-agnostic presentation/core slice; domain and data modules never depend on Compose or A2UI types. Treat agent JSON as untrusted input: validate it with `a2ui-engine` before it reaches UI state, surface component error states, and keep permission and credential decisions in their owning tested layers. Register only catalog components that follow the design-system, i18n, accessibility, and preview-first rules above. Before upgrading, verify the current protocol version and catalog negotiation behavior instead of assuming 0.9.1 is still current.

## Build and Verification

### Build commands

Run from the repository root with `gradlew.bat` on Windows:

- `.\gradlew.bat app:compileDebugKotlin` — compile the complete debug graph.
- `.\gradlew.bat :feature:<name>:testDebugUnitTest` — run affected feature JVM tests; use the equivalent domain/data task as needed.
- `.\gradlew.bat app:testDebugUnitTest` — run app JVM tests.
- `.\gradlew.bat app:assembleDebug` — build debug APKs.
- `.\gradlew.bat connectedDebugAndroidTest` — run configured instrumentation tests.
- `.\gradlew.bat check` — run configured verification lifecycle tasks.
- `.\gradlew.bat clean` — remove generated build output.

Android Studio may run the `app` debug configuration on a device or emulator.

### Fast validation and Jugg fallback

- For Android compilation and deployment, prefer Android Studio's Jugg run configurations through `mcp_servers.mcpserver`. First call `get_run_configurations` with the repository's `projectPath`, then call `execute_run_configuration` with the returned `jugg:app` configuration (or the matching plugin's `jugg:*` configuration). This IDE MCP route takes priority over the Jugg CLI and direct Gradle commands.
- Inspect the run output and, when returned, `fullOutputPath` until compilation reaches a terminal result. A successful MCP invocation or `Jugg compile started` only confirms startup. Report incremental compilation, Jugg's automatic Gradle fallback, deployment, and device verification separately; `No file changes` followed by Gradle fallback is not an incremental compile success.
- If the IDE MCP route or required Jugg configuration is unavailable, try the Jugg CLI; if Jugg is unavailable, use conventional Gradle/Android CLI commands. For Jugg failures, inspect and report the cause before falling back under the rules below. Inspect Jugg's resolved command: it may run `:app:assembleDebug` with the configuration cache disabled rather than a narrow compile.
- For JVM tests and required Gradle checks that Jugg's incremental path cannot establish, choose the smallest targets satisfying the testing rules below and combine compatible targets in one `gradlew.bat` invocation when practical. Existing Gradle commands remain the fallback and required-check reference. When a change requires both JVM tests and APK assembly, finish the focused tests and resolve their failures before starting assembly; combine compatible compile/test targets within that stage.
- If Jugg produces no new output or progress for 30–60 seconds, inspect Android Studio's window and owned dialogs, the output file, and the actual running build before retrying. A `Build Files Changed Confirm` dialog can block compilation; a missing device alone does not establish the cause. Resolve the observed prompt using the current diff and baseline, and resume the existing task rather than launching another waiting compile. Open or foreground Android Studio when needed; record the concrete blocker before taking a documented fallback.
- If Jugg's embedded Kotlin compiler reports `INTERNAL_ERROR` with `Could not find installation home path` / missing `product-info.json`, classify it as an IDE/Jugg compiler-environment failure, not a Kotlin source error. Retry the incremental compiler at most once; if it repeats, validate with the required Gradle targets and report the Jugg failure separately.
- If Jugg reports `READY_FULL_COMPILE (build.gradle.kts changed)`, inspect both `git diff` for build scripts and Jugg's `getLastBuildFile ... exists: false` entries. Missing Jugg history can trigger this verdict even when this task changed no build script. Accept one full build to re-establish the baseline, or use direct Gradle for compile-only validation; do not repeatedly retry incremental compile against the same state or delete build artifacts to force it.
- Changes to Hilt/Room annotations, generated code, or bytecode transforms need the Gradle build required by the testing rules; Jugg's source-to-dex incremental path does not rerun every processor or transform. Keep one build running at a time.
- After switching branches, a generated-code failure referencing a removed class may come from stale outputs. Check the current source/dependency graph and generated-file timestamps, then clean only the affected module's generated outputs when confirmed. Use a repository-wide `clean` only when evidence shows the problem spans modules.
- After a test failure, inspect the exact failing test and cause before rerunning. Repair a stale fixture or source issue, then rerun the same targeted tests; report unrelated failures separately. Reuse successful validation for behavior unchanged by subsequent edits. Record compilation, tests, APK assembly, deployment, and device verification separately; distinguish active build time from IDE/dialog waits and infrastructure retries. A combined test-and-assembly command's elapsed time is not the unit-test execution time.

### CI/CD (GitHub Actions, `.github/workflows/`)

- `ci.yml` delegates to `verify.yml`: push/PR to `main` and explicit `workflow_dispatch` run Python release-tool tests, architecture checks, `testDebugUnitTest`, `assembleDebug`, plugin compilation, and `app:lintDebug` on JDK 21 (Ubuntu).
- `release-please.yml`: main pushes maintain a Conventional Commits version PR updating `version.txt`, `.release-please-manifest.json`, and `CHANGELOG.md`; `prepare_version_pr.py` assigns `releaseVersionCode` in `gradle.properties` from main + 1 before explicitly dispatching CI. Re-running the same version PR retains its assigned code. On merge, release-please creates a tag and Draft Release and explicitly calls `release.yml`, avoiding `GITHUB_TOKEN` event suppression.
- `release.yml`: `vX.Y.Z` / `vX.Y.Z-rc.N` tag pushes, manual dispatch with a required existing tag, or reusable calls verify the exact tagged commit, then build and validate formally signed App/plugin APKs. All four signing secrets are mandatory for publication; local signing priority remains CI properties > IDE wizard > debug fallback. CHANGELOG.md is the Release body source. Complete artifacts are uploaded to Draft before publication; published releases are immutable. R8 mapping is kept internally as an Actions Artifact. No Google Play publishing.
- App and plugins share `version.txt` and `gradle.properties`'s `releaseVersionCode` through `gimi.android.application`. See `spec/en/releasing.md` for setup, version PR review, RCs, and retries.

### Testing rules

- Keep validation proportional to risk. During iteration, edit and review a coherent change before compiling; use the shortest relevant check at that checkpoint instead of rebuilding after each file. Escalate when the change crosses module contracts, persistence, security, generated code, or release wiring, or when a focused check exposes uncertainty.
- Use TDD selectively for core business logic or complex problems with a meaningful test seam, or when the user explicitly requests it. Non-core code and simple changes, including UI, layout, window sizing, copy, and low-risk wiring, do not require TDD; implement directly and use proportional compilation, previews, or manual verification. This scope takes precedence over skills that prescribe TDD for every feature or bug fix.
- Unit tests written for a task are normally development-time verification and should not be committed by default. Before committing, remove temporary test files and development-only additions to existing tests, or move reusable scratch tests under ignored `temp/`. Retain only coverage explicitly requested by the user or the smallest justified regression test for a necessary risk boundary; state the reason for any retained new test. Preserve existing tests and make the minimal fixture/expectation updates needed to keep them valid. Passing a temporary test does not itself justify adding it to the repository.
- Independent HTTP or paid tool integrations need focused boundary tests for request/response mapping, credential sourcing, validation, failure/retry behavior, and cancellation, plus domain tests for enablement and selection rules where applicable. Check the HTTP client's implicit retry behavior when duplicate requests affect cost or correctness. Give each behavior a primary test owner; add cross-layer tests only for wiring risks not already covered, rather than repeating the same static declarations across support-matrix, catalog, and factory tests.
- When diagnosing and fixing a problem, inspect existing logic and available runtime evidence first. If these do not reveal the cause, use focused unit tests to reproduce the behavior and validate hypotheses, including for non-core code. This diagnostic exception does not require TDD; whether to commit the tests follows the test submission scope above.
- Pure text-only changes, including documentation, comments, user-facing copy, and prompt wording, do not require adding or updating automated tests. This applies when the change does not also modify Kotlin control flow, parsing or interpolation logic, tool definitions, structured output contracts, resources wiring, or UI behavior. Review the diff and use only lightweight, directly relevant validation; do not run broad builds or test suites solely for these text-only edits.
- Use JUnit 4 in the owning module's `src/test`; use behavior-based `*Test.kt` names. Put Android-specific gateway and system integration coverage in `src/androidTest`.
- Behavior-preserving refactors of core business ViewModel transitions or externally visible repository behavior require characterization test coverage.
- Changed core business rules require domain/use-case or ViewModel tests. Gateways/repositories implementing core behavior or necessary risk boundaries require applicable success, failure, exception, and mapping coverage.
- For Compose UI, keep `@Preview` fixtures for relevant visual and conditional states; do not add automated UI interaction, layout, or screenshot tests. Copy selection by provider, icon choice/size, spacing, and text expansion do not require new unit tests when they only affect presentation; use previews and proportional compilation. Keep business rules and permission/credential decisions in their owning tested layers. Preserve existing business-behavior tests; new tests follow the submission policy above. Use manual device checks when needed.
- Tests use fakes, MockWebServer, or mocked gateways, never live model APIs or real credentials.
- After code changes, run `git diff --check` and compile each affected module. Run focused existing/new JVM tests for changed behavior and the characterization or boundary coverage required above; do not run an unrelated module's entire suite solely because code changed nearby.
- Run `app:compileDebugKotlin` when public module contracts or app composition change. Run `app:assembleDebug` for DI/generated wiring, resource identifiers or wiring, manifest, build configuration, packaging, or deployment; assembly covers the app compile, so do not run both targets separately without a specific reason. Pure copy edits follow the text-only rule above.
- Run full module/app test suites for broad refactors, integration milestones, pre-release checks, CI, or an explicit request; they are not a default gate for each iteration. Device tests are for hardware, permission, system integration, and UI behavior that JVM tests cannot establish, or when explicitly requested. State exactly which level passed.
- Every module declares and verifies its own dependencies; compilation through an undeclared `:app` transitive dependency is not completion.
- After repeated runner/device/infrastructure failures following a successful compile, stop retrying, hand off to manual verification, and record the successful compile, repeated failure, and any manual checks.

## Refactoring, Git, and Security

### Refactoring

- Refactor one closed capability at a time; preserve behavior and keep logical commits independently compilable and revertible.
- Use IDE Refactor Move or `git mv`. Move-only commits change only paths, packages, imports, and necessary visibility. Inspect `git diff --find-renames --summary`, compile affected modules, and remove empty legacy package directories.
- This project is in the development and exploration stage. Do not preserve old databases, serialized payloads, configuration fields, APIs, or compatibility adapters unless the user explicitly requests it; choose the cleanest current design, allow destructive local-data recreation, and do not add historical migrations.
- Do not leave forwarding wrappers, duplicate implementations, or legacy adapters without an explicit current requirement.
- Avoid drive-by package moves or broad unrelated cleanup.

### Git

- When the user requests a commit, format its subject as `<type>[optional scope][!]: <description>`. Use `feat` for features, `fix` for bug fixes, and an appropriate type such as `docs`, `refactor`, `test`, `build`, `ci`, or `chore` for other changes. Mark breaking changes with `!` or a `BREAKING CHANGE:` footer; separate any optional body or footer from the subject with a blank line. This rule is self-contained; do not look up an external commit convention unless the user explicitly asks.
- Keep commits focused with imperative subjects under 72 characters. Pull requests describe user-visible changes, tests, relevant issue/OpenSpec change, and Compose screenshots.
- Before committing, inspect staged paths, run `git diff --cached --check`, exclude generated/local artifacts, and confirm architecture boundaries.

### Security

- Never commit build outputs, caches, generated reports, IDE state, API keys, tokens, or credentials. Store local secrets in ignored `local.properties` or environment variables and revoke exposed credentials.
- Filter build/IDE logs before emitting them to tool output or conversation. Prefer an allowlist of task progress, compiler diagnostics, and terminal build results; Jugg logs can contain complete environment-variable dumps. Exclude environment dumps and redact credentials before output, rather than printing raw log tails and filtering afterward.
- Treat untracked `app/release/` APKs as local artifacts; include or delete them only when explicitly requested.

## Working Files and Caches

### The `temp/` directory

- Place any temporary working files, scratch artifacts, downloaded assets, or intermediate caches in the repository-root `temp/` directory (e.g. `temp/<purpose>/...`), not in scattered locations inside modules or the repo root.
- `temp/` is git-ignored; treat its contents as ephemeral and safe to delete at any time. Do not commit anything under `temp/`.
- When a tool needs a default cache directory (Gradle, IDE, SDK downloads, etc.), redirect it to a subdirectory of `temp/` or keep it under an already-ignored top-level path such as `.gradle`, `.idea`, or `.kotlin`; do not introduce new top-level cache directories.

### Worktree sharing

- `temp/` is shared across git worktrees: Claude Code's native `worktree.symlinkDirectories` setting (`.claude/settings.json`, set to `["temp"]`) symlinks the main checkout's `temp/` into every worktree it creates (`claude --worktree` and `EnterWorktree`). Put reusable reference resources (downloaded docs, extracted SDK API sources, vendor pins) in `temp/` so every worktree sees them without re-downloading. Because `temp/` is a link to a shared target, never `rm -rf` it from a worktree (that would recurse into the shared directory); remove only the link with `rmdir`/`os.rmdir`, or run `git worktree remove --force` after deleting the link.

## Android Tooling and Physical Device Verification

### Tool preference

- Prefer the `android` CLI for project, deployment, launch, screenshot, layout, and emulator workflows; consult `android help <command>` first.
- Use plain `android run` for deployment/launch, `android layout` for primary UI inspection, `android layout --diff` for focused changes, and `android screen capture` for secondary visual checks. Always inspect captured PNGs visually.
- Do not pass `--debug` during routine physical-device installation, launch, UI, layout, screenshot, or interaction verification. On some devices it enables "Waiting For Debugger", preventing the Activity and Compose hierarchy from appearing. Use `android run --debug` only when the task explicitly requires attaching a debugger; otherwise launch normally before inspecting the UI.
- Query connected devices/emulators with `adb devices -l` directly; device discovery is adb-first. The `android` CLI exposes no device-list command and cannot resolve some wireless serials (e.g. `adb-tls` mDNS names). Always resolve the serial dynamically at time of use; never hard-code or reuse a serial from memory.
- For everything other than device discovery, prefer the `android` CLI. Fall back to `adb` only when the installed CLI is unavailable, fails, or lacks the operation, such as wake, raw input, install/launch/screencap on a serial the CLI cannot resolve, or a narrow shell command. Record the reason, limit ADB to that operation, then return to the CLI.

### Device verification

- Prefer a connected physical Android device for installation, UI, permissions, system insets, and hardware behavior. Never hard-code a manufacturer, model, or serial; resolve the current physical device dynamically with `adb devices -l`. Use an emulator only when no suitable device exists or the task requires one, and report the fallback.
- Build and run on the selected physical device with:

```powershell
.\gradlew.bat app:assembleDebug
android run --device "$serial" --apks=app\build\outputs\apk\debug\app-arm64-v8a-debug.apk
```

- If `android run`/`android screen capture` cannot resolve the serial (e.g. wireless `adb-tls` names), fall back to `adb -s "$serial" install -r <apk>`, launch with `adb -s "$serial" shell monkey -p <applicationId> -c android.intent.category.LAUNCHER 1`, and capture with `adb -s "$serial" exec-out screencap -p > screen.png`; record the fallback reason.
- Use `android layout --device "$serial" --pretty` for hierarchy/text/bounds and `android screen capture -o build\device-screen.png` for visuals. When the CLI cannot resolve the device, `adb -s "$serial" shell uiautomator dump` + `cat` is the hierarchy fallback. Use narrowly scoped `adb -s "$serial" shell input ...` only when CLI lacks required input.
- If the device is asleep or not foregrounded and CLI has no wake command, use only `adb -s "$serial" shell input keyevent KEYCODE_WAKEUP`, then re-check with `android layout` or `android screen capture`. Never bypass device security. Report persistent test changes such as model selection.
