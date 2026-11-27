# Development, Verification, and Release

English · [简体中文](../zh/releasing.md)

## Workflow responsibilities

Workflow files under `.github/workflows/` are the source of truth:

- `ci.yml` delegates development branch verification to `verify.yml`, which runs release-tool tests,
  architecture checks, JVM tests, debug App assembly, plugin compilation, and `app:lintDebug` on
  Ubuntu with JDK 21. See Day-to-day development below for trigger exclusions.
- `release.yml` runs only on manual dispatch with an existing tag (`workflow_dispatch`). It builds
  and validates signed App/plugin APKs from the exact tagged commit, uploads complete artifacts to
  a Draft, then publishes an immutable GitHub Release — prereleases automatically carry the
  prerelease flag. It does not publish to Google Play.
- `prepare_release.py` (run locally by the releaser) scaffolds the version PR: it writes
  `version.txt`, appends the `CHANGELOG.md` section, and increments `releaseVersionCode` in one
  commit on a `chore/release-*` branch. Version numbers are entered by a human; the script only
  executes (ADR-0001).
- App and plugins share `version.txt` and `gradle.properties`'s `releaseVersionCode` through
  `gimi.android.application`.

## Initial setup

The release baseline is calibrated to the latest stable GitHub release and recorded in
`version.txt` and `CHANGELOG.md`; `version.txt` is the single source of truth for the version name.
The release script downloads the main App APK of the highest published semver Release
to verify the increment that in-place upgrades require; it never relies on workflow run numbers.

No PAT is needed: `prepare_release.py` uses the local `gh` credentials of whoever runs it, and
`release.yml` uses the default `GITHUB_TOKEN`.

Configure four repository Actions secrets with the same production key as the distributed APKs:

