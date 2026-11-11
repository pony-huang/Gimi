# NetEase Cloud Music OpenAPI Usage Specification

[简体中文](../../zh/plugin/netease.md) · English

> This document is written for **agents**: for any music operation, it states which endpoint to call,
> which parameters to send, what comes back, and what to do on failure.
> It contains only actionable rules and parameters, no implementation background.
> Signing, common parameters, and error codes follow the official developer documentation;
> the CLI surface follows `@music163/ncm-cli` 0.1.7.

---

## 0. Six rules to remember first

1. **Two call paths**: prefer `ncm-cli` when a shell is available (it handles signing, `device`, and token refresh); implement raw HTTP per §3 only for server-side integration or endpoints the CLI does not expose.
2. **Two kinds of ID**: an encrypted ID (32-char hex, used by every API parameter) and a plain numeric ID (used for `music.163.com` web links, app deep links, and `ncm-cli play --original-id`). **Never put a plain ID in an API parameter or an encrypted ID in a link.**
3. **Every endpoint except anonymous login, the QR-code key endpoint, and the `grantCode` exchange requires `accessToken`**; QR-status polling must use an **anonymous** token. Obtain a token before calling business endpoints.
4. **The signature covers all non-empty common parameters except `sign`**: sort by parameter name, join as `k=v&...`, sign with RSA-SHA256, emit base64.
5. **`bizContent` and `device` are serialized JSON strings**, not nested objects.
6. **The gateway authorizes per appId, per endpoint**: `code 300 / 301` means missing permission, not a bad parameter. Retrying is useless — switch to an appId that has the permission, or request it. The manifest endpoint is gated by the same authorization.

---

## 1. Choosing a path

