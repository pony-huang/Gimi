# Spotify Web API integration reference

Reviewed: 2026-10-19. Audience: agents and developers changing `plugins/spotify`. Chinese companion: [中文说明](../zh/spotify-building-with-ai.md). This is a dated reference; re-fetch the relevant official documents before changing a contract.

## What the AI tutorial establishes

Spotify's guide is about using AI to develop integrations. It supplies an OpenAPI entry point and a review checklist because generated code may invent fields or reuse obsolete patterns. Treat generated code as a proposal: verify its API contract, authorization, lifecycle, failure behavior, and policy obligations before release. The guide does not authorize every AI product or data use. [Source: Building with AI](https://developer.spotify.com/documentation/web-api/tutorials/building-with-ai).

## Contract and endpoint freshness

The official [OpenAPI YAML](https://developer.spotify.com/reference/web-api/open-api-schema.yaml) was reachable and declared OpenAPI 3.0.3 on the review date. Read the operation's method, parameter location, limits, security scopes, success status, and response shape together. Current playlist item operations use `/playlists/{playlist_id}/items`; the older `/tracks` operations are marked deprecated. Library mutations use `PUT`/`DELETE /me/library` with required comma-separated `uris` in the query, maximum 40; `GET /me/library/contains` returns a Boolean array. Do not infer these contracts from similar endpoints. [Source: official schema](https://developer.spotify.com/reference/web-api/open-api-schema.yaml).

## Authorization and permissions

Use Authorization Code with PKCE for an Android client that cannot protect a client secret. Generate a cryptographically random verifier, derive the S256 challenge, and exchange the code with the verifier. Keep authorization state verification. [Source: PKCE tutorial](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow).

The AI tutorial permits ordinary Authorization Code when a secure backend holds the secret, limits Client Credentials to public data, rejects deprecated Implicit Grant, and calls for minimum scopes and secure token storage. [Source: Building with AI](https://developer.spotify.com/documentation/web-api/tutorials/building-with-ai). Map enabled tools to their actual operation scopes using the [scope reference](https://developer.spotify.com/documentation/web-api/concepts/scopes) and schema; do not assume every 403 means a missing scope.

Redirects must match registered URIs, with a dynamic-port exception for loopback IP literals. Use HTTPS otherwise. Explicit `127.0.0.1` and `[::1]` loopback addresses may use HTTP; `localhost` is prohibited. The dedicated redirect guide is more precise than the tutorial's simplified IPv4 example. [Source: redirect requirements](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri).

Access tokens last one hour; use returned `expires_in` for access-token expiry. Dashboard-app refresh tokens currently last six months from authorization, and refreshing does not extend that lifetime. Preserve the existing refresh token when the response omits a replacement. For `invalid_grant`, discard the invalid refresh credential and request authorization again rather than retrying it. PKCE refresh sends `client_id` without a client secret. [Source: refreshing tokens](https://developer.spotify.com/documentation/web-api/tutorials/refreshing-tokens).

## Rate limits and errors

Spotify measures the app's rate over a rolling 30-second window; quotas differ by mode and some operations have additional limits. On 429, respect `Retry-After` seconds and reduce request volume. [Source: rate limits](https://developer.spotify.com/documentation/web-api/concepts/rate-limits). The AI tutorial also recommends exponential backoff. [Source: Building with AI](https://developer.spotify.com/documentation/web-api/tutorials/building-with-ai).

Some successful operations have no JSON body; 204 explicitly has none. Parse according to each endpoint rather than forcing every response into an object. Preserve status and server error details while redacting credentials. A 403 is a refusal, and a 404 may be temporary or permanent. [Source: API calls](https://developer.spotify.com/documentation/web-api/concepts/api-calls). Project recommendation: bound retries, propagate coroutine cancellation, and consider duplicate side effects before replaying mutations after uncertain transport failures.

## Playback verification

`PUT /me/player/play` requires Premium and targets an active or specified device. Albums, artists, and playlists use `context_uri`; individual tracks use the `uris` array. Ordering alongside other Player operations is not guaranteed. [Source: Start/Resume Playback](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback).

Project recommendation based on the user's physical-device report: distinguish accepted control requests from observed playback, then query state with bounded polling. Report unavailable devices and unsupported device volume separately. The reported track-only non-start and stale progress are observations, not proof that track URIs are invalid or that a particular Spotify defect is established. An album context is a diagnostic alternative, not a universal API requirement.

## Content and product policy

The tutorial cautions against unnecessary content caching. [Source: Building with AI](https://developer.spotify.com/documentation/web-api/tutorials/building-with-ai). Apply the full [Developer Terms](https://developer.spotify.com/terms), rather than treating this short checklist as a complete retention policy.

The Developer Policy requires Spotify attribution and links back for metadata, artwork, and previews; it also requires account disconnection and deletion of associated personal data when disconnected. Its section III.3 prohibits voice-enabled Spotify control and voice assistants providing that functionality. This matters to future speech integration: the coding tutorial does not waive that restriction. Recheck policy applicability before enabling such a workflow. [Source: Developer Policy, sections I–III](https://developer.spotify.com/policy).

Spotify's API documentation also prohibits using Spotify Platform/content to train AI or machine-learning models or otherwise ingesting Spotify content into them. Do not interpret permission to use a coding assistant as permission to feed retrieved content into a model; evaluate the intended runtime data flow against the current terms. [Source: Get Track policy notes](https://developer.spotify.com/documentation/web-api/reference/get-track).

## Project review workflow

These are project recommendations, not extra Spotify API guarantees:

1. Fetch the current schema and relevant operation pages; record the review date and any disagreement between them.
2. Review the proposed endpoint/method, parameter placement, response shape, enabled scopes, redirect, credential owner, refresh lifecycle, and policy applicability before coding.
3. Keep SDK/HTTP/authentication details in the Spotify plugin boundary. Follow repository architecture, i18n, preview, and proportional-testing rules for host changes.
4. Validate request mapping, empty/object/array responses, invalid credentials, 403, 429, cancellation, and mutation retry decisions using fakes or a local HTTP boundary; use no live credentials in tests.
5. When playback changes, verify with a currently connected physical device and record request acceptance, observed state, device capability, and unresolved behavior separately.

## Existing implementation follow-up, not a completed audit

Inspection on the review date found the following review points; this document makes no implementation changes:

- `SpotifyAuth.kt` exchanges and refreshes using Basic authentication with a client secret, rather than PKCE. Its redirect parser accepts `localhost`, and its KDoc incorrectly describes that host as supported. Revisit these against the auth and redirect references above.
- `SpotifyAuth.kt` requests one scope list covering all tools. Review whether all those tools are enabled and necessary for each authorization scenario.
- `TokenStore.kt` uses plugin-private `SharedPreferences`. This establishes private app storage, not a complete assessment of encryption, backups, logs, or credential handling.
- `SpotifyApi.kt` already includes bounded 429 retries and 401 refresh handling, and playlist editing uses `/items`. Existing code is evidence to inspect, not a substitute for validating the next integration against fresh contracts.

Local evidence: [SpotifyAuth.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/SpotifyAuth.kt), [TokenStore.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/TokenStore.kt), [SpotifyApi.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/SpotifyApi.kt), [SpotifyPlaylistTools.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/tools/SpotifyPlaylistTools.kt).
