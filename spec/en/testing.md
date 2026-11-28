# Testing and verification

English · [简体中文](../zh/testing.md)

## Scope and validation

AGENTS.md owns the default validation matrix and required checks. This document explains test quality,
fixtures, and diagnostics; it does not add a full-suite gate to ordinary development.
Use focused tests for changed behavior and expand validation when module contracts, persistence,
security, generated code, release wiring, or uncertain results justify it.

## Test value and retention

Retain the smallest regression coverage that protects business behavior, an external contract, a risk
boundary, or a fixed defect. Explain the protected behavior when it is not obvious. Core business
refactors require characterization coverage; changes to core rules require owning-layer behavior tests.
Temporary hypothesis probes and development-only tests should be removed before committing or moved
under ignored `temp/`. Passing a temporary test alone does not justify retaining it.

Avoid tests that merely repeat the implementation, echo mocked values, or duplicate another layer's
static declarations without detecting an independent failure. Literal expectations, registry entries,
and one-line predicates can still protect protocols or permission boundaries; assess their contract
and failure value rather than rejecting them by syntax. Preserve existing meaningful tests and make
minimal fixture/expectation updates when behavior legitimately changes. Remove dependencies only
when they become unused.

## Portable and isolated fixtures

Tests must work from a clean checkout on local Windows and GitHub Actions Ubuntu, independent of
execution order, caches, credentials, or machine configuration. Use exact filename casing,
platform-neutral paths, explicit UTF-8 and Locale where relevant, and deliberate newline handling.
Account for filesystem replacement, file locking, and permission differences. Use isolated temporary
files and self-contained fixtures; align toolchains with CI when investigating platform differences.
Tests use fakes, MockWebServer, or mocked gateways, never live model APIs or real credentials.

## HTTP and paid integration boundaries

Independent HTTP or paid tool integrations need focused tests for request/response mapping,
credential sourcing, validation, failure/retry behavior, and cancellation. Add domain tests for
enablement and selection rules where applicable. Check the HTTP client's implicit retries when
duplicate requests affect cost or correctness. Give each behavior a primary test owner; add
cross-layer coverage only for wiring risks not already covered.

## Device-level checks and baseline profiles

`app`'s navigation smoke test (`AppNavigationSmokeTest`) drives every typed NavKey through the
production `AppEntryContent` dispatch inside `DebugTestHostActivity` (debug source set). Host
activities declared in the androidTest manifest run in the test APK's own process and are rejected
by `ActivityScenario`, which is why the host lives in the debug source set instead. Run it with
`ANDROID_SERIAL=<serial> ./gradlew :app:connectedDebugAndroidTest`; on devices whose policy blocks
new instrumentation packages (some Meizu builds report `INSTALL_FAILED_USER_RESTRICTED`), fall back
to an emulator and record it. Unregistered destinations fail fast; parameterized destinations use
placeholder ids and must render an empty/error state rather than crash.

Baseline profiles are generated on a connected device and committed at
`app/src/release/generated/baselineProfiles/baseline-prof.txt`. Regenerate after changing
`MainApplication`/`MainActivity` startup paths or first-screen composition:
`ANDROID_SERIAL=<serial> ./gradlew :app:generateReleaseBaselineProfile`. The `StartupBenchmark`
pair in `:baselineprofile` refuses emulators by design and needs a device that permits
instrumentation installs; a benchmark build type is profileable and signed with the debug key.

## Recurring failure patterns

CI and local runs repeatedly hit a small set of failure classes; classify the failure from the log
before acting. Transient infrastructure failures (dependency downloads rejected with HTTP 429, network
blips) abort the build before any test executes; artifact-resolution errors and status-code failures
mean rerun, not a code change. Real test failures need a root cause.

Timing-sensitive tests that race real async I/O against the test scheduler flake under CI load even
when green locally — a local MockWebServer handshake rejection can land before the test coroutine's
next dispatch. Prevent this class by giving production code a single failure-reporting source (one
channel or exception path owns failure classification; no second path may preempt it), by making test
ordering deterministic (consume the failure frame before triggering the send), by asserting failure
classification rather than timing windows, and by rerunning the fixed test several times locally:
one green CI run does not prove stability. Watchdog code that runs on a real clock (`Dispatchers.IO`)
inside `runTest` is the recurring variant; tests must not depend on short scheduling windows there.

JVM tests fail on unmocked Android APIs such as `android.util.Log` when in-flight code touches them;
check the owning module's Robolectric or default-value configuration and run the module before
committing.

Red tests must not linger unowned: fix them immediately or record their owner and introducing change.
CI green gates merges and releases, so an unowned red test poisons every later signal.

Keep the test workspace clean: tests that write files use temporary directories and restore anything
left at a fixed path during teardown; check `git status` before committing (ObjectBox JSON dumps have
bitten before).

Framework and platform pitfalls recur: MockK `every { }` property reads shadowed by same-name
parameters, domain modules run the `test` task (not `testDebugUnitTest`), and Robolectric library
modules need `isIncludeAndroidResources`. Pick the smallest validation target with the task name that
matches the module type.

## Diagnosis and reporting

Inspect existing logic and runtime evidence first. When the cause remains unclear, use focused tests
to reproduce behavior and validate hypotheses, including for non-core code. Diagnostic tests do not
require a TDD workflow; retain them according to their regression value.
Inspect the exact failing test and cause before rerunning. After a source or fixture fix, rerun the
same focused checks; report unrelated failures separately and reuse successful checks for unchanged
behavior. Stop retries when the same infrastructure cause repeats with no new evidence supporting
another attempt. Record the blocker and any remaining manual verification; successful compilation
cannot establish missing test or device results.

Report compilation, JVM tests, APK assembly, deployment, and device verification separately. Distinguish
active build time from IDE/dialog waits and infrastructure retries. A combined test-and-assembly
command's elapsed time is not unit-test execution time.
