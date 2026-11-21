# NetEase Cloud Music OpenAPI Usage Specification

[简体中文](../../zh/plugin/netease.md) · English

> For agents: for any music operation — which endpoint, which params, what returns, what to do on failure.
> Actionable rules only. Signing / common params / error codes follow the official developer docs (Appendix B);
> the CLI surface follows `@music163/ncm-cli` 0.1.7. Endpoint lists are a stale snapshot — verify in the developer docs before use.

## 0. Six rules

1. **Two call paths**: prefer `ncm-cli` when a shell is available (it handles signing, `device`, token refresh); use raw HTTP (§3) only for server integration or endpoints the CLI lacks.
2. **Two ID kinds**: encrypted ID (32-char hex) in every API parameter; plain numeric ID for `music.163.com` links, app deep links, and `ncm-cli play --original-id`. Never mix them.
3. **`accessToken` required on every endpoint** except anonymous login, QR-code key, and `grantCode` exchange; QR-status polling must use an **anonymous** token. Get a token before business calls.
4. **Signature covers all non-empty common params except `sign`**: sort by name, join `k=v&...`, RSA-SHA256, base64 (§3.4).
5. **`bizContent` and `device` are JSON-serialized strings**, not nested objects.
6. **Gateway authorizes per appId per endpoint**: `code 300/301` means no permission, not a bad param; retrying is useless — switch appId or request access. The manifest endpoint is gated too.

## 1. Path choice

