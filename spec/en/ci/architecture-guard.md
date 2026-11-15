# Architecture guard and CI verification

[简体中文](../../zh/ci/architecture-guard.md) · English

Reviewed: 2026-11-15 · Audience: maintainers of CI / architecture checks and coding agents

> Post-mortem, evidence chain, and remediation plan for GitHub Actions run `43688678563` (job `132734476863`, PR #37).
> "DeepSeek Files API" is the title of PR #37 and the commit message of `f1250fd3`.
> Bottom line: this is not a defect introduced by that commit. Architecture rules finalised on 2026-10-04 drifted away from the module graph that evolved in November, and a gap in CI trigger coverage let the accumulated debt surface all at once when this branch triggered CI for the first time.

## 1. Cause

### 1.1 Symptoms

| Item | Value |
| --- | --- |
| Run / Job | `43688678563` / `132734476863` |
| PR / commit / branch | #37 / `f1250fd38cd34cb52af70735be981b720d300a4c` / `feature/1.0.0-beta.4` |
| Workflow / job name | CI → `verify / Build & Test` (`ci.yml` calls `verify.yml`) |
| Failing step | Step 3 `Check release tooling and architecture`, 2026-11-15 07:09:48 UTC, < 1 s, exit 1 |
| Consequence | Set up JDK 21, Unit tests, Assemble debug APK, Lint all skipped; no test reports or APK artifacts |

The step runs three commands:

| Command | Result |
| --- | --- |
| `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` | 41 tests OK |
| `python3 .github/scripts/check_architecture.py` | **73 violations, exit 1** |
| `python3 .github/scripts/release_tools.py check --base-ref origin/main` | Not reached due to `bash -e`; passes locally (exit 0) |

`bash -e` aborts the whole step on the first non-zero exit, so the third line never ran and all later Gradle steps were skipped. The 41 tests come from several `test_*.py` files under `.github/scripts`; 7 test functions belong to the architecture script.

### 1.2 Direct cause

`check_architecture.py` is an all-or-nothing source-level check: any violation calls `sys.exit(1)`, with no concept of known existing debt. A "production dependency violation" is an out-of-bounds `project(":...")` declaration in `build.gradle.kts` outside `test`/`ksp`/`kapt` configurations; an "import violation" is a Kotlin import under `src/main` crossing a rule boundary.

It reported 73 violations (5 dependency + 68 import) across 4 modules. All 27 affected files come from import violations and belong to 3 modules; 2 more dependency violations come from `environment-runtime`, which has declarations only:

| Module | Dependency violations | Import violations | Typical symbols |
| --- | --- | --- | --- |
| `:data:agent` | 1 (→ `:data:modelaccess`) | 56 | `AgentLLMModelFactory`, `ModelConfig`, `ModelRuntimeMetadata`, `ToolLoadingMode`, realtime probes, vendor tools |
| `:core:designsystem` | 1 (→ `:domain:voice`) | 10 | `TtsVoice`, `VoiceCategory`, `VoiceGender` used by `VoicePickerPage` / `VoicePickerDialog` |
| `:data:voice` | 1 (→ `:data:modelaccess`) | 2 | `speechRequestBaseUrl` |
| `:environment-runtime` | 2 (→ `:core:security`, `:domain:environment`) | 0 | companion runtime APK dependency declarations |

The local working tree (with 30+ uncommitted voice changes) reports 74; the difference comes from changed imports in uncommitted files.

### 1.3 Root causes

1. **Stale rules**: `check_architecture.py` and `test_check_architecture.py` were last changed on 2026-10-04 (`0c0ca096`) and untouched for 42 days; both files have identical blob SHAs on `main` and the feature branch. Modules grew from 75 to 80 in the same period.
2. **New module not registered**: `6f787f44` (11-14) added `:environment-runtime`, a companion runtime APK with an independent UID. `allowed_dependency()` recognises only six layers (`app/domain/core/feature/data/plugins`); unknown layers fall through to `return False`, so its two dependencies on `:core:security` and `:domain:environment` are **guaranteed false positives**. The string `environment-runtime` appears in neither the script nor its tests.
3. **Approved exception not mirrored**: `AGENTS.md` ("Compose, UI, i18n, and Style") explicitly documents the `:domain:voice` api dependency of `VoicePickerPage` / `VoicePickerDialog` as the second accepted exception, and `AGENTS.md` ("Capabilities") already defines the role of `environment-runtime`. Governance approved or defined them; the checker never registered them.
4. **Real cross-layer coupling grew**: after November moved vendor model access into `:data:modelaccess`, `:data:agent` started importing its implementation types directly (`AgentLLMModelFactory` is a `class`; `ModelConfig` / `ModelRuntimeMetadata` are `data class`), producing 55+ same-layer imports. The stale comment in `data/agent/build.gradle.kts` still claims "runtime only depends on domain contracts", showing intent and implementation have diverged.
5. **CI coverage gap (amplifier)**: `ci.yml` triggers only on `push`, explicitly excludes `main` (`'!main'`), and has no `pull_request` trigger. `feature/1.0.0-beta.4` had no prior CI runs, so nothing between 11-11 and 11-14 was gated until this push settled the whole bill at once.

## 2. Analysis

### 2.1 Evidence and reproduction

See Appendix A. `gh run view` yields steps and logs; `git archive <sha> | tar -x` exports historical snapshots on which the same `check_architecture.py` is run, and violation sets are diffed commit by commit; `gh api` compares file SHAs across branches.

Running the **current script** against historical snapshots (retrospective simulation, not the CI records of those days; the `e377c1a0` row also corresponds to the last real successful CI on main):

| Commit | Date | Modules | Current script result |
| --- | --- | --- | --- |
| `e377c1a0` | 10-16 | 67 | Passed (last successful CI on main) |
| `b5afaaa0` | 10-31 | 75 | Passed |
| `8e48b0dd` | 11-11 | — | **First failure, 11**: `:core:designsystem → :domain:voice` |
| `6f787f44` | 11-14 | — | **72**: +61 (55 agent→modelaccess, 2 voice→modelaccess, 4 dependency declarations) |
| `e5b67f30` | 11-14 | — | 72 (parent of the failing commit; also fails) |
| `f1250fd3` | 11-15 | 80 | **73** |

This commit adds exactly one violation: `ConversationGenerateTitlePlugin.kt:17` importing `CONVERSATION_TITLE_METADATA_KEY`. Eight other changed lines merely swap `anthropic.llm.*` imports for `deepseek.llm.*`; both live in `:data:modelaccess`, so `forbidden_import()` classifies them identically and the count is unchanged.

**Causality**: the parent `e5b67f30` already produced 72 violations and exit 1 under the same script, so this commit is not the point where CI turned red. It did add one violation, which Step 1 must handle. Precisely: the PR #37 feature code is not the cause, but it is not entirely unrelated either.

### 2.2 Problems in the rules themselves

- `allowed_dependency()` expresses every constraint as a directory **layer** (six values); new or special-role modules always hit `return False`. The proposed **role** dimension in Step 2 (`contract`/`orchestrator`/`adapter`/`ui-kit`/`runtime`/`feature`) is a separate, finer-grained concept — not a rename of the six layers.
- `forbidden_import()` bans `core → domain.*` and `data → sibling data.*` outright, without distinguishing pure data models, interface contracts and implementation classes.
- All 41 Python tests pass, yet none covers `environment-runtime`, new dependency patterns, or the approved exception. Tests and rules froze together in October, so a green first CI step proves nothing about current rule validity.
- Errors are a single line (`forbidden import xxx`) with no rule id, rationale, or fix hint; humans and agents can only guess.

### 2.3 Scale assessment

The repository has 80 modules (`domain` 22 / `data` 24 / `feature` 16 / `core` 9 / `plugins` 6 / `app` 1 / `environment-runtime` 1 / `plugin-api` 1), 1212 tracked Kotlin files and roughly 157k lines. **The module scale is sound**: capability-based slicing is friendly to parallel Gradle builds, and the 73 violations are highly localised (4 modules, 27 files), not a systemic collapse.

What no longer fits is **the expressive power of the rules**: six directory layers describing 80 modules. The 24 `data` modules mix repositories, vendor adapters, orchestration, and protocol implementations; `core` mixes `designsystem` (Compose UI) with `common` (pure utilities); an independent-process APK has no role at all. The rules cannot distinguish these roles, so they also reject legitimate collaboration such as "orchestrator → adapter" and "UI component → pure display model".

### 2.4 Fit with agent-driven development

If "agent programming" means coding agents changing code at high frequency: **right direction, wrong implementation**.

- Right: a deterministic, machine-readable, sub-second hard gate that needs no Android SDK is exactly the feedback an agent needs after an edit.
- Conflict 1, **noise drowns the signal**: with 73 standing violations, an agent cannot tell whether its own edit broke anything; without delta detection there is no signal.
- Conflict 2, **not explainable**: no rule id, reason, or fix hint, so agents may work around the check by renaming packages, using reflection, adding suppression comments, or even editing the checker.
- Conflict 3, **too late**: CI-only, excluding main, with no PR trigger; local iterations get no feedback.
- Conflict 4, **implicit knowledge**: conventions live inside Python if/else branches, so an agent adding a module or dependency has no cue to sync the rules — `environment-runtime` is the proof.

If the phrase refers to the in-app Agent runtime architecture (the `:data:agent` module): a flat ban on "orchestration → adapter" blocks exactly the composition the agent needs; the right direction is depending on abstract contracts implemented by the adapter layer, not banning the dependency.

### 2.5 Risk note

Once the architecture check passes, CI will for the first time actually execute `jvmTest`, `:app:assembleDebug` and `:app:lintDebug` (the CI steps Unit tests / Assemble debug APK / Lint). Local work notes record 2 unrelated pre-existing failures in `:app:testDebugUnitTest` (`ManagedDirectoryArchitectureTest`, involving `AppNavigation.kt`, `CompanionEnvironmentRepository.kt`, `ModelAccessModule.kt`), not yet verified on CI. Expect "the next layer of failures appears after the gate turns green"; do not misclassify them as regressions from this fix.

## 3. Solution

### 3.1 Options

| Option | Effort | Pros | Cons | Verdict |
| --- | --- | --- | --- | --- |
| Blanket allow `data→data` / `core→domain` | 2 lines | Fastest | Gate effectively disabled; new violations undetectable | Not recommended |
| Refactor all 73 at once | 27 files, 4 modules | Cleanest architecture | Collides with uncommitted voice changes; large regression surface | Not as step one |
| Delete / skip the check | 1 line | Fastest | Loses deterministic architecture feedback | Forbidden |
| **Baseline + role registration + trigger fix → per-module paydown** | 4 files, then per-module PRs | CI recovers immediately, incremental signal preserved, debt repayable, no Kotlin changes | Baseline file must be maintained | **Recommended** |

### 3.2 Step 0 · Stop the bleeding (no Kotlin changes, revertible)

Changes touch 4 files and no business code; `git revert` restores the previous behaviour. **Order is mandatory**: register roles and exceptions first, then generate the baseline, otherwise the baseline freezes false positives that should not exist.

1. Update `.github/scripts/check_architecture.py`:
   - add `MODULE_ROLES`: `environment-runtime` → `role = runtime` (allow `:core:*` / `:domain:*`); `:core:designsystem` → reference the second `AGENTS.md` exception, allowing voice-picker files to import `domain.voice.model.*`;
   - load the baseline and fail only on **new violations** (`current result − baseline`); fewer baseline matches means debt repaid, so report it but do not fail;
   - attach a `rule_id` and a one-line fix hint to every error.
   After registration the count drops from 73 to **60** (removing 2 runtime dependency false positives, 1 designsystem dependency, 10 designsystem imports).
2. Add `.github/scripts/architecture-baseline.txt`: generate those 60 signatures **with the script from step 1**. Format `rule type | normalised path | target symbol`, **no line numbers**, so unrelated edits do not invalidate entries. Example:
   ```text
   dep|:environment-runtime|:core:security
   import|data/agent/src/main/.../AgentFactory.kt|github.ponyhuang.gimi.data.modelaccess.AgentLLMModelFactory
   ```
3. Update `.github/scripts/test_check_architecture.py`: add 4 test functions — baseline blocks only new violations, runtime role, approved exception, unregistered new module must fail. The 41 tests come from several `test_*.py` files (7 architecture test functions); the total becomes 45.
4. Update `.github/workflows/ci.yml`: add a `pull_request` trigger (or let `main` run `verify.yml` too), closing the "main and PRs are unchecked" blind spot. This is companion governance and does not affect turning the gate green.
5. Keep this document and its Chinese companion in sync; if rule semantics change, update the dependency-direction section of `AGENTS.md`.

### 3.3 Step 1 · Per-module paydown (separate PRs, shrink the baseline)

Step 0 already did the registration; this section handles only the **60 remaining baseline entries** and exception metadata, with no duplicate registration.

| Module | Classification | Action |
| --- | --- | --- |
| `:environment-runtime` | Registered in Step 0 | No further action |
| `:core:designsystem` | Registered in Step 0 | Add the owner / removal condition text required by `AGENTS.md` "Mandatory boundaries" (as a comment on the exception entry); no code change. **Do not move the voice models down** — this is an approved pure-display-model exception, and changing business code to satisfy an under-expressive rule is the wrong direction |
| `:data:voice` | 3 violations (1 → `:data:modelaccess` dependency + 2 `speechRequestBaseUrl` imports) | Move the config constant to `:core:common`, then remove the modelaccess dependency from `build.gradle.kts`; or register a narrow exception |
| `:data:agent` | The only coupling that genuinely needs architectural work | Either (recommended) move `ModelConfig` / `ModelRuntimeMetadata` DTOs into a contract module and extract an `AgentLLMModelFactory` interface into `:domain:modelaccess` or `:plugin-api`, keeping implementations in `:data:modelaccess` bound via Hilt; or, short term, register a temporary exception with owner and removal condition per `AGENTS.md` |

The `:data:agent` choice belongs to the project owner: contract extraction is cleaner but touches 20+ files; a temporary exception is faster but cements same-layer coupling.

### 3.4 Step 2 · Make the rules keep pace

- Replace `allowed_dependency()` if/else branches with a declarative `architecture.toml` (module → role + allowed dependencies + rationale + owner); an unregistered new module fails, turning "sync" into a required action.
- Emit structured JSON (`rule_id` / `file` / `line` / `reason` / `fix_hint`) so coding agents can read and auto-fix.
- Share one config between the local command and CI; move the check into the pre-commit stage or the end of an agent task, making "delta = 0" the only signal.
- Suggested roles: `contract` / `orchestrator` / `adapter` / `ui-kit` / `runtime` / `feature`; the cheapest variant is promoting contract modules such as `:plugin-api`.

### 3.5 Acceptance criteria

1. `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` green (45 tests).
2. `python3 .github/scripts/check_architecture.py` exits 0 on current sources and reports "60 baseline entries / 0 new".
3. Delta self-check: temporarily add `import github.ponyhuang.gimi.data.*` to a non-voice-picker file in `:core:designsystem`; rerunning the same command must exit 1 and list that entry; revert the edit afterwards.
4. `python3 .github/scripts/release_tools.py check --base-ref origin/main` passes.
5. Push to the branch to trigger CI and confirm Unit tests (`jvmTest`) / Assemble debug APK / Lint (`:app:lintDebug`) actually run for the first time; attribute any new failures separately.
6. Both language specs are in sync and the `AGENTS.md` index lists this document.

### 3.6 Minimal path for "just make CI green"

The only strictly necessary actions are steps 1 and 2 of 3.2: make `check_architecture.py` exit 0 on current sources. Steps 3–5 (tests, trigger scope, docs) are companion governance and may ship in the same or a later PR.

A green gate is not a green pipeline: see 2.5, `:app:testDebugUnitTest` has 2 unrelated pre-existing failures, so the first real unit-test run may still be red. Open a separate PR to fix the 3 files behind `ManagedDirectoryArchitectureTest`, or adjust that test after assessment; do not count it as a Step 0 regression and do not skip the unit-test step to force a green CI.

### 3.7 Execution record (2026-11-15)

Step 0 and Step 1 are fully landed; violations dropped from 73 (74 in the working-tree snapshot) to **6**:

| Item | Result |
| --- | --- |
| `check_architecture.py` | Adds `MODULE_ROLES` (runtime role), `APPROVED_DEPENDENCIES` / `APPROVED_IMPORT_PREFIXES` (the approved designsystem voice-picker exception), baseline-based incremental gating (regenerate via `--update-baseline`), and `ARCH001–006` rule ids with per-rule fix hints |
| `architecture-baseline.txt` | New file pinning the 6 remaining ADK-boundary violations; signatures carry no line numbers |
| `test_check_architecture.py` | 7 → 11 cases (runtime role, exception narrowness, baseline signatures and new-only gating, unknown layer rejected by default) |
| `.github/workflows/ci.yml` | Adds the `pull_request` trigger (push on `main` stays excluded) |
| `:domain:modelaccess` (new module) | `ModelConfig` / `ModelRuntimeMetadata` / `toRuntimeMetadata()`, the `AgentLlmModelSource` selection contract, `ToolLoadingMode` plus `DEFERRED_CATALOG` / `INLINE_DEFINITIONS` / `TOOL_SEARCH_NAME` / `CONVERSATION_TITLE_METADATA_KEY` constants, and the `isOfficialDeepSeekAnthropicEndpoint` gate |
| `:domain:voice` | New `VoiceRealtimeContracts.kt` (`VoiceAudioMarkers` / `VoiceSentenceBuffer` / `RealtimeFunctionCallSink`); `SpeechRequestEndpoints` moved down from `:data:modelaccess` (step B) |
| `:data:agent` | ~24 files switched imports; new `AgentModelRuntime` facade concentrates ADK `Model` creation; the vendor branch of `OfficialToolFactory` inverted into `VendorLocalToolBinder` in `:data:modelaccess`; probe / voice-support DI bindings moved back with their implementations |
| Remaining 6 | All are ADK type boundaries `:domain:*` cannot carry (`adk.Model` / `adk.Content` / `adk.BaseTool`): `AgentModelRuntime`, `VendorLocalToolBinder`, `VoiceLiveModelFactory` ×2, `RealtimeManualInputSink`, plus the Gradle edge. Registered as an exception in `AGENTS.md` "Mandatory boundaries" (owner + removal condition) and pinned in the baseline |

Local verification: `unittest` green; `check_architecture.py` exits 0 (81 modules, 6 known / 0 new); injecting an out-of-bounds import reports `2 new` and exits 1, restoring exits 0; `release_tools.py check` passes; `:app:compileDebugKotlin` compiles cleanly (Hilt graph intact). The first real CI unit-test run may still hit the 2 pre-existing failures from 2.5; handle per 3.6.

## 4. Decisions (executed 2026-11-15)

- `:data:agent → :data:modelaccess`: contract extraction plus a documented ADK-boundary exception; see 3.7 and `AGENTS.md` "Mandatory boundaries".
- Baseline expiry: no hard deadline; the file header states it may only be regenerated via `--update-baseline` and shrunk by fixing code, never hand-edited.
- `ci.yml`: `pull_request` trigger added; push on `main` stays excluded, release pipelines unaffected.

## Appendix A · Reproduction commands

Run from the repository root. `gh` must be authenticated with read access to `pony-huang/Gimi`. CI uses `python3`; on Windows use `python` if that is the only interpreter available.

```bash
# 1. Inspect the failed run, steps and logs
gh run view 43688678563 --repo pony-huang/Gimi --json status,conclusion,jobs
gh run view --log --job 132734476863 --repo pony-huang/Gimi

# 2. Verify the rules were not synced: cross-branch SHA and last change date
gh api repos/pony-huang/Gimi/contents/.github/scripts/check_architecture.py?ref=main --jq .sha
gh api repos/pony-huang/Gimi/contents/.github/scripts/check_architecture.py?ref=feature/1.0.0-beta.4 --jq .sha
git log -1 --format='%h %ad %s' --date=short -- .github/scripts/check_architecture.py

# 3. Reproduce the architecture and release-tool checks locally
python3 .github/scripts/check_architecture.py
python3 .github/scripts/release_tools.py check --base-ref origin/main
python3 .github/scripts/check_architecture.py 2>&1 | wc -l

# 4. Historical snapshots (the script locates its root via __file__, so any cwd works)
for c in e377c1a0 b5afaaa0 8e48b0dd 6f787f44 e5b67f30 f1250fd3; do
  d="/tmp/gimi-$c"; mkdir -p "$d"
  git archive "$c" | tar -x -C "$d"
  printf '%s ' "$c"
  python3 "$d/.github/scripts/check_architecture.py" 2>&1 | head -1
done

# 5. Diff violation sets between two commits
diff <(python3 /tmp/gimi-e5b67f30/.github/scripts/check_architecture.py 2>&1) \
     <(python3 /tmp/gimi-f1250fd3/.github/scripts/check_architecture.py 2>&1)

# 6. Scale statistics
git ls-files '*.kt' | wc -l
python3 -c "import re,pathlib; print('modules:', len(re.findall(r'include\\(\\s*\"(:[^\"]+)\"\\s*\\)', pathlib.Path('settings.gradle.kts').read_text())))"
```

## Appendix B · Rules and governance index

| Location | Contents |
| --- | --- |
| `.github/scripts/check_architecture.py` | `allowed_dependency()`, `forbidden_import()`, `imported_module()`, `cycles()`, `MODULE_ROLES`, `APPROVED_DEPENDENCIES` / `APPROVED_IMPORT_PREFIXES`, baseline-based incremental gating |
| `.github/scripts/architecture-baseline.txt` | Known violation signatures (no line numbers); regenerated only via `--update-baseline` |
| `.github/scripts/test_check_architecture.py` | 11 test functions: layer direction, domain forbidden prefixes, runtime role, approved exceptions, baseline increment, unknown layer rejected by default |
| `.github/workflows/verify.yml` | Order of the three step-3 commands and `bash -e` behaviour |
| `.github/workflows/ci.yml` | Push trigger (excludes `main`) + `pull_request` trigger |
| `AGENTS.md` | Module map, dependency directions, mandatory boundaries, designsystem exception, exception registration rule, bilingual spec policy |
| `spec/en/releasing.md` / `spec/zh/releasing.md` | Authoritative CI/CD and release documentation |