| | `ncm-cli` (preferred for agents with shell access) | Raw HTTP OpenAPI |
|---|---|---|
| Entry point | `ncm-cli <command> [args]` | `https://openncm.music.163.com/openapi/...` |
| Signing / device / token | handled by the CLI | implement it yourself (§3) |
| Command/endpoint source | `ncm-cli commands` (static + server-delivered dynamic commands) | §4–§5 and Appendix A |
| Best for | search, playback, playback control, playlists, cloud uploads, TUI | server integration, batch jobs, endpoints not exposed by the CLI |
| Prerequisites | Node.js ≥ 18, appId / privateKey, login | appId / privateKey (PKCS#8 PEM), accessToken |

Both use the same protocol underneath: dynamic CLI commands are translated into the HTTP requests described in §3 (see §6.3).

---

## 2. Credentials and setup

| Credential | Purpose | Where to get it |
|---|---|---|
| `appId` | Application ID, a common parameter on every request | Developer console |
| `privateKey` | RSA private key, used locally for signing only, never sent to the server | Developer console, PKCS#8 PEM |
| `appSecret` (`clientSecret`) | Required to refresh an accessToken | Developer console |
| `accessToken` | Login state, required except for anonymous login, QR-code key, and grantCode exchange | §4 |

CLI setup:

```bash
npm install -g @music163/ncm-cli
ncm-cli --version                          # requires Node.js >= 18
ncm-cli config set appId <APP_ID>
ncm-cli config set privateKey <PEM_file_path_or_PEM_text>
ncm-cli config set player mpv              # or orpheus (client playback, macOS only)
ncm-cli login --check                      # check login state; 0.1.7 can proactively refresh
ncm-cli login --background                 # background QR login; hand the link to the user
```

- If `privateKey` is a path, the CLI reads the file; otherwise the PEM text is used as-is.
- Environment variables also work: `NETEASE_APP_ID`, `NETEASE_PRIVATE_KEY`, `NETEASE_APP_SECRET`.
- Playback additionally requires `mpv` (check with `mpv --version`); `ncm-cli config get player` shows the configured player.
- **Prerequisite**: the appId must be granted access to the target endpoints with encryption configured in the developer console. Otherwise every data endpoint returns `code 300/301`.

---

## 3. HTTP protocol (self-built clients)

### 3.1 Host and methods

- Base URL: **use `https://openncm.music.163.com` by default** (used by ncm-cli 0.1.7). Official developer examples use `https://openapi.music.163.com`; paths and parameters are identical. Only switch to the other host when the default returns 404 for the target path.
- **GET**: put all common parameters in the query string, URL-encoded with `URLSearchParams`.
- **POST**: send the **whole common-parameter object** (`appId`, `signType`, `timestamp`, `device`, `bizContent`, `accessToken`, `sign`) as the JSON body with `Content-Type: application/json`. It is *not* just `bizContent` in the body.
- Endpoints documented as `GET/POST` accept both; **default to GET** (the §3.5 example uses GET). Use POST only for write operations or when a card explicitly says POST.

### 3.2 Common parameters

| Parameter | Type | Required | Description |
|---|---|---|---|
| `appId` | String | yes | Application ID |
| `signType` | String | yes | Fixed value `RSA_SHA256` |
| `timestamp` | String | yes | Millisecond timestamp, `String(Date.now())`; must be within 5 minutes of server time |
| `device` | String | yes | Device info, serialized as a JSON string |
| `bizContent` | String | yes | Business parameters, serialized as a JSON string; pass `{}` when empty |
| `accessToken` | String | per endpoint | Login token; omitted only for anonymous login, QR-code key, and grantCode exchange; QR-status polling requires an anonymous token |
| `sign` | String | yes | Signature, see §3.4 |

`device` object:

| Field | Required | ncm-cli 0.1.7 value | Description |
|---|---|---|---|
| `deviceType` | yes | `openapi` | Device type |
| `os` | yes | `ncmcli` | OS identifier |
| `appVer` | yes | `0.1.7` | Must be three numeric segments, `x.x.x` |
| `channel` | yes | `ncmcli` | Channel identifier |
| `model` | yes | `Windows_x64_cli` | `<platform>_<source>` |
| `brand` | yes | `ncmcli` | Brand identifier |
| `osVer` | yes | `10.0.26300` | OS version |
| `clientIp` | yes | public IP | Real outbound IP; missing it returns `2511` |
| `deviceId` | yes | `ncmcli_<random>` | Unique per device, ≤64 chars, must not contain `=`, `;`, space, `+` or `|`; bound to the anonymous token and persisted |
| `netStatus` | required by official docs | `4g` | `wifi/2g/3g/4g/5g` |
| `flowFlag` | no | `init` | Whether the request counts as a daily active user |

The exact values of `deviceType` / `os` / `channel` / `brand` are assigned by the platform. **If you do not know the registered values, use the CLI 0.1.7 values in the table above** (verified working); the CLI reuses them too.

### 3.3 Headers and response

```text
User-Agent: ncm-0.1.7
Referer: https://music.163.com/
Content-Type: application/json        # POST only
X-User-Input: <encodeURIComponent(conversation_summary)>   # when a CLI command carries --userInput, see §6.3
```

Responses are JSON: `{ "code": 200, "message": "", "data": {...}, "subCode": null }`.
**Only `code === 200` means success**; business data is in `data`. List responses use `data.recordCount` (total) and `data.records` (array); some endpoints put the array directly in `data` or in `data.tracks` — follow the endpoint card.

### 3.4 Signature algorithm

1. Collect the common parameters (**excluding `sign`**), dropping `null` / `undefined` / empty strings. Only common parameters are signed; headers such as `X-User-Input` are not.
2. Stringify every value (`device` and `bizContent` stay as their serialized JSON text, no extra escaping).
3. Sort by parameter name in lexicographic (ASCII ascending) order.
4. Join as `k1=v1&k2=v2&...` with **no URL encoding**.
5. Sign with `SHA256withRSA` using the PKCS#8 private key, output **base64**, and store it as `sign`.
6. On GET, URL-encode the whole query afterwards (`URLSearchParams` does this); on POST, put `sign` in the JSON body as-is.

Node.js:

```js
const crypto = require("crypto");

function signParams(params, privateKeyPem) {
  const canonical = Object.entries(params)
    .filter(([k, v]) => k !== "sign" && v !== null && v !== undefined && v !== "")
    .map(([k, v]) => [k, String(v)])
    .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
    .map(([k, v]) => `${k}=${v}`).join("&");
  return crypto.createSign("SHA256").update(canonical).sign(privateKeyPem, "base64");
}
```

Python:

```python
import base64
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

def sign_params(params: dict, private_key_pem: bytes) -> str:
    canonical = "&".join(
        f"{k}={params[k]}" for k in sorted(params)
        if k != "sign" and params[k] not in (None, "")
    )
    key = serialization.load_pem_private_key(private_key_pem, password=None)
    return base64.b64encode(
        key.sign(canonical.encode("utf-8"), padding.PKCS1v15(), hashes.SHA256())
    ).decode()
```

### 3.5 Minimal end-to-end example

```js
const crypto = require("crypto");
const BASE = "https://openncm.music.163.com";
const APP_ID = process.env.NETEASE_APP_ID;
const PRIVATE_KEY = process.env.NETEASE_PRIVATE_KEY;   // PKCS#8 PEM

async function detectPublicIp() {
  for (const u of ["https://api.ipify.org?format=json", "https://api.ip.sb/jsonip", "https://ifconfig.me/ip"]) {
    try {
      const r = await fetch(u, { headers: { Accept: "application/json" } });
      const v = u.includes("ifconfig") ? (await r.text()).trim() : await r.json();
      const ip = typeof v === "string" ? v : (v.ip || v.ip_addr);
      if (ip) return ip;
    } catch {}
  }
  throw new Error("Cannot detect public IP; check network connectivity and retry");
}

function signParams(params, key) {
  const canonical = Object.entries(params)
    .filter(([k, v]) => k !== "sign" && v !== null && v !== undefined && v !== "")
    .map(([k, v]) => [k, String(v)])
    .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
    .map(([k, v]) => `${k}=${v}`).join("&");
  return crypto.createSign("SHA256").update(canonical).sign(key, "base64");
}

async function call(path, bizContent, { method = "GET", accessToken } = {}) {
  const clientIp = await detectPublicIp();
  const device = {
    deviceType: "openapi", os: "ncmcli", appVer: "0.1.7", channel: "ncmcli",
    model: "Windows_x64_cli", brand: "ncmcli", osVer: "10.0.26300",
    clientIp, deviceId: "ncmcli_agent_001", netStatus: "4g",
  };
  const params = {
    appId: APP_ID, signType: "RSA_SHA256", timestamp: String(Date.now()),
    device: JSON.stringify(device), bizContent: JSON.stringify(bizContent),
  };
  if (accessToken) params.accessToken = accessToken;
  params.sign = signParams(params, PRIVATE_KEY);

  const headers = { "User-Agent": "ncm-0.1.7", Referer: "https://music.163.com/" };
  const res = method === "POST"
    ? await fetch(BASE + path, {
        method: "POST",
        headers: { ...headers, "Content-Type": "application/json" },
        body: JSON.stringify(params),
      })
    : await fetch(BASE + path + "?" + new URLSearchParams(params), { headers });
  const body = await res.json();
  if (body.code !== 200) throw new Error(`code=${body.code} ${body.message}`);
  return body.data;
}

// 1) Anonymous login (no accessToken on this endpoint)
const anon = await call("/openapi/music/basic/oauth2/login/anonymous",
  { clientId: APP_ID }, { method: "POST" });
const token = anon.accessToken;   // bound to deviceId; cache and reuse

// 2) Search songs
const found = await call("/openapi/music/basic/search/song/get/v3",
  { keyword: "晴天", limit: 30, offset: 0, qualityFlag: true },
  { accessToken: token });

// 3) Fetch detail and playback URL with the encrypted ID
const detail = await call("/openapi/music/basic/song/detail/get/v2",
  { songId: found.records[0].id, withUrl: true, bitrate: 320 },
  { accessToken: token });
// detail.playUrl is the playback URL; detail.openApiTraceInfo is used for full-song preview reporting
```

`clientIp` must be your real outbound public IP: the example resolves it at runtime with `detectPublicIp()`. Do not hard-code a fake address, or requests return `2511`.

---

## 4. Authentication and token lifecycle

**Default order**: anonymous login (guest capabilities) → QR login when user assets are needed → refresh with refreshToken on expiry → re-login if refresh fails.

### `/openapi/music/basic/oauth2/login/anonymous` — anonymous login
- **Method**: POST (docs allow GET/POST)　**CLI**: called internally by `ncm-cli login`
- **bizContent**: `{"clientId":"<appId>"}` (same as `appId`)　**accessToken**: omit
- **Returns**: `data.accessToken` (never expires, **a string here**), `data.refreshToken`, `data.expireTime`, `data.scopes`
- **Rules**: one `deviceId` maps to one fixed anonymous token — cache and reuse it; changing `deviceId` creates a new user. Guest state can read content and play audio, but cannot read user assets.

### `/openapi/music/basic/user/oauth2/qrcodekey/get/v2` — get login QR code
- **Method**: GET　**accessToken**: omit　**bizContent**: `{"type":2,"expiredKey":"300"}` (`type` is fixed at 2; `expiredKey` is the QR lifetime in seconds, fixed at 300)
- **Returns**: `data.qrCodeUrl` (show/scan for the user), `data.uniKey` (polling key)
- **Rules**: valid for 5 minutes, single use; `qrCodeUrl` may be a short or long URL — reserve 8–512 chars.

### `/openapi/music/basic/oauth2/device/login/qrcode/get` — poll QR status
- **Method**: GET　**bizContent**: `{"key":"<uniKey>","clientId":"<appId>"}`
- **accessToken**: **must be an anonymous token** (a real-user token returns 400 `device has login success`)
- **Returns**: `data.status`, `data.msg`, `data.accessToken.{accessToken,refreshToken,expireTime}`. **Here `data.accessToken` is an object**; the new token is `data.accessToken.accessToken`, unlike the string `data.accessToken` from anonymous login.
- **status**: `800` missing/expired, `801` waiting for scan, `802` authorizing, `803` success, `804` unknown error
- **Rules**: poll every 2–3 seconds until `803` (extract tokens), `800` (request a new QR code), or the 5-minute timeout; `804` counts as failure — request a fresh QR code.

### `/openapi/music/basic/user/oauth2/token/refresh/v2` — refresh token
- **Method**: GET　**bizContent**: `{"clientId":"<appId>","clientSecret":"<appSecret>","refreshToken":"<RT>"}`
- **Returns**: `data.accessToken`, `data.refreshToken`, `data.expiresTime` (seconds)
- **Rules**: accessToken lasts 7 days, refreshToken 20 days, and both renew together; `clientId` must match the appId the refreshToken was issued under; anonymous tokens cannot be refreshed (returns 1406).

### Other auth endpoints
| Endpoint | Method | bizContent | Purpose |
|---|---|---|---|
| `/openapi/music/basic/user/oauth2/token/get/v2` | GET/POST | `{"grantCode":"<callback_code>"}` | Exchange an H5 / app-deeplink callback code for a token; no accessToken on this endpoint |
| `/openapi/music/basic/user/info/bytoken/get/v2` | POST | `{"accessToken":"<AT>"}` | Look up user info by token |
| `/openapi/music/basic/do/noting` | GET | `{"clientId":"<appId>"}` | Validate credentials in the setup wizard |
| `/openapi/music/basic/user/profile/get/v2` | GET | `{}` | Current user profile, see §5.3 |

**Token error handling**: first check `message` per §8.2 to confirm the failure is token expiry. Only a **real-user token** is refreshed; **an anonymous token cannot be refreshed — go straight to QR login**. If refresh fails or returns `1407` / `1408`, also re-login. Store both tokens as unbounded strings (at least 512 characters).

---

## 5. Endpoint cards

**How to read**: `bizContent` values are ready-to-use JSON; `songId` / `playlistId` / `albumId` / `artistId` are always **encrypted IDs**. Cards without a CLI command are delivered by the server manifest (§5.6). All endpoints require `accessToken` unless stated otherwise; **reading my profile / Liked Songs, creating or editing playlists, and like operations require a real-user token** — an anonymous token will fail.

### 5.1 Search and discovery

| Intent | Endpoint | Key bizContent |
|---|---|---|
| Search songs | `/openapi/music/basic/search/song/get/v3` | `keyword`, `limit`, `offset` |
| Search albums | `/openapi/music/basic/search/album/get/v2` | `keyword`, `limit`, `offset` |
| Search artists | `/openapi/music/basic/search/artists/get/v2` | `keyword`, `limit`, `offset` |
| Search playlists | `/openapi/music/basic/search/playlist/get/v2` | `keyword`, `limit`, `offset` |
| Playlists by tag | `/openapi/music/basic/search/playlist/bytag/get/v2` | `tag`, `limit`, `offset` |
| Combined search (multiple types at once) | `/openapi/music/basic/complex/search` | `keyword` |
| Trending keywords | `/openapi/music/basic/search/hot/keyword/get/v2` | `limit` |
| Search suggestions | `/openapi/music/basic/search/suggest/keyword/get/v2` | `keyword` |

#### `/openapi/music/basic/search/song/get/v3` — search songs
- **Method**: GET / POST　**CLI**: `ncm-cli search song --keyword "xxx" --userInput "..."`
- **bizContent**: `{"keyword":"晴天","limit":30,"offset":0,"qualityFlag":true}`
- **Parameters**: `keyword` String required｜`limit` Int required (≤100 recommended)｜`offset` Int required (starts at 0)｜`qualityFlag` Boolean optional, whether to include quality info
- **Returns**: array of song objects, normally in `data.records` (total in `data.recordCount`); fields in §9.1. If the response is a bare array, read it directly.
- **Note**: the legacy endpoint is `search/song/get/v2`; always use v3 for new integrations. The `id` in results is the encrypted ID used by every subsequent call.

#### `/openapi/music/basic/complex/search` — combined search
- **Method**: GET / POST　**bizContent**: `{"keyword":"精卫","identityFlag":true,"subCountFlag":true}`
- **Parameters**: `keyword` String required｜`qualityFlag` Boolean optional｜`identityFlag` / `subCountFlag` Boolean optional (artist identity tags, follower counts)
- **Returns**: `data` containing `songs` / `artists` / `albums` / `playlists` / `voicelist` / `bestMatchResources` / `bestMatchResourceTypes`
- **Usage**: prefer this when the intent is vague and you want candidates of several types; use `search/song/get/v3` when only songs are needed.

#### Other search endpoints
| Endpoint | Method | bizContent | Returns / rules |
|---|---|---|---|
| `/openapi/music/basic/search/album/get/v2` | GET/POST | `{"keyword":"xxx","limit":30,"offset":0}` | Albums; `limit` up to 300, ≤100 recommended |
| `/openapi/music/basic/search/artists/get/v2` | GET/POST | `{"keyword":"xxx","limit":30,"offset":0,"identityFlag":true,"subCountFlag":true}` | Artists; both optional flags default to false |
| `/openapi/music/basic/search/playlist/get/v2` | GET/POST | `{"keyword":"xxx","limit":30,"offset":0}` | Playlists |
| `/openapi/music/basic/search/playlist/bytag/get/v2` | GET/POST | `{"tag":"欧美","limit":10,"offset":0}` | Playlists by tag; `limit` up to 500 |
| `/openapi/music/basic/search/hot/keyword/get/v2` | GET/POST | `{"limit":10}` | Trending keywords; no `keyword` |
| `/openapi/music/basic/search/suggest/keyword/get/v2` | GET/POST | `{"keyword":"邓"}` | Autocomplete; not for full search |

### 5.2 Songs, lyrics, and playback

#### `/openapi/music/basic/song/detail/get/v2` — song detail + playback URL
- **Method**: GET (docs allow GET/POST)　**bizContent**: `{"songId":"<encId>","withUrl":true,"bitrate":320}`
- **Parameters**: `songId` String required｜`withUrl` Boolean required, whether to include a playback URL｜`bitrate` Int optional｜`effects` String optional (`eac3` / `ac4` for Dolby)｜`qualityFlag` Boolean optional｜`level` String optional (`vivid` / `sky` / `jymaster` / `jyeffect`, falls back automatically)｜`immerseType` String optional (required with `sky`: `c51` / `ste` / `aac`)｜`languageFlag` Boolean optional｜`extFlags` String optional, a JSON-serialized string such as `"{\"hqScene\":\"normal\"}"` or `"{\"chorusFlag\":true}"`
- **bitrate values**: `128` standard, `320` very high, `999` lossless, `1999` Hi-Res; omitted means the highest tier the user can play. These are tier codes, not bits per second.
- **Returns**: song object; `playUrl` is the playback URL, `playUrlExpireTime` its expiry, `br` the actual bitrate, `gain` / `peak` the gain values, `openApiTraceInfo` the trace ID (pass through for full-song preview reporting)
- **Note**: check `visible` / `playFlag` first — do not play when `false`; handle restrictions via `message` / `subCode` (§8).

#### `/openapi/music/basic/song/playurl/get/v2` — playback URL only
- **Method**: GET / POST　**bizContent**: `{"songId":"<encId>","bitrate":320}`
- **Parameters**: `songId` String required｜`bitrate` Int optional (default 320, downgraded if unavailable)｜`effects` / `level` / `immerseType` same as for `song/detail/get/v2` above
- **Returns**: `data.url` is the playback URL (the equivalent field in song detail is `playUrl`).
- **Usage**: when only a URL is needed without full song metadata; use the batch endpoint for multiple songs.

#### `/openapi/music/basic/batch/song/playurl/get` — batch playback URLs
- **Method**: GET / POST　**bizContent**: `{"songIds":["<encId1>","<encId2>"],"bitrate":128,"effects":"eac3"}`
- **Parameters**: `songIds` List\<String> required, **≤500 IDs**｜`bitrate` Int optional｜`effects` String optional

#### `/openapi/music/basic/song/lyric/get/v2` — line-by-line lyrics
- **Method**: GET / POST　**bizContent**: `{"songId":"<encId>"}`
- **Returns**: `data.songId`, `lyric` (LRC timestamped text), `transLyric` (translation), `txtLyric` (plain text), `romalrc` (romanization), `noLyric`, `pureMusic`
- **Rules**: do not show lyrics when `noLyric=true` or `pureMusic=true`; `transLyric` may be `null`.

#### `/openapi/music/basic/song/lyric/word/by/word/get` — word-by-word lyrics
- **Method**: GET / POST　**bizContent**: `{"songId":"<encId>"}`
- **Rules**: only available for head-catalog songs; fall back to `song/lyric/get/v2` when unavailable.

#### `/openapi/music/basic/play/data/record` — playback data reporting
- **Method**: POST (docs allow GET/POST)　**CLI**: reported automatically by playback control
- **Rules**: report **twice per play**: `action="startplay"` at the start and `action="play"` at the end (**the end payload must explicitly set `action` to `play`**). Pass through `alg` from search/recommend/playlist responses and `openApiTraceInfo` from song detail when present; use `alg: "search"` for search scenarios with no upstream value.

Start of playback:

```json
{
  "id": "<encId>", "action": "startplay", "type": "song",
  "bitrate": 320, "file": 4, "startLogTime": 1730000000000,
  "alg": "<alg from upstream>", "sourceId": "search", "sourceType": "search"
}
```

End of playback (copy the start payload, change `action` to `play`, then add `time` and `end`):

```json
{
  "id": "<encId>", "action": "play", "type": "song",
  "bitrate": 320, "file": 4, "startLogTime": 1730000000000,
  "alg": "<alg from upstream>", "sourceId": "search", "sourceType": "search",
  "time": 210, "end": "playend"
}
```

| Parameter | Required | Description |
|---|---|---|
| `id` | yes | Encrypted resource ID (`encVoiceId` for podcasts) |
| `action` | yes | `startplay` to start, `play` to finish |
| `type` | yes | `song` or `dj` (podcast) |
| `bitrate` | yes | `128`/`320`/`999`/`1999`/`3999`/`4999`/`4099`/`2999`/`5999` (the last five high tiers are reporting-only); podcasts always `128` |
| `file` | yes | `0` downloaded, `1` local, `2` cached, `3` cloud drive, `4` online; use `4` when unsure |
| `startLogTime` | yes | Start time in milliseconds |
| `time` | required at end | Actual listened seconds (excluding pauses), >0 and ≤ song duration; accumulate actual listening time across speed changes and seeks |
| `end` | required at end | `playend` natural end｜`interrupt` third-party interruption｜`exception` playback error｜`ui` user skip/pause; use `playend` when unsure |
| `alg` | yes | Algorithm field from upstream (search/recommend/playlist), pass through when present; otherwise use `search` in search scenarios |
| `sourceId` / `sourceType` | yes | Playback source; `sourceType` is `list`/`album`/`dailySongRecommend`/`userfm`/`search`/`djradio`, `sourceId` is the corresponding playlist/album ID or the type name |
| `isAudition` | no | `1` clip preview, `2` full-song preview |
| `auditionStart` / `auditionEnd` | no | Preview clip boundaries in seconds, taken from the upstream endpoint |
| `openApiTraceInfo` | no | Trace ID returned by song detail; pass through for full-song preview reporting, omit when absent |
| `categoryId` / `ext` | no | Podcast category / device identifier JSON string |

### 5.3 User and playlists

#### User profile
| Endpoint | Method | bizContent | Returns |
|---|---|---|---|
| `/openapi/music/basic/user/profile/get/v2` | GET | `{}` | `id`, `nickname`, `avatarUrl`, `gender` (0 unknown / 1 male / 2 female), `signature`, `redVipLevel`, `vipDetail` |
| `/openapi/music/basic/user/info/bytoken/get/v2` | POST | `{"accessToken":"<AT>"}` | User info for the token |

#### Reading playlists
| Endpoint | Method | bizContent | Returns / rules |
|---|---|---|---|
| `/openapi/music/basic/playlist/star/get/v2` | GET | `{}` | Liked Songs playlist object (`id`, `name`, `trackCount`, `coverImgUrl`, …); entry point for preference analysis |
| `/openapi/music/basic/playlist/created/get/v2` | GET | `{"limit":100,"offset":0}` | Playlists I created; `recordCount` + `records`, `limit` ≤500 |
| `/openapi/music/basic/playlist/subed/get/v2` | GET | `{"limit":100,"offset":0,"originalCoverFlag":false}` | Playlists I subscribed to; `originalCoverFlag` controls watermark-free covers |
| `/openapi/music/basic/playlist/detail/get/v2` | GET/POST | `{"playlistId":"<encId>"}` | Playlist detail; optional `originalCoverFlag` / `newCoverFlag` |
| `/openapi/music/basic/playlist/song/list/get/v3` | GET | `{"playlistId":"<encId>","limit":30,"offset":0,"qualityFlag":true}` | Track list; `limit` ≤500, 30 recommended |
| `/openapi/music/basic/playlist/song/list/get/v5` | GET/POST | same as above | Returns `data.tracks` plus playlist metadata, useful for fetching everything at once |

`specialType`: `0` normal, `5` Liked Songs, `10` pinned, `20` tail, `100` official, `200` video, `300` shared.
Tracks in Liked Songs carry `extMap.addTime` (like time in ms) and `songTag[]` (genre tags), usable for preference analysis.

#### Writing playlists
| Endpoint | Method | bizContent | Description |
|---|---|---|---|
| `/openapi/music/basic/playlist/create` | POST | `{"playlistName":"Running"}` | Create a playlist; **CLI**: `ncm-cli playlist create --playlistName "xxx" --userInput "..."`. For direct HTTP calls, follow the manifest `bodyParams` |
| `/openapi/music/basic/playlist/song/batch/like` | POST | `{"playlistId":"<encId>","songIdList":["<encId>","<encId>"]}` | Add multiple tracks |
| `/openapi/music/basic/playlist/song/batch/delete` | POST | `{"playlistId":"<encId>","songIdList":["<encId>"]}` | Remove multiple tracks, max 500; creator only |
| `/openapi/music/basic/playlist/song/like/v2` | POST | `{"songId":"<encId>","isLike":true}` | Like / unlike; `isLike=false` removes |
| `/openapi/music/basic/playlist/sub/v2` | POST | `{"id":"<encId>"}` | Subscribe to a playlist |
| `/openapi/music/basic/playlist/unsub/v2` | POST | `{"id":"<encId>"}` | Unsubscribe |

**IDs after creation**: `playlist/create` returns the new playlist object in `data`; take its playlist ID (encrypted) as the `playlistId` for the next step. If the response does not contain it, call `playlist/created/get/v2` and take the newest entry. The plain numeric ID needed for a user-facing link (usually also named `originalId` on a playlist object) comes from `playlist/detail/get/v2` or `created/get/v2`; if you cannot obtain it, do not invent a link.

**Verify writes**: call `playlist/detail/get/v2` again and confirm `data.trackCount` increased by the number of tracks added before reporting success.

### 5.4 Albums, artists, and charts

| Endpoint | Method | bizContent | Returns / rules |
|---|---|---|---|
| `/openapi/music/basic/album/detail/get/v2` | POST | `{"albumId":"<encId>","descFlag":true}` | Album detail; `descFlag` includes the description |
| `/openapi/music/basic/album/song/list/get/v3` | GET/POST | `{"albumId":"<encId>","limit":30,"offset":0}` | Album tracks |
| `/openapi/music/basic/artist/detail/get/v2` | GET/POST | `{"artistId":"<encId>","extFlag":true}` | Artist detail; optional `personalFlag` / `creatorRoleFlag` / `subCountFlag` / `identityFlag` |
| `/openapi/music/basic/artists/hotsong/list/get/v2` | GET/POST | `{"artistsId":"<encId>","top":50,"qualityFlag":true}` | Artist's popular tracks; the parameter is `artistsId` (plural), `top` is the first N tracks, max 60 |
| `/openapi/music/basic/toplist/get/v2` | GET/POST | `{}` | Chart list; `category` is `OFFICIAL`/`FEATURE`/`MUSIC_STYLE`/`GLOBAL`/`MORE`/`LANGUAGE` |

### 5.5 Utilities and files

| Endpoint | Method | bizContent | Description |
|---|---|---|---|
| `/openapi/music/basic/common/middle/short/url` | POST | `{"sourceUrl":"<long_url>","expireTimeSeconds":1296000}` | Long URL to short URL, returns `data.shortUrl`; defaults to 15 days |
| `/openapi/music/cloud/file/upload/check` | POST | delivered by CLI | Pre-upload instant-transfer check |
| `/openapi/whale/nos/upload/token`, `/openapi/whale/nos/upload/token/docid` | GET | delivered by CLI | NOS upload credentials; file bytes go directly to `wanproxy.127.net` / `wanproxy-web.127.net` |
| `/openapi/music/cloud/file/upload` | POST | delivered by CLI | Commit a cloud-drive upload |
| `/openapi/music/cloud/file/upload/status` | POST | delivered by CLI | Background upload task status |
| `/openapi/music/cloud/file/download` | GET | delivered by CLI | Cloud-drive file download |

Cloud-upload parameters are delivered by the CLI / manifest; self-built clients should use `ncm-cli cloudupload --help` instead of assembling them manually.

### 5.6 Dynamic command manifest

- **Endpoint**: `POST /openapi/v1/ncm/cli/manifest`, **bizContent**: `{"cli_version":"0.1.7","cached_versions":{}}`, requires `accessToken`.
- **Returns**: the server-delivered command tree; the CLI caches it at `~/.config/ncm-cli/cache/manifest.json`.
- **Purpose**: exact endpoints and parameters for search, lyrics, note publishing, playlist creation, and similar commands come from here. `ncm-cli commands` shows "static commands + manifest dynamic commands". This endpoint is also gated by appId authorization.

---

## 6. CLI command surface

### 6.1 Static commands

```bash
ncm-cli --help                         # all available commands
ncm-cli commands                       # current command tree (including dynamic commands)
ncm-cli <command> --help               # exact parameters; never guess them
ncm-cli configure                      # setup wizard: appId / privateKey / player
ncm-cli config get <key>               # read config, e.g. config get player
ncm-cli config set <key> <value>       # write config
ncm-cli login --check                  # login state check
ncm-cli login --background             # background QR login, hand the link to the user
ncm-cli search song --keyword "xxx"    # search songs
ncm-cli playlist create --playlistName "xxx"
ncm-cli play --song --encrypted-id <encrypted_id> --original-id <plain_id>
ncm-cli pause | resume | next | prev | state
ncm-cli volume 60                      # 0-100
ncm-cli tui                            # full-screen player
ncm-cli cloudupload file --file <path> # upload a file to the cloud drive
ncm-cli diag report                    # upload diagnostic logs
```

### 6.2 Rules for agents

1. Before first use: `ncm-cli --version` to check installation, `ncm-cli login --check` for login state.
2. **Fetch the command tree at the start of every session**: `ncm-cli commands`. The command set changes with the server manifest — do not rely on memorized commands.
3. **Never guess parameters**: run `ncm-cli <command> --help` when a command's parameters are needed.
4. Except for playback-control commands, append `--userInput "<short summary of the user's latest turn, ≤200 chars>"` to dynamic commands; it becomes the `X-User-Input` header (§6.3).
5. If output contains "please log in / unauthorized", run `ncm-cli login --background`, give the link to the user, finish the whole login flow, then retry the original command.
6. If output says the total request quota is exceeded, tell the user that exact reason and stop — no retries, no rephrasing.
7. Return clickable links where possible, using **plain numeric IDs**:
   `https://music.163.com/#/song?id=<plain_id>`, `.../playlist?id=<plain_id>`, `.../album?id=<plain_id>`, `.../artist?id=<plain_id>`.
8. **Never** play or queue a song with `visible=false`. With multiple results, play the first and queue the rest (use the queue command shown by `ncm-cli commands`; do not guess).

### 6.3 How CLI commands map to HTTP (dynamic command executor)

Each manifest entry contains:

```text
path          : "/openapi/..." (may contain {pathParam} placeholders)
http_method   : "GET" | "POST"
auth_required : bool
auth_provider : "netease" (default)
pathParams / queryParams / bodyParams : parameter definitions grouped by position
command name, description, parameter validation (zod)
```

At execution time:

- `GET`: `queryParams` are appended to the query string.
- `POST`: `{...queryParams, ...bodyParams}` becomes the JSON body.
- `--userInput "<summary>"` becomes the header `X-User-Input: <encodeURIComponent(summary)>`.
- Common parameters (`appId` / `signType` / `timestamp` / `device` / `bizContent` / `accessToken` / `sign`) are injected and signed by the client, see §3.
- **Note**: this section describes the CLI executor's internal assembly, **not the body format for a self-built HTTP client**. Self-built clients always follow §3.1 (GET: common parameters in the query; POST: the whole common-parameter object as the body).

---

## 7. Agent decision flow

### 7.1 Intent → endpoint quick reference

| User wants | Call this | Prerequisite |
|---|---|---|
| Find a song | `search/song/get/v3` | any token |
| Find songs/artists/albums/playlists at once | `complex/search` | any token |
| Show lyrics | `song/lyric/get/v2` | encrypted `songId` |
| Play / get an audio URL | `song/detail/get/v2` (`withUrl:true`) or `song/playurl/get/v2` | encrypted `songId` + check `visible` |
| Playback URLs in bulk | `batch/song/playurl/get` | ≤500 encrypted IDs |
| My profile / Liked Songs | `user/profile/get/v2` / `playlist/star/get/v2` | **real-user token** |
| Playlist contents | `playlist/detail/get/v2` → `playlist/song/list/get/v3` | encrypted `playlistId` |
| Create a playlist and add tracks | `playlist/create` → `playlist/song/batch/like` | real-user token |
| Like / unlike a song | `playlist/song/like/v2` | real-user token |
| Analyze preferences | `playlist/star/get/v2` → `playlist/song/list/get/v3` (read `songTag` / `extMap.addTime`) | real-user token |
| Shorten a URL | `common/middle/short/url` | any token |

### 7.2 Typical task: search and play a song

```text
1. Setup: config set appId/privateKey → login --check (if not logged in, login --background)
2. Fetch command tree: ncm-cli commands; confirm search song parameters with --help
3. Search: ncm-cli search song --keyword "xxx" --userInput "..."
           or HTTP §5.1, obtaining a song list
4. Filter: drop results with visible=false / playFlag=false, take the first one
5. Record both IDs: id = encrypted ID (all later API calls), originalId = plain ID (links)
6. Detail/URL: song/detail/get/v2 {songId: id, withUrl: true, bitrate: 320}
7. Play: ncm-cli play --song --encrypted-id <id> --original-id <originalId>
         with multiple songs, play the first and queue the rest
8. Report: play/data/record with action=startplay, then action=play at the end (pass through alg)
9. Reply: song name + https://music.163.com/#/song?id=<originalId>
```

### 7.3 Typical task: build a playlist from preferences

```text
1. Real-user login (anonymous tokens cannot read user assets)
2. playlist/star/get/v2 → get the Liked Songs playlist id
3. playlist/song/list/get/v3 {playlistId, limit:200, offset:0} → read songTag / addTime for preference analysis
4. Run several search/song/get/v3 (or complex/search) calls with preference keywords;
   deduplicate and filter out visible=false
5. playlist/create {playlistName} → get the new playlist id
6. playlist/song/batch/like {playlistId, songIdList}
7. playlist/detail/get/v2 → verify trackCount
8. Reply: https://music.163.com/#/playlist?id=<plain_id>
```

---

## 8. Error codes and handling

### 8.1 Common error codes

| code | Meaning | What the agent should do |
|---|---|---|
| `200` | Success | Read `data` |
| `300` / `301` | App not authorized for this endpoint | Stop retrying; switch to an appId with the permission or request it. The manifest endpoint is gated by the same authorization and is not a workaround |
| `401` / `402` | Invalid parameter / invalid business parameter | Check `bizContent` field names, types, and required fields |
| `404` | Endpoint or resource not found | Check the path and IDs; if the path is correct, switch to the other Base URL in §3.1 |
| `500` | Business/system error | Retry once; if it fails again, relay `message` |
| `1401` | Unsupported HTTP method | Switch between GET and POST |
| `1402` / `1403` / `1405` | Invalid sign / signType / appId | Check the private key, canonical string, and appId |
| `1404` | Invalid timestamp | Sync the clock (within 5 minutes of Beijing time) |
| `1406` | accessToken or refreshToken expired | First confirm via `message` per §8.2 that this is token expiry; refresh only real-user tokens, re-login immediately for anonymous tokens; re-login if refresh fails |
| `1407` / `1408` | Account problem / invalid accessToken | Re-login via QR code, do not refresh |
| `2402`–`2406` | Missing sign / signType / timestamp / appId, or encryption not configured | Complete common parameters and encryption setup |
| `2501`–`2508` | `device` missing channel / deviceId / deviceType / appVer / os / osVer / brand / model | Fill in `device` per §3.2 |
| `2509` | Missing accessToken | Log in to obtain a token |
| `2511` | Missing clientIp | Put the real public IP in `device` |
| `-444` / `-445` / `-446` | Too many requests | Back off and retry a limited number of times; no immediate tight loop |
| `-461` | Total request quota exceeded | **Tell the user directly and stop**, without rephrasing |

### 8.2 Fall back to `message`

**Decision order**: protocol/authorization codes (`300/301`, `2509`, `2511`, `2402`–`2508`, `1402`–`1405`) are handled directly per §8.1. Only business-layer codes (`1406`, `-444/-446/-461`, and `4xx` on song/playback endpoints) require reading `message` first. The same `code` can mean different things at different gateway layers (for example those codes can also indicate copyright, VIP, preview, or rate limits), so for these **decide the cause from `message` / `subCode`, not from the number alone**:

- Message mentions "not logged in / please log in / unauthorized" → run the login flow.
- Message mentions "no copyright / removed / VIP / preview / paid" → pick another song or lower the bitrate; do not retry the same request.
- Message says total quota exceeded → stop immediately and relay it verbatim; only "too frequent" allows a limited number of retries after backing off.
- `subCode: 10003 / 10004` → the song must be played or purchased in the mobile app; tell the user.

---

## 9. IDs, fields, and return-value rules

### 9.1 Song object (shared by search / playlist tracks / detail)

| Field | Description |
|---|---|
| `id` | **Encrypted ID**, 32-char hex; use this for every API parameter |
| `originalId` | **Plain numeric ID**; only for `music.163.com` links and app deep links, present in some responses |
| `name` / `duration` / `artists[]` / `album` / `coverImgUrl` | Basic info; `duration` is in milliseconds |
| `visible` | Copyright available; when `false`, never play or queue |
| `playFlag` / `downloadFlag` | Whether playback / download is allowed |
| `payPlayFlag` / `payDownloadFlag` / `vipFlag` / `vipPlayFlag` | Paid / VIP restrictions |
| `liked` / `freeTrailFlag` | Liked state / preview support |
| `songTag[]` | Genre tags, used for preference analysis |
| `alg` | Algorithm identifier, must be passed through to playback reporting |
| `maxBrLevel` / `plLevel` / `dlLevel` | Song max quality / user playable / downloadable tiers |

### 9.2 General rules

- **API parameters always use encrypted IDs** for `songId`, `playlistId`, `albumId`, `artistId`, `songIdList[]`, and `songIds[]` (32-char hex); plain numeric IDs are for web links, app deep links, and `ncm-cli play --original-id`. If a search result has no `originalId`, obtain it from `song/detail/get/v2` or CLI output; if still unavailable, do not invent a link.
- **Pagination**: `offset` starts at 0; `limit` is commonly capped at 500, ≤100 recommended for search and 30 for playlist tracks; the total is in `data.recordCount`.
- **bitrate**: tier codes `128` / `320` / `999` / `1999` (not 128000/320000); the server downgrades when the requested tier is unavailable.
- **Token fields**: reserve ≥512 chars for `accessToken` / `refreshToken`, which may contain `+` `/` `=`; URL-encode them in query strings.
- **Time**: `timestamp` and `startLogTime` are milliseconds; `expireTime` / `expiresTime` / `auditionStart` / `auditionEnd` / `time` are seconds (or the unit given by the endpoint).
- **Links**: `https://music.163.com/#/song?id=<plain_id>`, `.../playlist?id=<plain_id>`, `.../album?id=<plain_id>`, `.../artist?id=<plain_id>`.

---

## Appendix A · Full endpoint index

The developer documentation lists 282 endpoints, grouped below by capability. **This index contains only paths from the official documentation list**; CLI-only endpoints from §4–§5 are not included: `/openapi/music/cloud/file/upload/check`, `/upload`, `/upload/status`, `/download`, `/openapi/whale/nos/upload/token`, `/token/docid`, `/openapi/v1/ncm/cli/manifest`, `/openapi/music/basic/do/noting`, `/openapi/music/basic/playlist/create`. A few paths are reproduced verbatim from the official list (for example `.../recommend/playlist/ge`) — verify them in the developer docs before use. For endpoints not expanded in §4–§5, parameter details come from the official developer docs (Appendix B). All of them still require the common parameters and signature from §3, and an appId authorized for that endpoint.

### Authentication & Account (26)

- `/openapi/activity/summary/annual/third-party/2025/user-bind`
- `/openapi/api/game/clause/switch/get`
- `/openapi/cmcc/package/login`
- `/openapi/cmcc/package/order`
- `/openapi/cmcc/package/protocol`
- `/openapi/cmcc/package/sms/code`
- `/openapi/livestream/game/user/info/get`
- `/openapi/livestream/user/info/get`
- `/openapi/middle/activity/music-moyi/query-user-status`
- `/openapi/music/basic/oauth2/device/login/qrcode/get`
- `/openapi/music/basic/oauth2/login/anonymous`
- `/openapi/music/basic/user/id/info`
- `/openapi/music/basic/user/info/bytoken/get/v2`
- `/openapi/music/basic/user/info/get/v2`
- `/openapi/music/basic/user/nearby/v2`
- `/openapi/music/basic/user/oauth2/getByUrs`
- `/openapi/music/basic/user/oauth2/qrcodekey/get/v2`
- `/openapi/music/basic/user/oauth2/token/get/v2`
- `/openapi/music/basic/user/oauth2/token/refresh/v2`
- `/openapi/music/basic/user/oauth2/urs/login`
- `/openapi/music/basic/user/profile/get/v2`
- `/openapi/music/market/activity/user/vip/issue`
- `/openapi/user/cert/twoelement/status/get`
- `/openapi/user/follow/add`
- `/openapi/user/follow/judge`
- `/openapi/user/follow/list`

### Search & Discovery (28)

- `/openapi/ai/factory/user/song/generate/production/detail`
- `/openapi/aio/creative/material/upload`
- `/openapi/aio/produce/task/create`
- `/openapi/aio/produce/task/result/get`
- `/openapi/aio/produce/task/sync/execute`
- `/openapi/dj/program/news/quick/ai/talk`
- `/openapi/music/basic/complex/search`
- `/openapi/music/basic/search/album/get/v2`
- `/openapi/music/basic/search/artists/get/v2`
- `/openapi/music/basic/search/charts/list/get`
- `/openapi/music/basic/search/hot/keyword/get/v2`
- `/openapi/music/basic/search/mv/get/v1`
- `/openapi/music/basic/search/song/by/album/artist/get/v2`
- `/openapi/music/basic/search/song/by/artist/song/get/v2`
- `/openapi/music/basic/search/song/byartist/get/v2`
- `/openapi/music/basic/search/song/get/v2`
- `/openapi/music/basic/search/song/get/v3`
- `/openapi/music/basic/search/suggest/keyword/get/v2`
- `/openapi/music/basic/voice/assistant/multi/search`
- `/openapi/music/search/`
- `/openapi/podcast/ai/custom/radio/produce/again`
- `/openapi/podcast/ai/custom/radio/save`
- `/openapi/podcast/ai/custom/radio/static/data/get`
- `/openapi/podcast/ai/custom/radio/status/get`
- `/openapi/podcast/ai/custom/radio/user/data/get`
- `/openapi/search/multi/terminal/program`
- `/openapi/search/multi/terminal/voicelist`
- `/openapi/stream/common/ai/search`

### Songs, Lyrics & Audio Quality (66)

- `/openapi/dj/program/song/list`
- `/openapi/listen/together/like/song`
- `/openapi/listen/together/song/change/report`
- `/openapi/music/basic/action/aidj/audio/get`
- `/openapi/music/basic/aidj/audio/timbre/get`
- `/openapi/music/basic/alarm/list/get/v2`
- `/openapi/music/basic/alarm/playurl/get/v2`
- `/openapi/music/basic/album/song/list/get/v2`
- `/openapi/music/basic/album/song/list/get/v3`
- `/openapi/music/basic/artists/song/list/get`
- `/openapi/music/basic/batch/play/data/record`
- `/openapi/music/basic/batch/song/playurl/get`
- `/openapi/music/basic/custom/page/list`
- `/openapi/music/basic/dolby/list/get/v2`
- `/openapi/music/basic/dolby/list/get/v3`
- `/openapi/music/basic/hi/res/get`
- `/openapi/music/basic/httpdns/d`
- `/openapi/music/basic/link/page/rcmd/resource/show`
- `/openapi/music/basic/new/song/chart/list/get`
- `/openapi/music/basic/private/cloud/song/list/get`
- `/openapi/music/basic/private/fm/roaming/song/list`
- `/openapi/music/basic/query/song/record/get`
- `/openapi/music/basic/recommend/miyue/song/list/get`
- `/openapi/music/basic/recommend/more/song`
- `/openapi/music/basic/recommend/songlist/get/v2`
- `/openapi/music/basic/recommend/style/songlist/get`
- `/openapi/music/basic/song/aidj/audio/get`
- `/openapi/music/basic/song/aidj/chat/rcmd`
- `/openapi/music/basic/song/bpm/get`
- `/openapi/music/basic/song/cio/detail/get`
- `/openapi/music/basic/song/daily/style/get`
- `/openapi/music/basic/song/detail/get/v2`
- `/openapi/music/basic/song/distribute/channel/info`
- `/openapi/music/basic/song/download/count/get`
- `/openapi/music/basic/song/downloadurl/get/v2`
- `/openapi/music/basic/song/freely/sing/detail`
- `/openapi/music/basic/song/freely/sing/get`
- `/openapi/music/basic/song/list/get/v2`
- `/openapi/music/basic/song/listen/type/get`
- `/openapi/music/basic/song/lyric/get/v2`
- `/openapi/music/basic/song/lyric/word/by/word/get`
- `/openapi/music/basic/song/music/quality/sound/get`
- `/openapi/music/basic/song/music/quality/sound/sp/get`
- `/openapi/music/basic/song/paid/get`
- `/openapi/music/basic/song/play/intelligence/get`
- `/openapi/music/basic/song/play/record/list`
- `/openapi/music/basic/song/playurl/get/v2`
- `/openapi/music/basic/song/quality/interests/get`
- `/openapi/music/basic/song/quality/interests/start`
- `/openapi/music/basic/song/text/download/get/v2`
- `/openapi/music/basic/song/text/play/get/v2`
- `/openapi/music/basic/sound/effect/list`
- `/openapi/music/basic/sound/effect/list/v2`
- `/openapi/music/basic/sound/effect/use`
- `/openapi/music/basic/sound/effect/use/check`
- `/openapi/music/basic/tuning/tools/config/fetch`
- `/openapi/music/basic/yida/hifi/get`
- `/openapi/music/basic/yida/hifi/module/get`
- `/openapi/music/basic/yida/page/get`
- `/openapi/music/channel/song/detail/get/v2`
- `/openapi/music/channel/song/downloadurl/get/v2`
- `/openapi/music/channel/song/list/get/v2`
- `/openapi/music/channel/song/lyric/get/v2`
- `/openapi/music/channel/song/playurl/get/v2`
- `/openapi/music/song/simulation/get`
- `/openapi/podcast/song/block`

### Playlists & Favorites (26)

- `/openapi/music/basic/playlist/created/get/v2`
- `/openapi/music/basic/playlist/detail/get/v2`
- `/openapi/music/basic/playlist/detail/list`
- `/openapi/music/basic/playlist/oftag/get/v2`
- `/openapi/music/basic/playlist/play/record/list`
- `/openapi/music/basic/playlist/radar/get`
- `/openapi/music/basic/playlist/song/batch/delete`
- `/openapi/music/basic/playlist/song/batch/like`
- `/openapi/music/basic/playlist/song/like/v2`
- `/openapi/music/basic/playlist/song/list/get/v2`
- `/openapi/music/basic/playlist/song/list/get/v3`
- `/openapi/music/basic/playlist/song/list/get/v4`
- `/openapi/music/basic/playlist/song/list/get/v5`
- `/openapi/music/basic/playlist/square/tag/get`
- `/openapi/music/basic/playlist/star/get/v2`
- `/openapi/music/basic/playlist/sub/v2`
- `/openapi/music/basic/playlist/subed/get/v2`
- `/openapi/music/basic/playlist/tag/get/v2`
- `/openapi/music/basic/playlist/unsub/v2`
- `/openapi/music/basic/recommend/playlist/ge`
- `/openapi/music/basic/recommend/playlist/get`
- `/openapi/music/basic/search/playlist/bytag/get/v2`
- `/openapi/music/basic/search/playlist/get/v2`
- `/openapi/music/channel/playlist/batch/get/v2`
- `/openapi/music/channel/playlist/song/list/get/v2`
- `/openapi/stream/common/ai/playlist/create`

### Albums & Artists (15)

- `/openapi/music/basic/album/detail/get/v2`
- `/openapi/music/basic/album/paid/get/v2`
- `/openapi/music/basic/album/play/record/list`
- `/openapi/music/basic/album/sub`
- `/openapi/music/basic/album/subed/get/v2`
- `/openapi/music/basic/album/unsub`
- `/openapi/music/basic/artist/detail/get/v2`
- `/openapi/music/basic/artist/list/get`
- `/openapi/music/basic/artist/subed/get/v2`
- `/openapi/music/basic/artist/zone/get/by/tab`
- `/openapi/music/basic/artist/zone/tab/list`
- `/openapi/music/basic/artists/album/list/bytype/get/v3`
- `/openapi/music/basic/artists/hotsong/list/get/v2`
- `/openapi/music/basic/artists/list/bytype/get/v2`
- `/openapi/music/basic/artists/list/bytype/get/v3`

### Recommendations, Radio & Charts (19)

- `/openapi/music/banner/get`
- `/openapi/music/basic/mix/recent/get`
- `/openapi/music/basic/private/fm/roaming/category`
- `/openapi/music/basic/radio/fm/get/v2`
- `/openapi/music/basic/radio/tag/fm/get/v2`
- `/openapi/music/basic/recommend/daily/image`
- `/openapi/music/basic/recommend/daily/mix`
- `/openapi/music/basic/scene/radio/get`
- `/openapi/music/basic/scene/radio/tags/get`
- `/openapi/music/basic/toplist/get/v2`
- `/openapi/music/heart/beat/widget/get`
- `/openapi/music/interest/trial/card/get`
- `/openapi/music/interest/trial/card/obtained`
- `/openapi/music/orpheus/get/v2`
- `/openapi/podcast/recommend/guess/like/radio/list`
- `/openapi/podcast/scene/fm/list`
- `/openapi/podcast/scene/fm/private/podcast/voices`
- `/openapi/podcast/scene/fm/voice/list`
- `/openapi/radio/gold/sentences/query`

### Comments, Social & Listen Together (28)

- `/openapi/fans-group/add-integral`
- `/openapi/fans-group/cursor-page-fans-group-info`
- `/openapi/fans-group/get-user-fans-group-info-by-artist`
- `/openapi/fans-group/join-fans-group`
- `/openapi/fans-group/list-available-member-fans-group`
- `/openapi/fans-group/list-user-fans-group-info-by-artists`
- `/openapi/listen/together/end`
- `/openapi/listen/together/full/report`
- `/openapi/listen/together/privilege/change/report`
- `/openapi/listen/together/relation/statistics/get`
- `/openapi/listen/together/room/create`
- `/openapi/music/basic/comment/calendar/get`
- `/openapi/music/basic/comments/get/v2`
- `/openapi/music/channel/comments/get/v2`
- `/openapi/music/channel/comments/hot/get/v2`
- `/openapi/music/comment/get-best-comments-by-song`
- `/openapi/music/comment/user/get-user-comments-by-song`
- `/openapi/music/multi/listen/room/qrcode/get`
- `/openapi/music/multi/listen/room/user/list`
- `/openapi/music/multi/reconnect/play/info/get`
- `/openapi/music/multi/reconnect/play/report`
- `/openapi/music/multi/terminal/pc/wps/token/decrypt`
- `/openapi/music/resource/comment/lv1/list/desc`
- `/openapi/music/resource/comment/reply/list/asc`
- `/openapi/score/queryMonthReporPic`
- `/openapi/v1/resource/comments/add`
- `/openapi/v1/resource/comments/reply`
- `/openapi/workflow/callback`

### Podcasts, Voices & Audiobooks (50)

- `/openapi/dj/program/collect/list`
- `/openapi/dj/program/collect/status`
- `/openapi/dj/program/info/list`
- `/openapi/dj/program/news/fall/list`
- `/openapi/dj/program/news/quick/ai_talk`
- `/openapi/dj/program/news/quick/paper/lastest`
- `/openapi/dj/program/news/quick/timelength/voice`
- `/openapi/dj/program/playurl/get`
- `/openapi/djradio/play/progress/record/list`
- `/openapi/podcast/block/content/list`
- `/openapi/podcast/block/list`
- `/openapi/podcast/block/module/content`
- `/openapi/podcast/block/page`
- `/openapi/podcast/block/page/modules`
- `/openapi/podcast/download/url/get/v1`
- `/openapi/podcast/fallspage/my/collect`
- `/openapi/podcast/fallspage/my/page/list`
- `/openapi/podcast/my/collect/count`
- `/openapi/podcast/my/created/voicelist`
- `/openapi/podcast/my/recent/update/voice/list`
- `/openapi/podcast/play/url/get`
- `/openapi/podcast/play/url/get/v1`
- `/openapi/podcast/previous/program/list`
- `/openapi/podcast/ranklist/charts/get`
- `/openapi/podcast/ranklist/square/ranklist/get`
- `/openapi/podcast/ranklist/voice/charts/get`
- `/openapi/podcast/room/columns/get`
- `/openapi/podcast/room/play/location/get`
- `/openapi/podcast/subscribe`
- `/openapi/podcast/unSubscribe`
- `/openapi/podcast/unsubscribe`
- `/openapi/podcast/voicebook/import/create`
- `/openapi/podcast/voicebook/import/status`
- `/openapi/podcast/voicebook/import/voice/batch`
- `/openapi/voice/basic/play/data/upload`
- `/openapi/voice/category/rcmd/program`
- `/openapi/voice/category/rcmd/voice`
- `/openapi/voice/category/second`
- `/openapi/voice/category/top`
- `/openapi/voice/category/voice`
- `/openapi/voice/fall/list`
- `/openapi/voice/get/voicelist`
- `/openapi/voice/list`
- `/openapi/voice/play/record/list`
- `/openapi/voice/sati/resource/list`
- `/openapi/voice/sati/resource/query`
- `/openapi/voice/sati/tag/list`
- `/openapi/voice/similar`
- `/openapi/voicebook/play/progress/record/list`
- `/openapi/voicelist/bought/list`

### Membership, Commerce & Live (15)

- `/openapi/api/game/time/heartbeat`
- `/openapi/game/distribute/ordering/web/checkout/token`
- `/openapi/livestream/game/balance/get`
- `/openapi/livestream/game/combine/submit`
- `/openapi/livestream/game/gift/get`
- `/openapi/livestream/game/gift/present`
- `/openapi/livestream/game/live/anchorinfo/get`
- `/openapi/music-vip-membership/open-platform/iot/vip/info`
- `/openapi/music/basic/order/data/record`
- `/openapi/ordering/web/checkout/token`
- `/openapi/ordering/web/checkout/token/order`
- `/openapi/vippro/vipproduct/commonvip/supplier/refund`
- `/openapi/yunbei/balance`
- `/openapi/yunbei/consume`
- `/openapi/yunbei/deposit`

### Cloud Drive & Files (2)

- `/openapi/music/basic/common/middle/short/url`
- `/openapi/music/resource/short/url/get`

### Infrastructure & Misc (7)

- `/openapi/middle/interest/exchangecode/generate`
- `/openapi/middle/interest/exchangecode/query`
- `/openapi/music/basic/mv/detail/get/v2`
- `/openapi/music/basic/play/data/record`
- `/openapi/music/common/popup/get`
- `/openapi/music/common/recently/heard/get`
- `/openapi/openapi/music/common/popup/get`


---

## Appendix B · References

- Official developer documentation: <https://developer.music.163.com/>
- Documentation category tree: `GET https://interface.music.163.com/api/openplatform/developer/api/doc/category`
- Document detail: `GET https://interface.music.163.com/api/openplatform/developer/apidoc/get?docId=<id>`
- CLI package: `@music163/ncm-cli@0.1.7` (<https://www.npmjs.com/package/@music163/ncm-cli>)
- Official Agent Skills: <https://github.com/NetEase/skills>
- Local reference material (available on this machine only): `ncm-cli-api.md`, `analysis/ncm-openapi-reference.md`, `analysis/ncm-openapi-endpoints-all.md` (under `E:/workplace/ncm/`)
