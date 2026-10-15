# Contributing to Gimi

Contributions are welcome, including bug reports, documentation improvements, translations,
feature proposals, and code changes. Read the [README](README.md) for the current feature set
and [AGENTS.md](AGENTS.md) for detailed repository conventions.

## Project direction

Gimi's features and interface design are primarily shaped by the author's own usage habits,
with inspiration from established products and their interaction patterns. Suggestions are
welcome, especially when they explain a concrete use case and how a change improves everyday
use. For substantial feature or interface changes, open an issue to discuss the direction
before investing in implementation.

## Reporting bugs and proposing features

Search existing [issues](https://github.com/pony-huang/Gimi/issues) before opening a new one.
For a bug report, include:

- The app version, Android version, and device model.
- Steps to reproduce, expected behavior, and actual behavior.
- Relevant settings, provider/model or plugin details, and sanitized logs or screenshots.

Remove API keys, tokens, personal conversations, and other private data from reports.
For a feature proposal, describe the problem, the intended workflow, and any relevant examples
or design references.

## Working on a change

1. Fork the repository and create a focused branch for your change.
2. Open the project in Android Studio and configure the Android SDK. Use JDK 21, as CI does;
   dependency versions are defined in `gradle/libs.versions.toml`.
3. Keep local SDK paths and secrets in ignored `local.properties` or environment variables.
4. Make one coherent change and follow the owning module's conventions.
5. Run the checks appropriate to the change and open a pull request.

Keep temporary files under the ignored repository-root `temp/` directory. Do not commit build
outputs, caches, IDE state, credentials, or local APKs.

## Architecture and code conventions

`settings.gradle.kts` defines the module graph. Place changes in the capability that owns them:

- `:feature:<capability>`: screens, routes, presentation state, and ViewModels.
- `:domain:<capability>`: business models, repository interfaces, and reusable workflows.
- `:data:<capability>`: persistence, network clients, Android gateways, and SDK adapters.
- `:core:*`: narrow, business-agnostic shared infrastructure or UI.
- `:app`: startup, top-level navigation, services, and cross-capability composition.

Feature modules must not depend on one another or on `:app`. Domain code must stay independent
of Android and provider SDKs. ViewModels depend on domain interfaces or use cases; keep storage,
network, and SDK details behind data implementations.

Use idiomatic Kotlin with four-space indentation and match nearby formatting. Document Kotlin
`data class` and `data object` responsibilities with KDoc. Add concise Chinese comments where
complex logic needs an explanation of its invariants or lifecycle. Preserve coroutine cancellation.

## UI and translations

Start UI changes with an isolated Compose preview using fixture data for light and dark themes.
Share the proposed design for maintainer review before wiring it into production. After approval,
integrate the design and remove temporary draft files and preview-only dependencies.

Keep screens stateless, collect state lifecycle-aware in routes, and keep business-operation
state in ViewModels. Preserve Material 3 theme tokens, accessibility semantics, and system insets.

Chinese is the default resource locale (`values/`); English resources live in `values-en/`.
Add user-facing strings to both files in the owning module. Keep [README.md](README.md) and
[README.zh-CN.md](README.zh-CN.md) synchronized. This contribution guide is maintained in English only.

## Validation

Run commands from the repository root. On Windows, use the Gradle wrapper:

```powershell
# Compile an affected module (replace chat with the owning capability)
.\gradlew.bat :feature:chat:compileDebugKotlin

# Run focused JVM tests in the affected module
.\gradlew.bat :feature:chat:testDebugUnitTest

# Compile the app graph when public module contracts or app composition change
.\gradlew.bat app:compileDebugKotlin

# Build an APK for DI/generated wiring, resources, manifests, packaging, or deployment
.\gradlew.bat app:assembleDebug

# Check whitespace errors
git diff --check
```

On macOS or Linux, use `./gradlew` for the equivalent tasks. Choose the smallest checks that
cover the change; APK assembly already covers app compilation. Add behavior-based tests for
changed business rules, state transitions, parsers, repositories, and gateways. Use fakes or
mocked services rather than live model APIs or real credentials.

Validate Compose changes through compilation and previews; do not add automated UI interaction,
layout, or screenshot tests. Use device checks for hardware, permissions, system integration,
or UI behavior that JVM tests cannot establish, preferably on a physical device.

Documentation-only changes need diff and link review, without a build or test suite. Report
compilation, JVM tests, APK assembly, and device verification separately, including any blockers.
See the [release guide](docs/releasing.md) for CI and release details.

## Pull requests

- Explain the problem, resulting behavior, and scope; link the relevant issue or OpenSpec change.
- Include the checks you ran and their results, with screenshots or previews for Compose changes.
- Keep unrelated cleanup and generated/local artifacts out of the diff.
- Use focused commits with subjects such as `fix(chat): preserve attachment state` or
  `docs: clarify model setup`. Follow `<type>[optional scope][!]: <description>` and keep
  subjects under 72 characters; mark breaking changes explicitly.

The project is licensed under [Apache License 2.0](LICENSE). Make sure any contributed code
or assets can be distributed under the project's license, and retain required attribution.