| | `ncm-cli` (preferred with shell) | Raw HTTP OpenAPI |
|---|---|---|
| Entry | `ncm-cli <command> [args]` | `https://openncm.music.163.com/openapi/...` |
| Signing / device / token | handled by CLI | implement yourself (§3) |
| Command source | `ncm-cli commands` (static + manifest) | §4–§5 + developer docs |
| Best for | search, playback, control, playlists, cloud upload, TUI | server integration, batch, endpoints CLI lacks |
| Needs | Node.js ≥ 18, appId/privateKey, login | appId/privateKey (PKCS#8 PEM), accessToken |

Same protocol underneath: dynamic CLI commands become the §3 HTTP requests.

## 2. Credentials

| Credential | Purpose | Source |
|---|---|---|
| `appId` | App ID; common param on every request | developer console |
| `privateKey` | RSA key for local signing only, never sent | console, PKCS#8 PEM |
| `appSecret` (`clientSecret`) | Refresh an accessToken | console |
| `accessToken` | Login state; required except §0.3 | §4 |

```bash
npm install -g @music163/ncm-cli
ncm-cli --version                          # Node.js >= 18
ncm-cli config set appId <APP_ID>
ncm-cli config set privateKey <PEM_path_or_text>
ncm-cli config set player mpv              # or orpheus (macOS only)
ncm-cli login --check
ncm-cli login --background                 # hand the QR link to the user
```

- `privateKey`: path → read file; otherwise PEM text. Env vars also work: `NETEASE_APP_ID`, `NETEASE_PRIVATE_KEY`, `NETEASE_APP_SECRET`.
- Playback needs `mpv` (`mpv --version`); `ncm-cli config get player` shows the choice.
- appId must be granted each target endpoint with encryption configured, or every data endpoint returns `300/301`.

## 3. HTTP protocol (self-built clients)

- Base URL: `https://openncm.music.163.com` (CLI 0.1.7); docs use `https://openapi.music.163.com` — same paths/params, switch only on a 404 for the target path.
- **GET**: all common params in the query string, `URLSearchParams`-encoded. **POST**: the **whole common-param object** as JSON body (`Content-Type: application/json`), not just `bizContent`.
- `GET/POST` endpoints: default to GET; POST only for writes or when the card says so.
- Response JSON `{code, message, data, subCode}`: **only `code === 200` succeeds**. Lists usually in `data.records` + `data.recordCount`, sometimes bare `data` / `data.tracks` — follow the card.

Common params: `appId`, `signType`=`RSA_SHA256`, `timestamp` (ms, within 5 min of server time), `device` (JSON string), `bizContent` (JSON string, `{}` if empty), `accessToken` (per §0.3), `sign` (§3.4).

`device` (JSON string) fields: `deviceType`=`openapi`, `os`=`ncmcli`, `appVer`=`0.1.7` (`x.x.x`), `channel`=`ncmcli`, `model`=`Windows_x64_cli`, `brand`=`ncmcli`, `osVer`=`10.0.26300`, `clientIp`=real public IP (else `2511`), `deviceId`=`ncmcli_<rand>` (≤64 chars, no `= ; space + |`, bound to the anonymous token, persist it), `netStatus`=`4g` (`wifi/2g/3g/4g/5g`), `flowFlag`=`init`. These values are platform-assigned; if unknown, use these verified CLI 0.1.7 values.

Headers: `User-Agent: ncm-0.1.7`, `Referer: https://music.163.com/`, `Content-Type: application/json` (POST only), `X-User-Input: <encodeURIComponent(summary)>` (§6).

### 3.4 Signing algorithm

1. Common params except `sign`, drop `null`/`undefined`/empty; only common params are signed (not headers).
2. Stringify values (`device` / `bizContent` stay as serialized JSON text, no extra escaping).
3. Sort by name, ASCII ascending.
4. Join `k=v&...` with **no URL encoding**.
5. RSA-SHA256 with PKCS#8 private key → **base64** → `sign`.
6. GET: URL-encode the query afterwards; POST: put `sign` in the JSON body.

```js
const canonical = Object.entries(params)
  .filter(([k, v]) => k !== "sign" && v != null && v !== "")
  .sort(([a], [b]) => (a < b) - (a > b))
  .map(([k, v]) => `${k}=${v}`).join("&");
const sign = crypto.createSign("SHA256").update(canonical).sign(privateKeyPem, "base64");
```

Python: same canonical string, then `base64.b64encode(key.sign(canonical.encode(), padding.PKCS1v15(), hashes.SHA256())).decode()`.

Minimal end-to-end example (anonymous login → search → detail) is omitted; assemble calls from §3.1–§3.4 + §4–§5. `clientIp` must be your real outbound IP or you get `2511`.

## 4. Auth & token lifecycle

Order: anonymous login (guest) → QR login for user assets → refresh on expiry → re-login if refresh fails.

| Endpoint (method) | bizContent | Returns / rules |
|---|---|---|
| `/openapi/music/basic/oauth2/login/anonymous` (POST) | `{"clientId":"<appId>"}`, no token | `data.accessToken` is a **string**, never expires; also refreshToken/scopes. One `deviceId` → one fixed token, cache it; changing it = new user. Guest reads content + plays, no user assets |
| `/openapi/music/basic/user/oauth2/qrcodekey/get/v2` (GET) | `{"type":2,"expiredKey":"300"}`, no token | `qrCodeUrl` (reserve 8–512 chars), `uniKey`; 5 min, single use |
| `/openapi/music/basic/oauth2/device/login/qrcode/get` (GET) | `{"key":"<uniKey>","clientId":"<appId>"}` | **anonymous token only** (real token → 400 `device has login success`). `data.accessToken` is an **object**: new token at `data.accessToken.accessToken`. Status 800 expired / 801 waiting / 802 authorizing / 803 success / 804 error; poll 2–3 s to 803 / 800 / 804 or 5-min timeout; 804 → fresh QR |
| `/openapi/music/basic/user/oauth2/token/refresh/v2` (GET) | `{"clientId","clientSecret","refreshToken"}` | AT 7 d, RT 20 d, renewed together; `clientId` must match the issuer appId; anonymous cannot refresh (1406) |

Other: `/openapi/music/basic/user/oauth2/token/get/v2` (grantCode→token, no token), `/openapi/music/basic/user/info/bytoken/get/v2` (POST), `/openapi/music/basic/do/noting` (GET, credential check), `/openapi/music/basic/user/profile/get/v2` (§5.3).

**Token errors**: confirm expiry via `message` (§8.2); refresh only real-user tokens, go straight to QR login for anonymous; refresh failure or `1407`/`1408` → re-login. Store tokens as unbounded strings (≥512 chars).

## 5. Endpoint cards

IDs in params are always encrypted; cards need `accessToken` unless noted; **profile / Liked Songs, playlist writes, and like ops need a real-user token**. No CLI command shown → endpoint is delivered by the server manifest (§5.6).

### 5.1 Search & discovery

| Intent | Endpoint | Key bizContent |
|---|---|---|
| Search songs | `/openapi/music/basic/search/song/get/v3` | `keyword`, `limit`, `offset` |
| Search albums | `/openapi/music/basic/search/album/get/v2` | `keyword`, `limit`, `offset` |
| Search artists | `/openapi/music/basic/search/artists/get/v2` | `keyword`, `limit`, `offset` |
| Search playlists | `/openapi/music/basic/search/playlist/get/v2` | `keyword`, `limit`, `offset` |
| Playlists by tag | `/openapi/music/basic/search/playlist/bytag/get/v2` | `tag`, `limit`, `offset` |
| Combined search | `/openapi/music/basic/complex/search` | `keyword` |
| Trending keywords | `/openapi/music/basic/search/hot/keyword/get/v2` | `limit` |
| Suggestions | `/openapi/music/basic/search/suggest/keyword/get/v2` | `keyword` |

- `search/song/get/v3` (GET/POST; CLI `search song --keyword "x" --userInput "..."`) `{"keyword","limit"≤100,"offset"≥0,"qualityFlag"}` → `data.records` + `data.recordCount` (bare array possible). Result `id` is encrypted. Use v3, not legacy v2.
- `complex/search` `{"keyword","identityFlag","subCountFlag","qualityFlag"}` → `data.{songs,artists,albums,playlists,voicelist,bestMatchResources,bestMatchResourceTypes}`. Use for vague / multi-type intent.
- Other search limits: album `limit` ≤300 (100 recommended), artist flags default false, bytag `limit` ≤500, hot has no `keyword`, suggest is for autocomplete only.

### 5.2 Songs, lyrics, playback

- `song/detail/get/v2` (GET; CLI via play) `{"songId","withUrl":true,"bitrate":320,...}` → `playUrl`, `playUrlExpireTime`, `br`, `gain`/`peak`, `openApiTraceInfo` (pass through for preview reporting). Check `visible`/`playFlag` first — never play when false. `bitrate` tiers 128/320/999/1999 (not bps); omit = best playable. Optional `effects` eac3/ac4, `level` vivid/sky/jymaster/jyeffect (sky needs `immerseType` c51/ste/aac), `extFlags` (JSON string).
- `song/playurl/get/v2` `{"songId","bitrate"}` → `data.url` (detail calls it `playUrl`); URL only, no metadata.
- `batch/song/playurl/get` `{"songIds":[…≤500],"bitrate","effects"}`.
- `song/lyric/get/v2` `{"songId"}` → `lyric` (LRC), `transLyric`, `txtLyric`, `romalrc`; hide lyrics if `noLyric`/`pureMusic`; `transLyric` may be null. `song/lyric/word/by/word/get` → head catalog only, fall back to line-by-line.
- `play/data/record` (POST; CLI reports automatically) — report twice: `startplay` then `play` (end payload must explicitly set `action":"play"`). Pass through `alg` from upstream and `openApiTraceInfo` from detail; use `alg:"search"` when no upstream value. Fields: `id` (enc; podcast `encVoiceId`), `action`, `type` song/dj, `bitrate` 128/320/999/1999/3999/4999/4099/2999/5999 (last five report-only; podcasts 128), `file` 0 downloaded/1 local/2 cached/3 cloud/4 online (4 if unsure), `startLogTime` ms, `time` listened seconds (>0, ≤duration, excluding pauses), `end` playend/interrupt/exception/ui (playend if unsure), `sourceId`/`sourceType` (list/album/dailySongRecommend/userfm/search/djradio), `isAudition` 1/2, `auditionStart`/`auditionEnd`, `categoryId`/`ext`.

### 5.3 User & playlists

| Read endpoint | bizContent | Returns |
|---|---|---|
| `/openapi/music/basic/user/profile/get/v2` | `{}` | `id`, `nickname`, `avatarUrl`, `gender` (0 unknown/1 male/2 female), `signature`, `redVipLevel`, `vipDetail` |
| `/openapi/music/basic/playlist/star/get/v2` | `{}` | Liked Songs object (`id`, `name`, `trackCount`, `coverImgUrl`); preference-analysis entry |
| `/openapi/music/basic/playlist/created/get/v2` | `{"limit":100,"offset":0}` | my created; `recordCount`+`records`, `limit`≤500 |
| `/openapi/music/basic/playlist/subed/get/v2` | `{"limit":100,"offset":0,"originalCoverFlag":false}` | my subscribed; `originalCoverFlag` = watermark-free |
| `/openapi/music/basic/playlist/detail/get/v2` | `{"playlistId":"<encId>"}` | detail; optional `originalCoverFlag`/`newCoverFlag` |
| `/openapi/music/basic/playlist/song/list/get/v3` | `{"playlistId":"<encId>","limit":30,"offset":0,"qualityFlag":true}` | tracks; `limit`≤500, 30 recommended |
| `/openapi/music/basic/playlist/song/list/get/v5` | same | `data.tracks` + playlist metadata |

`specialType`: 0 normal / 5 Liked / 10 pinned / 20 tail / 100 official / 200 video / 300 shared. Liked tracks carry `extMap.addTime` (like ms) and `songTag[]` (genre), usable for preferences.

| Write endpoint | bizContent | Effect |
|---|---|---|
| `/openapi/music/basic/playlist/create` | `{"playlistName":"Running"}` | CLI `playlist create --playlistName "x" --userInput "…"`; HTTP uses manifest `bodyParams` |
| `/openapi/music/basic/playlist/song/batch/like` | `{"playlistId":"<enc>","songIdList":["<enc>"]}` | add tracks |
| `/openapi/music/basic/playlist/song/batch/delete` | same | remove, ≤500, creator only |
| `/openapi/music/basic/playlist/song/like/v2` | `{"songId":"<enc>","isLike":true}` | like/unlike (`false` removes) |
| `/openapi/music/basic/playlist/sub/v2` / `unsub/v2` | `{"id":"<enc>"}` | subscribe / unsubscribe |

**After create**: take the new encrypted `playlistId` from `data`; if absent, call `created/get/v2` for the newest. Plain numeric `originalId` for links comes from `detail/get/v2` or `created/get/v2` — if unavailable, do not invent a link. **Verify writes**: re-fetch `detail/get/v2` and confirm `data.trackCount` rose by the added count.

### 5.4 Albums, artists, charts

| Endpoint | bizContent | Returns / rules |
|---|---|---|
| `/openapi/music/basic/album/detail/get/v2` (POST) | `{"albumId":"<enc>","descFlag":true}` | detail; `descFlag` adds description |
| `/openapi/music/basic/album/song/list/get/v3` | `{"albumId":"<enc>","limit":30,"offset":0}` | album tracks |
| `/openapi/music/basic/artist/detail/get/v2` | `{"artistId":"<enc>","extFlag":true}` | detail; optional `personalFlag`/`creatorRoleFlag`/`subCountFlag`/`identityFlag` |
| `/openapi/music/basic/artists/hotsong/list/get/v2` | `{"artistsId":"<enc>","top":50,"qualityFlag":true}` | hot tracks; param is `artistsId` (plural), `top` ≤60 |
| `/openapi/music/basic/toplist/get/v2` | `{}` | charts; `category` OFFICIAL/FEATURE/MUSIC_STYLE/GLOBAL/MORE/LANGUAGE |

### 5.5 Utilities & files

| Endpoint | bizContent | Purpose |
|---|---|---|
| `/openapi/music/basic/common/middle/short/url` (POST) | `{"sourceUrl":"<url>","expireTimeSeconds":1296000}` | short URL in `data.shortUrl`; default 15 days |
| `/openapi/music/cloud/file/upload/check`, `/upload`, `/upload/status`, `/download`, `/openapi/whale/nos/upload/token`, `/token/docid` | CLI-delivered | cloud upload/download; file bytes go directly to `wanproxy.127.net` / `wanproxy-web.127.net` |

Cloud params come from the CLI/manifest — use `ncm-cli cloudupload --help`, do not assemble manually.

### 5.6 Dynamic command manifest

- `POST /openapi/v1/ncm/cli/manifest`, `{"cli_version":"0.1.7","cached_versions":{}}`, needs `accessToken`; gated by appId authorization.
- Returns the command tree, cached at `~/.config/ncm-cli/cache/manifest.json`. Exact endpoints/params for search, lyrics, note publishing, playlist creation come from here; `ncm-cli commands` shows static + manifest commands.

## 6. CLI command surface

### 6.1 Static commands

```bash
ncm-cli --help | commands | <command> --help   # list / tree / exact params (never guess)
ncm-cli configure                              # setup wizard
ncm-cli config get <key> | config set <key> <value>
ncm-cli login --check | login --background     # background QR → hand link to user
ncm-cli search song --keyword "xxx"
ncm-cli playlist create --playlistName "xxx"
ncm-cli play --song --encrypted-id <enc> --original-id <plain>
ncm-cli pause | resume | next | prev | state | volume 60 | tui
ncm-cli cloudupload file --file <path> | diag report
```

### 6.2 Agent rules

1. First use: `ncm-cli --version`, then `ncm-cli login --check`.
2. **Fetch the tree every session**: `ncm-cli commands` — the command set changes with the manifest; do not rely on memory.
3. **Never guess params**: `ncm-cli <command> --help`.
4. Except playback-control commands, add `--userInput "<≤200-char summary of the user's latest turn>"` to dynamic commands; it becomes `X-User-Input`.
5. "Please log in / unauthorized" → `login --background`, give the link, finish the flow, retry the original command.
6. "Total quota exceeded" → relay that exact reason and stop; no retries or rephrasing.
7. Return clickable links with **plain IDs**: `https://music.163.com/#/song?id=<plain>`, `.../playlist?id=<plain>`, `.../album?id=<plain>`, `.../artist?id=<plain>`.
8. **Never** play/queue `visible=false`. With multiple results, play the first and queue the rest (queue command per `ncm-cli commands`, do not guess).

§6.3 (CLI manifest executor: `queryParams`→query, `bodyParams`+`queryParams`→POST JSON, `--userInput`→header, common params injected/signed) is internal detail; self-built clients always follow §3.1.

## 7. Agent decision flow

### 7.1 Intent → endpoint

| User wants | Call | Prerequisite |
|---|---|---|
| Find a song | `search/song/get/v3` | any token |
| Mixed types at once | `complex/search` | any token |
| Lyrics | `song/lyric/get/v2` | encrypted `songId` |
| Play / audio URL | `song/detail/get/v2` (`withUrl:true`) or `song/playurl/get/v2` | encrypted `songId` + check `visible` |
| Bulk URLs | `batch/song/playurl/get` | ≤500 encrypted IDs |
| My profile / Liked Songs | `user/profile/get/v2` / `playlist/star/get/v2` | **real-user token** |
| Playlist contents | `playlist/detail/get/v2` → `playlist/song/list/get/v3` | encrypted `playlistId` |
| Create + fill playlist | `playlist/create` → `playlist/song/batch/like` | real-user token |
| Like / unlike | `playlist/song/like/v2` | real-user token |
| Analyze preferences | `playlist/star/get/v2` → `playlist/song/list/get/v3` (`songTag`/`addTime`) | real-user token |
| Shorten URL | `common/middle/short/url` | any token |

### 7.2 Search and play

1. `config set appId/privateKey` → `login --check` (else `login --background`) → `ncm-cli commands` → `search song --help`.
2. Search (`ncm-cli search song` or §5.1) → drop `visible=false`/`playFlag=false` → take the first.
3. Record `id` (enc, API) + `originalId` (plain, links) → `song/detail/get/v2 {songId:id, withUrl:true, bitrate:320}`.
4. `ncm-cli play --song --encrypted-id <id> --original-id <originalId>`; first plays, rest queue.
5. `play/data/record`: `startplay` then `play` at end (pass `alg`) → reply name + `https://music.163.com/#/song?id=<originalId>`.

### 7.3 Build a playlist from preferences

1. Real-user login (anonymous cannot read user assets) → `playlist/star/get/v2` → Liked Songs id.
2. `playlist/song/list/get/v3 {playlistId, limit:200, offset:0}` → `songTag`/`addTime` for preference analysis.
3. Several `search/song/get/v3` (or `complex/search`) with preference keywords; dedupe, drop `visible=false`.
4. `playlist/create {playlistName}` → new id → `playlist/song/batch/like {playlistId, songIdList}`.
5. `playlist/detail/get/v2` → verify `trackCount` → reply `https://music.163.com/#/playlist?id=<plain>`.

## 8. Error codes

| code | Meaning | Action |
|---|---|---|
| `200` | Success | read `data` |
| `300`/`301` | appId not authorized for endpoint | stop retrying; switch appId or request access; manifest is gated too |
| `401`/`402` | invalid param / bizContent | check field names, types, required fields |
| `404` | endpoint/resource not found | check path + IDs; if correct, try the other Base URL (§3) |
| `500` | business/system error | retry once, then relay `message` |
| `1401` | unsupported HTTP method | switch GET↔POST |
| `1402`/`1403`/`1405` | bad sign/signType/appId | check key, canonical string, appId |
| `1404` | bad timestamp | sync clock within 5 min of server |
| `1406` | token expired | confirm via `message` (§8.2); refresh real-user only, re-login anonymous; re-login if refresh fails |
| `1407`/`1408` | account issue / invalid token | QR re-login, no refresh |
| `2402`–`2406` | missing sign/signType/timestamp/appId or encryption | complete common params + crypto config |
| `2501`–`2508` | `device` missing channel/deviceId/deviceType/appVer/os/osVer/brand/model | fill `device` (§3) |
| `2509` | missing accessToken | log in |
| `2511` | missing clientIp | set real public IP in `device` |
| `-444`/`-445`/`-446` | too frequent | back off, limited retries; no tight loop |
| `-461` | total quota exceeded | **tell the user and stop** |

### 8.2 Fall back to `message`

Protocol/auth codes (`300/301`, `2509`, `2511`, `2402`–`2508`, `1402`–`1405`) are handled directly above. Only business codes (`1406`, `-444/-446/-461`, song/playback `4xx`) need `message` first: the same `code` can mean copyright / VIP / preview / rate-limit at different layers, so decide from `message`/`subCode`, not the number.

- "not logged in / unauthorized" → login flow.
- "no copyright / removed / VIP / preview / paid" → pick another song or lower bitrate; do not retry.
- "total quota exceeded" → stop, relay verbatim; only "too frequent" allows limited back-off retries.
- `subCode 10003/10004` → must listen/buy in the mobile app; tell the user.

## 9. IDs, fields, returns

### 9.1 Song object (search / playlist tracks / detail)

| Field | Meaning |
|---|---|
| `id` | **encrypted ID**, 32-char hex — every API parameter |
| `originalId` | **plain numeric ID** — links/deep links only, in some responses |
| `name`/`duration`/`artists[]`/`album`/`coverImgUrl` | basics; `duration` ms |
| `visible` | copyright; `false` → never play/queue |
| `playFlag`/`downloadFlag` | play/download allowed |
| `payPlayFlag`/`payDownloadFlag`/`vipFlag`/`vipPlayFlag` | paid/VIP restrictions |
| `liked`/`freeTrailFlag` | liked / preview support |
| `songTag[]` | genre tags for preference analysis |
| `alg` | algorithm id, pass through to playback reporting |
| `maxBrLevel`/`plLevel`/`dlLevel` | max / playable / downloadable quality tier |

### 9.2 Rules

- **API params always encrypted IDs** (`songId`, `playlistId`, `albumId`, `artistId`, `songIdList[]`, `songIds[]`); plain IDs only for links, deep links, and `ncm-cli play --original-id`. If a result lacks `originalId`, get it from `song/detail/get/v2` or CLI output; if still absent, do not invent a link.
- **Pagination**: `offset` from 0; `limit` often capped 500 (search ≤100, playlist tracks 30 recommended); total in `data.recordCount`.
- **bitrate**: tier codes 128/320/999/1999, not bps; server downgrades if unavailable.
- **Tokens**: `accessToken`/`refreshToken` ≥512 chars, may contain `+`/`/`/`=` — URL-encode in queries.
- **Time**: `timestamp`/`startLogTime` ms; `expireTime`/`expiresTime`/`auditionStart`/`auditionEnd`/`time` seconds (or endpoint unit).
- **Links**: `https://music.163.com/#/song?id=<plain>`, `.../playlist?id=<plain>`, `.../album?id=<plain>`, `.../artist?id=<plain>`.

## Appendix A · Endpoint index

The official developer docs list ~282 endpoints; that list changes and is the source of truth — do not cache it here (Appendix B). Endpoints not expanded in §4–§5 still need all §3 common params + signature and an appId authorized for that specific endpoint.

## Appendix B · References

- Official developer documentation: <https://developer.music.163.com/>
- Documentation category tree: `GET https://interface.music.163.com/api/openplatform/developer/api/doc/category`
- Document detail: `GET https://interface.music.163.com/api/openplatform/developer/apidoc/get?docId=<id>`
- CLI package: `@music163/ncm-cli@0.1.7` (<https://www.npmjs.com/package/@music163/ncm-cli>)
- Official Agent Skills: <https://github.com/NetEase/skills>
