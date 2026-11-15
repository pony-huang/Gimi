[简体中文](../../zh/conversation/vendor-files.md) · English

# Vendor file gateway (multi-protocol Files API)

Reviewed on 2026-11-15 for developers.

This document holds the **operational constraints and recipes** for the vendor file capability (upload / list / retrieve / delete / download content, implemented 2026-11-15): terminology and scope, domain model rules, protocol dispatch rules, registry integration rules, and the mandatory steps when adding a vendor or a new protocol. Follow this document for future development.

## Terminology and scope

- **Vendor file gateway (`VendorFileGateway`)**: the single domain-layer entry point for the five file operations against vendor Files APIs (upload / listFiles / getFile / deleteFile / getContent).
- **File protocol (`FileProtocol`)**: the wire format of one vendor Files API family. Two families exist: `OPENAI_COMPATIBLE` (DeepSeek, OpenAI, and any vendor wire-compatible with the OpenAI files format) and `ANTHROPIC` (Claude and vendors compatible with its beta files format).
- **Protocol client (`FileProtocolClient`)**: a data-layer class implementing all five operations for one protocol family; per-vendor differences are only endpoint and credentials.
- **File-upload switch**: the instance-level toggle on a model service's detail page. Enabled → document capability is `FILE_REFERENCE` (vendor file references); disabled → `PATH_TEXT` (documents are not transferred; the model receives the local path as text). It decides *whether* a vendor offers Files at all; the registry below only answers *which protocol* to use.
- **Vendor file reference (`VendorFileReference`)**: the private `gimi-file://<vendor>/<fileId>` URI; fileId is the single server-side source of truth and session replay never re-uploads.

## Domain model constraints

> Decision rationale: evolving the upload-only gateway into a five-operation interface (instead of a parallel new interface) because one implementation class naturally shares auth, endpoint, and serialization config; splitting into two interfaces would double DI and dispatch code.

- Every `VendorFileGateway` method takes `vendor` / `endpoint` / `apiKey` as **call parameters**, not per-vendor injected beans. This is the foundation of "compatible vendors reuse the client with a different endpoint"; never convert it to per-vendor multi-bindings.
- The unified DTO `VendorFileObject` carries only the **intersection fields** of the vendors' FileObjects (id/filename/sizeBytes/mimeType/createdAt/expiresAt); vendor-specific fields (Claude's `scope`/`downloadable`, OpenAI's `purpose`) stay out of the model and are preserved verbatim in `raw: JsonObject`. Callers write one logic path; never let `if provider == "claude"` appear upstream.
- List pagination converges on **limit + opaque cursor**: implementations stuff their own cursor (OpenAI `last_id`, Claude `next_page`) into `nextCursor` verbatim, and callers pass it back unchanged — the per-vendor pagination protocols stay invisible. Ordering parameters are not part of the unified interface.
- The failure model shares the top-level enum `VendorFileFailureReason` (TIMEOUT / TOO_LARGE / NETWORK / VENDOR_ERROR / UNSUPPORTED_VENDOR / NOT_FOUND). "File does not exist" must map to `NOT_FOUND` (upstream cleans stale registry entries programmatically); never degrade it to `VENDOR_ERROR` + message-string parsing.
- `VendorFileUpload.purposeHint` is the nullable vendor purpose override (wins when non-null); when null each protocol client maps by MIME. Purpose mapping is an **implementation-private detail**: DeepSeek maps by MIME (PDF/Office → `rich_layout_recognition`, otherwise → `user_data`), OpenAI defaults to `user_data`, and Claude has no purpose concept and drops it. Upstream must never see vendor-private enums.
- `getContent` returns a **temporary file path on disk** (`VendorFileContentResult.Success.localPath`), not a ByteArray — Android is memory-sensitive and OpenAI allows files up to 512MB; the on-disk location is implementation-defined and callers release the file after consuming it.
- `UploadedFileRegistry.remove(vendor, fileId)` is silently idempotent when the entry is missing; see the registry rules below.

## Protocol dispatch and DI

- Dispatch converges in `DefaultVendorFileGateway`'s two-level vendor → protocol lookup: resolve the protocol family via `VendorFileProtocols.byVendor`, then pick the `FileProtocolClient` whose `protocol` matches from the client set. Unregistered vendors return `UNSUPPORTED_VENDOR`.
- Current registry: `deepseek`/`openai` → OPENAI_COMPATIBLE, `anthropic` → ANTHROPIC.
- DI shape: one `@Binds` binding for the gateway plus `@Provides @IntoSet` collection of protocol clients (`ModelAccessModule.kt`). Never regress to one binding per vendor.
- Shared support lives in `FileClientSupport`: Json config, OkHttp (150s timeout; vendors require completion within 120s), ASCII filename sanitization, and error detail extraction.
- The DeepSeek-specific `deepseek/file/` implementation **has been deleted** and absorbed into `OpenAiProtocolFileClient`; never recreate vendor-specific file implementation classes.

