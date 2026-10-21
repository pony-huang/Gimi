# Development, Verification, and Release

English · [简体中文](../zh/releasing.md)

## Initial setup

The release baseline is calibrated to the latest stable GitHub release, `0.10.2`, and recorded in
`version.txt`, `.release-please-manifest.json`, and `CHANGELOG.md`; the `0.6.x` values in older code
are no longer authoritative. `bootstrap-sha` points at `c53a311cfc95adc2553a610ccccf9e778c436f93`,
the v0.10.2 commit, and is used as the commit scan start when no historical Release is found. Later
version PRs continue from the released version and never republish v0.10.2.

The universal main App APK of GitHub v0.10.2 was checked: `versionName=0.10.2`, `versionCode=54`.
`gradle.properties` already matches that installed version, and the next version PR increments it to
55. The release script also downloads the main App APK of the highest published semver Release to
verify the increment that in-place upgrades require; it never relies on workflow run numbers.

In the repository's Settings → Actions → General, GitHub Actions may create pull requests and use the
write permissions the workflows declare. release-please uses the default `GITHUB_TOKEN`, so no PAT is
needed. Version PRs receive an install version allocation without dispatching CI. Tags created by
the bot are published explicitly through the reusable workflow.

Configure four repository Actions secrets with the same production key as the distributed APKs:

- `RELEASE_KEYSTORE_BASE64`: base64 content of the keystore.
- `RELEASE_KEYSTORE_PASSWORD`: keystore password.
- `RELEASE_KEY_ALIAS`: signing alias.
- `RELEASE_KEY_PASSWORD`: private key password.

The main branch rules require a pull request and forbid direct pushes. CI runs only on development
branch pushes, so version PRs do not produce a `Build & Test` check. If existing rules require that
check on every PR, adjust them to avoid blocking version PR merges. Protect `v*` tags against updates
and deletion while allowing the release bot to create tags. A repository admin configures these
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
   `main`, `release-please--**`, `backup/**`, and `dependabot/**`. Local commits alone do not run CI.
4. Open the pull request with actual verification results. PR events, main merges, version PRs,
   tags, and manual dispatches do not trigger CI. Prepare Release only maintains version PRs and
   starts publication.

## Version PR and changelog

`version.txt` is the only version name file; the app and every plugin read the same version name and
`releaseVersionCode` through the shared convention plugin.

release-please updates `version.txt`, `.release-please-manifest.json`, and `CHANGELOG.md`, classifying
commits under the English headings "Features / Bug Fixes / Performance Improvements". Internal refactors, documentation, CI, tests, and
maintenance stay hidden by default; user-visible changes of those kinds should be added by hand in the
version PR.

The bot assigns each version PR a `versionCode + 1` based on the version installed from main, commits
it to that branch without starting CI. Repeated updates of the same PR do not keep incrementing, and
release retries do not increment either, so day-to-day releases never edit `gradle.properties` by
hand.

Before merging the version PR:

1. Confirm the bot has updated `releaseVersionCode` in `gradle.properties`. Publication checks
   the install version increment required for in-place upgrades.
2. Rewrite the commit summaries into Chinese user-facing notes, merge duplicate entries, and add
   notes about permissions, plugins, protocols, and data resets. Edit the current version's section
   directly in CHANGELOG.md.
3. Confirm the development branch CI passes for the features being released and finish the required physical-device installation, core
   feature, and plugin verification.
4. Merge the version PR. release-please creates the `vX.Y.Z` tag and the Draft Release, and starts
   publication explicitly.

Later release-please commits may regenerate the current version draft, so do the final editing when
the version PR is ready to merge; if new features land afterwards, check the changelog again. Older
entries stay in CHANGELOG.md.

The Release body is extracted from that version's CHANGELOG section at build time and replaces the
automatic Draft body. The GitHub Release and the in-app update dialog read the same notes.

## Production build and publication

The release workflow accepts only `vX.Y.Z` and `vX.Y.Z-rc.N`, checks that the tag, the file version,
and the manifest agree, and that the tag points at the exact commit in main's history. Publication steps check out the resolved commit SHA.

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

Stable releases become latest; RCs are marked prerelease and do not affect the app's
`/releases/latest` update channel. After publishing, check download, in-place install, and core
features on a physical device; validate important versions through an RC first.

## Manual tags, RCs, and retries

A manual release also needs the version files, the manifest, an incremented versionCode, and the
matching CHANGELOG section committed and merged into main before the matching tag is pushed. For
example:

```sh
git tag v0.11.0-rc.1
git push origin v0.11.0-rc.1
```

RC numbering starts at 1; the stable release and every RC use their own incremented versionCode.
Automatic version PRs cover stable releases, while RCs ship through a manual version commit and tag.
After an RC, check the target version of the next release-please version PR; to pin it explicitly, use
a `Release-As: 0.11.0` footer in the merge commit.

A failed release keeps the unpublished Draft. After fixing the environment or the secrets, re-run
Actions → Publish Android Release → Run workflow with the existing tag; the re-run does not build
other sources from a manually chosen branch and does not create a new tag. Finish re-running a
release-please Draft before merging the next version PR, so that several unfinished releases never
pile up.

Publications of the same version run serially and the Draft may overwrite incomplete attachments. A
published Release cannot have its attachments or body rewritten, and a re-run fails explicitly; a code
problem should ship as a new patch version. A tag cannot be moved and re-run; GitHub tag rules keep
tags immutable.
