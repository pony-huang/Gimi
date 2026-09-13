# Chat Attachment Storage Pipeline

How user-attached images and files (camera photos, Photo Picker picks, SAF documents,
share-into-app images) flow through the app: where bytes live at every stage, how messages
reference them, how they are rendered, and when they are deleted.

Scope note: this document covers **chat attachments only**. The `:workfiles` capability
("work directories" for local document search) is a separate system — see
[Section 8](#8-related-but-separate-work-directories).

Last updated: 2026-09-13 (workspace rework: all attachments archive into the shared,
user-managed workspace; per-part history rendering resilience).

## 1. Overview

```
Camera (TakePicture)  ─→ FileProvider temp file (cache/shareable/chat/camera) ─┐
Photo Picker (≤3 images) ──────────────────────────────────────────────────────┤
SAF OpenMultipleDocuments ─────────────────────────────────────────────────────┤→ copy → cache/conversation/composer-drafts/<UUID>
Share-into-app (ACTION_SEND images) ───────────────────────────────────────────┘         (draft phase, cache, reclaimable)
                                                                       │ send
                                                                       ▼
              images:  decode → downsample ≤1280px → JPEG ≤512KB
                       → files/workspace/<显示名>-<sha256前16位>.<jpg>   (content-addressed, cross-session reuse)
              audio/docs: rename the draft file
                       → files/workspace/<显示名>-<随机12位>.<ext>       (zero copy)
                                                                       │
                                                                       ▼
              ADK Room session event stores Part(fileData{mimeType, displayName, fileUri = absolute path})
                                                                       │ model request
                                                                       ▼
              provider adapters (OpenAI / Claude) read the file and base64-encode per request
```

Design principles:

- **Copy-on-import, path-only metadata.** Picked URIs are never persisted with
  `takePersistableUriPermission`; bytes are copied into app-owned storage immediately, so
  revocation of the source URI cannot break a pending message.
- **One shared workspace, user-managed.** All attachments archive into the flat global
  workspace (`files/workspace/`), shared across sessions. Files are never deleted
  automatically — conversation deletion touches only the database; users manage files
  through the workspace management UI.
- **Readable archive names.** `<cleaned display name>-<suffix>.<ext>`: images use the
  sha256 prefix (content addressing), audio/docs use a random hex suffix (zero-copy rename
  preserved).
- **Render from paths, not base64.** Base64 exists only in outbound model requests.

Storage locations (declared via `ManagedDirectorySpec` in `core/storage` — workspace —,
`data/conversation/ConversationStorage.kt` and `feature/chat/ChatStorage.kt`):

| Path | Area | Lifecycle | Content |
|---|---|---|---|
| `cache/shareable/chat/camera/` | CACHE | TEMPORARY | camera temp files (FileProvider-visible) |
| `cache/conversation/composer-drafts/` | CACHE | TEMPORARY | composer draft attachments |
| `cache/conversation/drafts/` | CACHE | TEMPORARY | repository-side drafts (inline-data restore for editing) |
| `files/workspace/` | FILES | PERSISTENT | shared workspace: every new attachment archive, deleted only by the user |
| `files/conversation/attachments/<sessionId>/` | FILES | PERSISTENT | **legacy** pre-workspace archives; old files stay renderable, each session dir is removed when that conversation is deleted |
| `cache/shareable/previews/` | CACHE | TEMPORARY | copies handed to external viewers |
| `cache/shareable/playback/` | CACHE | TEMPORARY | playback copies for inline-only audio |

## 2. Entry Points

All entries converge in the composer's add-sheet (`ChatAddToChatSheet`), driven by
`ChatComposer.kt`:

- **Camera** — `ActivityResultContracts.TakePicture()`; the system camera writes into a
  FileProvider URI we supply (`feature/chat/CameraAttachment.kt`). Photos do **not** go to
  the system gallery; the URI target is their only copy.
- **Photo Picker** — `PickMultipleVisualMedia`, images only, max 3.
- **Files** — `ActivityResultContracts.OpenMultipleDocuments` (SAF); the mime filter is the
  union of the current model's `audioInput` / `documentInput` capabilities, so unsupported
  types are not selectable at all.
- **Share-into-app** — `ACTION_SEND` / `SEND_MULTIPLE` image URIs, resolved by
  `SharedMediaIntent.kt` and fed into the same `acceptSelection()`.

Every URI passes through `importDraftAttachment()` and then `mergeAttachmentSelection()`
(`AttachmentSelectionPolicy.kt`: dedup, single category per message, max 3), followed by a
mime check against the model's advertised capabilities.

## 3. Draft Phase (cache)

`importDraftAttachment()` (`feature/chat/DraftAttachmentStorage.kt`) copies the source bytes
into `cache/conversation/composer-drafts/<UUID>` and returns a lightweight
`DraftAttachment(reference = absolute path, displayName, mimeType, sizeBytes, category)`.
Bytes are deliberately excluded from Compose saved state and Room rows.

Camera temp files (`attachment_*.jpg` under `cache/shareable/chat/camera/`, exposed via
FileProvider so the camera app can write into them) are deleted immediately after a
successful import, on cancel, and on composable disposal.

The composer renders draft thumbnails by manual sampled decode (`decodeSampledBitmap`, EXIF
rotation aware) with explicit placeholder/error states.

## 4. Send Preparation (archive)

`PrepareChatTurnUseCase` calls `ChatAttachmentRepository.read(sessionId, drafts)`
(`AndroidChatAttachmentRepository.kt`), on `Dispatchers.IO`:

**Images** — decoded, downsampled to ≤1280px on the long edge, EXIF-rotated, re-encoded to
JPEG (quality 85→45 in steps of 10 until ≤512KB, up to 4 scale retries). The re-encoded
bytes are written to `files/workspace/<显示名>-<sha256前16位>.<ext>` (staged as a uniquely
suffixed `.tmp` then atomically renamed). Identical images with the same display name reuse
the existing file across sessions. All EXIF (including GPS) is stripped — a privacy
benefit. This is the only path that produces *new* bytes. The attachment id remains the
full stable content hash.

**Audio / documents** — zero-copy archive (`archiveDraftAttachment`, introduced by
`98e5ba86`): the draft file is **moved** (rename) into the workspace as
`<显示名>-<12位随机后缀>.<ext>`; a name collision regenerates the suffix.

- Extension is preserved in the archive name; consumers such as the Zhihu upload bridge
  infer Content-Type from the file name.
- Guard (dual root): if the draft reference already points into the workspace, or into the
  legacy per-session archive (`files/conversation/attachments/<sessionId>/` — the
  edit-resend path on old conversations), the file is referenced as-is. Renaming it would
  dangle the path recorded in earlier history events.
- A `length == sizeBytes` check is kept to reject drafts that changed between import and
  send.
- `renameTo` failure (cross-volume edge cases) falls back to copy + delete, costing the
  same as the old behavior.
- Per the interface contract (`ChatAgentRepository.kt`), image drafts survive `read()`;
  audio/document drafts are consumed by the move, and a later `deleteDrafts()` safely
  skips them.

## 5. How Messages Reference Attachments

Sent attachments live inside ADK Room session events (the `google-adk-kotlin` session
store) as `Part(fileData = FileData(mimeType, displayName, fileUri = absolute local path))`.
Storage holds **path strings, never base64**. `FileAttachment` (domain model) treats
`payloadReference` (the absolute path) as the primary source of truth; `inlineData` is
populated only for attachments restored from an ADK `inlineData` part.

History restore: `EventMapper.Part.toFileAttachment()` maps `fileData` back to
`FileAttachment.fromFile` **without reading bytes** — the id is derived from the file name
(the content hash for images, a display-name-based name for audio/documents) and the size
from `File.length()`. A missing payload file degrades per part into
`FileAttachment(isMissing = true)` instead of throwing; the UI renders a "文件已丢失"
placeholder, so one lost file can never blank a whole session (the `loadMessages`
catch-all is now a last-resort only). Missing attachments are dropped from retry/edit
resends (`PrepareChatTurnUseCase`, `ChatViewModel.editFailedTurn`).

Before each model request, `AgentChatRunner` also appends an `attachmentPathManifest` text
part listing the local paths, for tools that take path arguments (e.g. `read_local_file`,
plugin upload bridges).

## 6. Rendering

Rendering never base64-encodes; that happens only in the provider adapters
(`data/agent/model/Openai.kt`, `Claude.kt`) at request time.

- **Sent images** — Coil `SubcomposeAsyncImage` with model = `payloadReference` file (or
  `inlineData` bytes for inline-restored attachments); Coil owns background decode,
  downsampling, and caching. A load failure renders an explicit error placeholder;
  `isMissing` attachments render a "文件已丢失" placeholder tile without preview access.
  Full-screen preview uses a zoomable Coil dialog keyed by the attachment id.
- **Documents** — rendered as an icon + display-name row without reading content. Tapping
  copies the archived file to `cache/shareable/previews/<id>-<name>` and opens it in an
  external app via FileProvider content URI + `ACTION_VIEW` (`ChatRoute.kt`). Missing
  documents render as a disabled row.
- **Audio** — `MediaPlayer` plays the archived path directly; inline-only audio is first
  copied to `cache/shareable/playback/`. Missing audio renders as a disabled row.

## 7. Lifecycle & Cleanup

- **Drafts** are deleted under a directory fence (the file's parent must equal a draft
  root, so workspace files can never be removed by draft cleanup): on removal,
  replacement, leaving the composer, and after send ACCEPTED.
- **Workspace payloads** are the user's assets: they are never deleted automatically — not
  by conversation deletion, not by the manual "clear reclaimable storage" action
  (PERSISTENT lifecycle). The only deletion path is the user's explicit action in the
  workspace management UI (`:data:workspace` guards deletes to the workspace root).
- **Legacy session directories** (`files/conversation/attachments/<sessionId>/`) belong to
  pre-workspace data only; deleting such a conversation removes its legacy directory, so
  the legacy tree shrinks naturally.
- **Deletion guards**: conversations that are currently generating or are the active
  session cannot be deleted.
- Backup rules exclude both attachment trees from cloud auto-backup
  (`backup_rules.xml` / `data_extraction_rules.xml`); device-to-device transfer still
  carries them.

## 8. Related but Separate: Work Directories

The `:workfiles` capability is unrelated to chat attachments. The user authorizes a SAF
tree (`OpenDocumentTree` + `takePersistableUriPermission`) for **local document search**;
files there are searched in place via SAF queries, never copied, and the configuration is
a JSON DataStore (`WorkDirectoryConfigStore`). Do not mix the two systems.

## 9. Known Limitations

1. **No orphan GC — by design.** Crash-orphaned workspace files (e.g. a crash between
   "archive persisted" and "event written") simply become user-visible workspace files;
   the workspace UI is their cleanup path. No reference-count subsystem is needed.
2. **Per-request re-encoding.** Every turn re-reads and base64-encodes every historical
   attachment (linear CPU/memory growth with conversation length).
3. **Backup rules cover attachments only.** `allowBackup="true"` remains, but both
   attachment trees are explicitly excluded from cloud backup. The remaining payload
   (databases, preferences) still goes to the 25MB auto-backup by default.
4. **Images are always re-encoded.** PNG transparency is lost (black background), animated
   GIF/WebP become a static frame, and edit-resend cycles add a JPEG generation loss.
5. **Move-archive trade-offs** (zero-copy rename): identical documents/audio do not
   deduplicate (random suffix per archive), and a cancellation between prepare and
   ACCEPTED leaves the composer draft reference dangling (worst case: one orphan file).
6. **Same content, different names duplicates images.** Image content addressing includes
   the display name, so re-sending the same picture under a different name writes a second
   copy (accepted trade-off for readable archive names).