## Protocol differences

| Operation | OpenAI-compatible | Anthropic |
|---|---|---|
| Auth | `Authorization: Bearer` | `x-api-key` + `anthropic-version` |
| Upload purpose | required (`rich_layout_recognition`/`user_data`/6 enums, mapped per vendor) | none; optional `expires_in_seconds` |
| List pagination | `after` / `has_more` / `last_id` | `page` / `next_page` |
| Delete response | `{id, deleted: bool}` | `{id, type: "file_deleted"}` → deleted=true |
| Metadata fields | `bytes`, `purpose` | `size_bytes`, `mime_type`, `scope` (into raw) |
| Path building | append `/files` | official base URL lacks `/v1`; `filesUrl` inserts the `v1` segment |

The Anthropic client does **not** send the deprecated beta header; Claude Files state-machine fields (uploading/pending/processed) stay out of the unified model.

## Message-side reference passthrough

- The OpenAI-compatible converter (`ChatCompletionsConverters`) already passes `gimi-file://` references through as file_id parameters.
- The Anthropic converter (`ClaudeRequestConverter`) FileData branch accepts two sources: HTTP URI (original behavior unchanged) and `gimi-file://` vendor references — the latter resolves fileId and emits image → `ImageBlockParam` + `FileImageSource.of(fileId)`, PDF → `DocumentBlockParam` + `FileDocumentSource.of(fileId)`; any other MIME throws `UnsupportedOperationException`. Never require the HTTP-reference check for `gimi-file://` (it once threw outright, which crashed sending the moment the switch was enabled).
- The master switch for file upload is `FILE_UPLOAD_TEMPLATE_IDS` in `ServiceCatalogModels.kt` (currently `deepseek`/`anthropic`/`openai`); the detail-page switch, capability derivation (`withDerivedUploadCapabilities`), and send gating are all driven by it.
- With the switch disabled (`PATH_TEXT`) documents are never transferred with the request: `AttachmentResolvingModel` replaces the local document reference with a text part carrying the **absolute path** (plus display name and MIME); the agent decides whether to read it via the environment file-import tools (e.g. `import_file_to_environment`). Only a missing file degrades to the placeholder text. Historical local-document drafts under `FILE_REFERENCE` are delivered the same way.

## Registry integration

- The registry records an entry as soon as a chip upload succeeds (regardless of whether the draft is later sent); no file bytes are stored locally, no fileId mapping is cached, and no content deduplication happens — identical content uploads once per conversation.
- After `deleteFile` succeeds the gateway **synchronously removes** the matching `UploadedFileRegistry` entry (prevents ghost rows in the UI); on failure (any reason other than a successful delete) the entry is kept for user retry.
- The Room DAO (`uploaded_files`, PK fileId) provides query-by-vendor and delete-by-(vendor, fileId).

## Known pitfalls (must-read before rewriting or debugging)

- Multipart ordering: the purpose field must come **before** the file part; the reverse order is rejected. The builder must explicitly `setType(MultipartBody.FORM)` (the default `multipart/mixed` is rejected).
- ASCII filenames: non-ASCII characters in the Content-Disposition filename return 400; sanitize through `asciiFileName()`.
- HTTP 413 → `TOO_LARGE`; timeout is 150s.
- K2 compilation: constructor references like `::Class.Nested` are unsupported when the constructor has default parameters (use lambdas); a `@Serializable` class with a custom private companion throws `IllegalAccessError` via runtime reflection; same-package private DTOs with identical names across files trigger Redeclaration errors (this module prefixes them `OpenAi*`/`Claude*`).

## Mandatory steps when adding a vendor or protocol

1. **Vendor compatible with the OpenAI or Claude protocol**: add one vendor → protocol line to `VendorFileProtocols.byVendor`; declare the conversation connection default endpoint in the template (`ModelServiceTemplates.kt`); add the templateId to `FILE_UPLOAD_TEMPLATE_IDS` if the switch should appear. Zero client code.
2. **Brand-new protocol**: implement one `FileProtocolClient` (all five operations; unsupported ones throw `Failure(UNSUPPORTED_VENDOR)`) + one `@IntoSet` binding in `ModelAccessModule` + one registry line. Zero changes to existing code.
3. **New protocol clients must ship unit tests** (MockWebServer style, see `OpenAiProtocolFileClientTest` / `ClaudeProtocolFileClientTest`) covering at least: multipart field ordering, non-ASCII filenames, 413/404 mapping, cursor mapping, and unified-DTO raw passthrough.