- `RELEASE_KEYSTORE_BASE64`: base64 content of the keystore.
- `RELEASE_KEYSTORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: signing alias.
- `RELEASE_KEY_PASSWORD`: private key password.

The main branch rules require a pull request and forbid direct pushes. CI runs only on development
branch pushes and pull requests, so `chore/release-*` version-PR pushes do not produce an extra
push-run `Build & Test` check (the pull_request run covers it). Protect `v*` tags against updates
and deletion; tags are created locally by the releaser. A repository admin configures these
GitHub settings; editing the YAML does not enable them.

## Day-to-day development

1. Branch from main and use Conventional Commits such as `feat`, `fix`, and `perf`. With squash
   merges the PR title becomes the final commit title, so it must describe the complete user-visible
   change.
2. Compile the affected modules and run their behavior tests locally; assemble the debug app when
   resources, DI, or build configuration are involved. UI work follows the preview-first requirement
   in AGENTS.md.
3. Push a development branch to run release script tests, architecture checks, JVM unit tests,
   debug APK assembly, plugin compilation, and lint. Development branches are all branches except
   `main`, `chore/release-**`, `backup/**`, and `dependabot/**`. Local commits alone do not run CI.
4. Open the pull request with actual verification results. Main merges and tags do not trigger CI
   on their own; a pull request targeting main runs the `Build & Test` check (including the
   release-tooling `check` that validates the version PR).

## Version PR and changelog

`version.txt` is the only version name file; the app and every plugin read the same version name and
`releaseVersionCode` through the shared convention plugin. `version.txt` is the single version
source of truth — no tool may infer or overwrite the version number; it is either read from the
file or written exactly as the releaser typed it (ADR-0001).

Run `python3 .github/scripts/prepare_release.py <version>` from an up-to-date main checkout. The
script validates the version format and that it advances the current version, creates a
`chore/release-<version>` branch, updates `version.txt`, increments `releaseVersionCode` (main's
value + 1), and appends a `CHANGELOG.md` section generated from the Conventional Commits since the
highest reachable version tag (`feat` → 新功能与体验, `fix` → 修复; chores stay out and user-visible
details are edited by hand). It then pushes the branch and opens the version PR via `gh`.

Before merging the version PR:

1. Confirm `releaseVersionCode` in `gradle.properties` was incremented. Publication checks
   the install version increment required for in-place upgrades.
2. Rewrite the commit summaries into Chinese user-facing notes, merge duplicate entries, and add
   notes about permissions, plugins, protocols, and data resets. Edit the current version's section
   directly in CHANGELOG.md.
3. Wait for the PR's `Build & Test` check and finish the required physical-device installation, core
   feature, and plugin verification.
4. Merge the version PR into main.

The Release body is extracted from that version's CHANGELOG section at build time and replaces the
automatic body. The GitHub Release and the in-app update dialog read the same notes.

## Production build and publication

The release workflow accepts only `vX.Y.Z`, `vX.Y.Z-beta.N[.M...]`, and `vX.Y.Z-rc.N[.M...]`, checks
that the tag matches `version.txt` and that the tag points at the exact commit in main's history.
Publication steps check out the resolved commit SHA.

Publication builds the main app and five plugins without repeating full CI, retaining version,
signing, and artifact validation. A missing
production signing configuration stops the run; passwords travel through Gradle environment variables
and are never concatenated into commands. Every APK is checked for applicationId, versionName,
versionCode, and signing certificate, and the main app is checked for the installed version of the
previous published release.

Once every artifact passes, they are uploaded to the Draft Release, attachment names and sizes are
reviewed, and the release is published. Attachments include the arm64-v8a / universal app APKs, the
five plugin APKs, `plugin-subscription.json`, `SHA256SUMS`, and `release-metadata.json` carrying the commit, version, and
certificate fingerprint. The main app file name keeps its ABI suffix so the in-app update selection
works. R8 mapping is kept as an internal Actions Artifact for 90 days and should be archived elsewhere
before it expires when it is needed long term.

### Plugin subscription generation

Maintain plugin descriptions only in `data/plugin/src/main/assets/plugin_catalog.json`.
The same file is the app's bundled fallback and the release generator's input; do not maintain a
second catalog at the repository root. `release_tools.py check` validates the catalog in CI.
`pluginVersion` describes the plugin interface/behavior version (`AgentPlugin.version`), not the
APK's shared versionName/versionCode; update it deliberately with the plugin implementation.

The `package` command validates APK package names, versions and signing certificates before
generating `temp/release/dist/plugin-subscription.json`. It preserves editorial fields and fills
`apkUrl`, `sha256`, `sizeBytes`, `versionCode`, and `versionName` from the validated APK release. URLs point to the exact release tag,
including RC tags. Missing/empty/oversized APKs, incomplete or duplicate catalog entries, package
mismatches and invalid metadata stop publication. The generated catalog is included in
`SHA256SUMS` and the existing draft upload completeness gate. Build retries remove stale dist files.

Clients compare the subscription's `versionCode` with the installed package's Android
`longVersionCode`, requiring the same package name and a strictly higher code. `pluginVersion`
and version-name strings do not determine update availability. Missing APK version metadata
keeps installation available without advertising an update. Downloads must match the catalog's
package name and, when provided, exact `versionCode` before opening Android's installer;
Android enforces signing compatibility and handles the in-place replacement. Plugin enablement
and saved configuration survive replacement through the existing package refresh flow.

The app's default subscription is
`https://github.com/pony-huang/Gimi/releases/latest/download/plugin-subscription.json`.
Stable releases update this channel; RCs include their own catalog without becoming latest.
Until the first stable release containing this asset, clients fall back to the bundled catalog.
Existing saved subscription lists remain user-owned; use “Restore default source” to adopt the new
URL. Preview local generation using fixture APKs only through the release-tool tests; production
catalogs are generated from signed artifacts by CI, never committed or manually uploaded.

Stable releases become latest; betas and RCs are marked prerelease and do not affect the app's
`/releases/latest` update channel. After publishing, check download, in-place install, and core
features on a physical device; validate important versions through a prerelease first.

## Tagging, prereleases, and retries

After merging the version PR, tag the merge commit on main and dispatch the release. First run the
local pre-tag gate, which checks the tag format, the agreement between tag and `version.txt`, that
the tag exists and is part of main's history, the CHANGELOG section, and that the version exceeds
every published release:

```sh
python3 .github/scripts/release_tools.py pretag --tag v1.0.0-beta.6
git tag v1.0.0-beta.6
git push origin v1.0.0-beta.6
gh workflow run release.yml -f tag=v1.0.0-beta.6
```

Tags must be created locally after the version PR merge; the workflow's `push: tags` trigger was
removed so a tag push can never start a release on the wrong baseline (ADR-0001). Prerelease
numbering starts at 1; the stable release and every prerelease use their own incremented
versionCode. Prerelease tiers are ordered `beta` before `rc` before the stable release,
so `1.0.0-beta.1` precedes `1.0.0-rc.1`, which precedes `1.0.0`.

A failed release keeps the unpublished Draft. After fixing the environment or the secrets, re-run
Actions → Publish Android Release → Run workflow with the existing tag; the re-run does not build
other sources from a manually chosen branch and does not create a new tag. Finish re-running a
release before merging the next version PR, so that several unfinished releases never pile up.

Publications of the same version run serially and the Draft may overwrite incomplete attachments. A
published Release cannot have its attachments or body rewritten, and a re-run fails explicitly; a code
problem should ship as a new patch version. A tag cannot be moved and re-run; GitHub tag rules keep
tags immutable.
