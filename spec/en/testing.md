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
