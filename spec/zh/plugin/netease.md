# 网易云音乐开放平台接口使用规范

[English](../../en/plugin/netease.md) · 简体中文

> 本文面向 **agent**：描述「要完成一个音乐操作，该调哪个接口、传什么参数、拿到什么、出错怎么办」。
> 只写可执行的规则与参数，不写实现背景。签名、公共参数、错误码以开放平台官方文档为准；
> CLI 命令面以 `@music163/ncm-cli` 0.1.7 为准。

---

## 0. 先记住这 6 条

1. **两条调用路径**：能执行 shell 就优先用 `ncm-cli`（签名、device、token 刷新由它负责）；需要服务端集成或 CLI 未覆盖的端点，才按 §3 自己发 HTTP。
2. **ID 有两套**：加密 ID（32 位 hex，所有 API 参数都用它）和明文 ID（数字，用于拼 `music.163.com` 网页链接、唤起客户端，以及 `ncm-cli play --original-id`）。**禁止在 API 参数里用明文 ID，也禁止在链接里用加密 ID**。
3. **除匿名登录、二维码 key、`grantCode` 换 token 外，所有接口都要 `accessToken`**；二维码状态轮询必须带**匿名** token。先拿 token，再调业务接口。
4. **签名覆盖除 `sign` 外的全部非空公共参数**：按参数名字典序拼 `k=v&...`，用 RSA-SHA256 私钥签名，输出 base64。
5. **`bizContent` 与 `device` 都是「JSON 序列化后的字符串」**，不是嵌套对象。
6. **网关按 appId 逐接口授权**：`code 300 / 301` 表示没权限，不是参数错误，重试无用，只能换有该接口权限的 appId 或申请开通；manifest 接口本身也受同一授权控制。

---

## 1. 选哪条路径

| | `ncm-cli`（推荐给能执行 shell 的 agent） | 直接 HTTP OpenAPI |
|---|---|---|
| 入口 | `ncm-cli <command> [args]` | `https://openncm.music.163.com/openapi/...` |
| 签名 / device / token | CLI 自动处理 | 自己实现（§3） |
| 命令/端点来源 | `ncm-cli commands`（静态命令 + 服务端下发的动态命令） | 本文 §4–§5 + 附录 A |
| 适合 | 搜索、播放、播控、歌单、云盘上传、TUI | 服务端集成、批量任务、CLI 未暴露的端点 |
| 前置 | Node.js ≥ 18、appId / privateKey、登录 | appId / privateKey（PKCS#8 PEM）、accessToken |

两者底层是同一套协议：`ncm-cli` 的动态命令最终会翻译成 §3 描述的 HTTP 请求（§6.3）。

---

## 2. 凭证与准备

| 凭证 | 用途 | 获取 |
|---|---|---|
| `appId` | 应用 ID，所有请求的公共参数 | 开放平台控制台 |
| `privateKey` | RSA 私钥，仅用于本地签名，绝不发送到服务端 | 开放平台控制台，PKCS#8 PEM |
| `appSecret` (`clientSecret`) | 刷新 accessToken 时需要 | 开放平台控制台 |
| `accessToken` | 登录态，除匿名登录、二维码 key、grantCode 换 token 外必传 | §4 |

CLI 侧准备命令：

```bash
npm install -g @music163/ncm-cli
ncm-cli --version                          # 需 Node.js >= 18
ncm-cli config set appId <APP_ID>
ncm-cli config set privateKey <PEM_文件路径或PEM内容>
ncm-cli config set player mpv              # 或 orpheus（仅 macOS 客户端播放）
ncm-cli login --check                      # 检查登录态；0.1.7 起支持主动续期
ncm-cli login --background                 # 后台扫码登录，把链接交给用户
```

- `privateKey` 写成路径时，CLI 读取文件内容；写成 PEM 文本时直接使用。
- 环境变量同样生效：`NETEASE_APP_ID`、`NETEASE_PRIVATE_KEY`、`NETEASE_APP_SECRET`。
- 播放命令额外需要 `mpv`（`mpv --version` 检查）；`ncm-cli config get player` 查看当前播放器。
- **前置条件**：appId 必须在开放平台开通了目标接口的权限，并配置好加密（appSecret/公钥）。否则所有数据接口返回 `code 300/301`。

---

## 3. HTTP 协议（自建客户端）

### 3.1 域名与方法

- Base URL：**默认用** `https://openncm.music.163.com`（ncm-cli 0.1.7 使用）。官方开发者文档示例使用 `https://openapi.music.163.com`，两者路径与参数一致；仅当默认域名对目标路径返回 404 时才换用另一个。
- **GET**：全部公共参数拼到 query string，用 `URLSearchParams` 做 URL 编码。
- **POST**：**整个公共参数对象**（含 `appId`、`signType`、`timestamp`、`device`、`bizContent`、`accessToken`、`sign`）作为 JSON body，`Content-Type: application/json`。不是只把 `bizContent` 放进 body。
- 官方文档中标注 `GET/POST` 的接口，两种都可用；**默认用 GET**（§3.5 示例即 GET），只有写操作或接口卡片明确标 POST 时才用 POST。

### 3.2 公共参数

| 参数 | 类型 | 必选 | 说明 |
|---|---|---|---|
| `appId` | String | 是 | 应用 ID |
| `signType` | String | 是 | 固定 `RSA_SHA256` |
| `timestamp` | String | 是 | 毫秒时间戳 `String(Date.now())`；与服务端时间偏差需在 5 分钟内 |
| `device` | String | 是 | 设备信息 JSON 序列化后的字符串 |
| `bizContent` | String | 是 | 业务参数 JSON 序列化后的字符串；无参数时传 `{}` |
| `accessToken` | String | 按接口 | 登录令牌；仅匿名登录、二维码 key、grantCode 换 token 可不带，其余必传；二维码状态轮询必须用匿名 token |
| `sign` | String | 是 | 签名，见 §3.4 |

`device` 对象：

| 字段 | 必选 | CLI 0.1.7 取值 | 说明 |
|---|---|---|---|
| `deviceType` | 是 | `openapi` | 设备类型 |
| `os` | 是 | `ncmcli` | 操作系统标识 |
| `appVer` | 是 | `0.1.7` | 必须 `x.x.x` 三段数字 |
| `channel` | 是 | `ncmcli` | 渠道标识 |
| `model` | 是 | `Windows_x64_cli` | `<平台>_<source>` |
| `brand` | 是 | `ncmcli` | 品牌标识 |
| `osVer` | 是 | `10.0.26300` | 系统版本 |
| `clientIp` | 是 | 公网 IP | 真实出口 IP；缺失会返回 `2511` |
| `deviceId` | 是 | `ncmcli_<随机>` | 每台设备唯一，≤64 字符，不得含 `=`、`;`、空格、`+`、`|`；与匿名 token 强绑定并持久化 |
| `netStatus` | 官方要求 | `4g` | `wifi/2g/3g/4g/5g` |
| `flowFlag` | 否 | `init` | 是否计入日活 |

`deviceType` / `os` / `channel` / `brand` 的具体取值由开放平台分配；**不清楚登记值时直接使用上表的 CLI 0.1.7 取值**（已验证可用），用 CLI 时同样复用。

### 3.3 请求头与响应

```text
User-Agent: ncm-0.1.7
Referer: https://music.163.com/
Content-Type: application/json        # 仅 POST
X-User-Input: <encodeURIComponent(会话摘要)>   # CLI 命令附加 --userInput 时，见 §6.3
```

响应统一为 JSON：`{ "code": 200, "message": "", "data": {...}, "subCode": null }`。
**只有 `code === 200` 才是成功**，业务数据在 `data`。列表型响应的数据在 `data.recordCount`（总数）与 `data.records`（数组）中；部分接口直接把数组放在 `data` 或 `data.tracks` 下，按接口卡片说明取。

### 3.4 签名算法

1. 收集公共参数（**不含 `sign`**），剔除 `null` / `undefined` / 空串。只签公共参数；`X-User-Input` 等请求头不参与签名。
2. 每个值转成字符串（`device`、`bizContent` 保持序列化后的 JSON 原文，不做额外转义）。
3. 按参数名做字典序（ASCII 升序）排序。
4. 拼成 `k1=v1&k2=v2&...`，**不 URL 编码**。
5. 用 PKCS#8 RSA 私钥做 `SHA256withRSA` 签名，结果取 **base64**，写入 `sign` 参数。
6. GET 发送时再对整个 query 做 URL 编码（`URLSearchParams` 自动处理）；POST 直接把 `sign` 放进 JSON body。

Node.js：

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

Python：

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

### 3.5 端到端最小示例

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
  throw new Error("无法探测公网 IP，请检查网络后重试");
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

// 1) 匿名登录拿 token（此接口不传 accessToken）
const anon = await call("/openapi/music/basic/oauth2/login/anonymous",
  { clientId: APP_ID }, { method: "POST" });
const token = anon.accessToken;   // 与 deviceId 绑定，缓存复用

// 2) 搜索歌曲
const found = await call("/openapi/music/basic/search/song/get/v3",
  { keyword: "晴天", limit: 30, offset: 0, qualityFlag: true },
  { accessToken: token });

// 3) 用加密 ID 取歌曲详情与播放地址
const detail = await call("/openapi/music/basic/song/detail/get/v2",
  { songId: found.records[0].id, withUrl: true, bitrate: 320 },
  { accessToken: token });
// detail.playUrl 是播放地址；detail.openApiTraceInfo 用于全曲试听上报
```

`clientIp` 必须是真实公网出口 IP：示例用 `detectPublicIp()` 运行时探测，不要填假地址，否则返回 `2511`。

---

## 4. 认证与 token 生命周期

**默认顺序**：匿名登录（游客能力）→ 如需用户资产则扫码登录 → token 过期用 refreshToken 刷新 → 刷新失败重登。

### `/openapi/music/basic/oauth2/login/anonymous` — 匿名登录
- **方法**：POST（官方允许 GET/POST）　**CLI**：由 `ncm-cli login` 内部调用
- **bizContent**：`{"clientId":"<appId>"}`（与 `appId` 相同）　**accessToken**：不传
- **返回**：`data.accessToken`（永不过期，**这里是字符串**）、`data.refreshToken`、`data.expireTime`、`data.scopes`
- **规则**：同一 `deviceId` 对应固定匿名 token，必须本地缓存并复用；`deviceId` 变了就是新用户。游客态只能读内容与播放，读不到用户资产。

### `/openapi/music/basic/user/oauth2/qrcodekey/get/v2` — 获取登录二维码
- **方法**：GET　**accessToken**：不传　**bizContent**：`{"type":2,"expiredKey":"300"}`（`type` 固定 2；`expiredKey` 是二维码有效秒数，固定 300）
- **返回**：`data.qrCodeUrl`（交给用户打开/扫码）、`data.uniKey`（轮询用 key）
- **规则**：二维码 5 分钟有效，只能扫一次；`qrCodeUrl` 可能是短链也可能是长链，预留 8–512 字符。

### `/openapi/music/basic/oauth2/device/login/qrcode/get` — 轮询二维码状态
- **方法**：GET　**bizContent**：`{"key":"<uniKey>","clientId":"<appId>"}`
- **accessToken**：**必须传匿名 token**（用实名 token 会返回 400 `device has login success`）
- **返回**：`data.status`、`data.msg`、`data.accessToken.{accessToken,refreshToken,expireTime}`。**注意此处 `data.accessToken` 是对象**，新 token 在 `data.accessToken.accessToken`；与匿名登录处的字符串 `data.accessToken` 结构不同。
- **status**：`800` 不存在/已过期、`801` 等待扫码、`802` 授权中、`803` 登录成功、`804` 未知错误
- **规则**：每 2–3 秒轮询一次，直到 `803`（提取 token）、`800`（重新取二维码）或超时 5 分钟；`804` 视为失败，重新取二维码。

### `/openapi/music/basic/user/oauth2/token/refresh/v2` — 刷新 token
- **方法**：GET　**bizContent**：`{"clientId":"<appId>","clientSecret":"<appSecret>","refreshToken":"<RT>"}`
- **返回**：`data.accessToken`、`data.refreshToken`、`data.expiresTime`（秒）
- **规则**：accessToken 默认 7 天、refreshToken 默认 20 天，刷新时两者一起续期；`clientId` 必须与 refreshToken 的来源 appId 一致；匿名 token 不能刷新（返回 1406）。

### 其他认证接口
| 端点 | 方法 | bizContent | 用途 |
|---|---|---|---|
| `/openapi/music/basic/user/oauth2/token/get/v2` | GET/POST | `{"grantCode":"<回调code>"}` | H5 / 唤端登录回调 code 换 token；此接口不带 accessToken |
| `/openapi/music/basic/user/info/bytoken/get/v2` | POST | `{"accessToken":"<AT>"}` | 用 token 查用户信息 |
| `/openapi/music/basic/do/noting` | GET | `{"clientId":"<appId>"}` | 配置向导校验凭证是否有效 |
| `/openapi/music/basic/user/profile/get/v2` | GET | `{}` | 当前登录用户资料，见 §5.3 |

**token 异常处理**：先按 §8.2 确认 `message` 指向 token 过期；确认后，**实名 token** 才调刷新接口，**匿名 token 直接重新扫码登录**（匿名 token 不能刷新）。刷新仍失败或返回 `1407` / `1408` 也直接重登。`accessToken` / `refreshToken` 按无上限字符串存储（至少能容纳 512 字符）。

---

## 5. 接口卡片

**读法**：`bizContent` 给的是可直接用的 JSON；`songId` / `playlistId` / `albumId` / `artistId` 一律是**加密 ID**；未写 CLI 命令的端点由服务端 manifest 下发（见 §5.6）。除特别说明外均需 `accessToken`；**读取我的资料 / 红心歌单、创建或编辑歌单、红心操作需要实名 token**，匿名 token 会失败。

### 5.1 搜索与发现

| 意图 | 端点 | 关键 bizContent |
|---|---|---|
| 搜歌曲 | `/openapi/music/basic/search/song/get/v3` | `keyword`, `limit`, `offset` |
| 搜专辑 | `/openapi/music/basic/search/album/get/v2` | `keyword`, `limit`, `offset` |
| 搜歌手 | `/openapi/music/basic/search/artists/get/v2` | `keyword`, `limit`, `offset` |
| 搜歌单 | `/openapi/music/basic/search/playlist/get/v2` | `keyword`, `limit`, `offset` |
| 按标签找歌单 | `/openapi/music/basic/search/playlist/bytag/get/v2` | `tag`, `limit`, `offset` |
| 综合搜索（多类型一次返回） | `/openapi/music/basic/complex/search` | `keyword` |
| 热搜词 | `/openapi/music/basic/search/hot/keyword/get/v2` | `limit` |
| 搜索联想词 | `/openapi/music/basic/search/suggest/keyword/get/v2` | `keyword` |

#### `/openapi/music/basic/search/song/get/v3` — 搜索歌曲
- **方法**：GET / POST　**CLI**：`ncm-cli search song --keyword "xxx" --userInput "..."`
- **bizContent**：`{"keyword":"晴天","limit":30,"offset":0,"qualityFlag":true}`
- **参数**：`keyword` String 必填｜`limit` Int 必填（建议 ≤100）｜`offset` Int 必填（从 0 开始）｜`qualityFlag` Boolean 可选，是否下发音质
- **返回**：歌曲对象数组，通常在 `data.records`（总数 `data.recordCount`）；字段见 §9.1。若响应直接是数组，按数组取。
- **注意**：老接口是 `search/song/get/v2`，新接入一律用 v3；搜索结果的 `id` 是加密 ID，后面所有接口都用它。

#### `/openapi/music/basic/complex/search` — 综合搜索
- **方法**：GET / POST　**bizContent**：`{"keyword":"精卫","identityFlag":true,"subCountFlag":true}`
- **参数**：`keyword` String 必填｜`qualityFlag` Boolean 可选｜`identityFlag` / `subCountFlag` Boolean 可选（艺人身份、粉丝数）
- **返回**：`data` 含 `songs` / `artists` / `albums` / `playlists` / `voicelist` / `bestMatchResources` / `bestMatchResourceTypes`
- **用法**：用户意图不明确、想一次拿到多类型候选时优先用它；明确只要歌曲时用 `search/song/get/v3`。

#### 其余搜索接口
| 端点 | 方法 | bizContent | 返回 / 规则 |
|---|---|---|---|
| `/openapi/music/basic/search/album/get/v2` | GET/POST | `{"keyword":"xxx","limit":30,"offset":0}` | 专辑列表；`limit` 最多 300，建议 ≤100 |
| `/openapi/music/basic/search/artists/get/v2` | GET/POST | `{"keyword":"xxx","limit":30,"offset":0,"identityFlag":true,"subCountFlag":true}` | 艺人列表；两个可选开关默认 false |
| `/openapi/music/basic/search/playlist/get/v2` | GET/POST | `{"keyword":"xxx","limit":30,"offset":0}` | 歌单列表 |
| `/openapi/music/basic/search/playlist/bytag/get/v2` | GET/POST | `{"tag":"欧美","limit":10,"offset":0}` | 标签歌单；`limit` 最多 500 |
| `/openapi/music/basic/search/hot/keyword/get/v2` | GET/POST | `{"limit":10}` | 热搜词；无 `keyword` |
| `/openapi/music/basic/search/suggest/keyword/get/v2` | GET/POST | `{"keyword":"邓"}` | 联想词；适合输入补全，不用于正式检索 |

### 5.2 歌曲、歌词与播放

#### `/openapi/music/basic/song/detail/get/v2` — 歌曲详情 + 播放 URL
- **方法**：GET（官方允许 GET/POST）　**bizContent**：`{"songId":"<encId>","withUrl":true,"bitrate":320}`
- **参数**：`songId` String 必填｜`withUrl` Boolean 必填，是否需要播放 URL｜`bitrate` Int 可选｜`effects` String 可选（`eac3` / `ac4` 杜比）｜`qualityFlag` Boolean 可选｜`level` String 可选（`vivid` / `sky` / `jymaster` / `jyeffect`，拿不到自动降级）｜`immerseType` String 可选（`sky` 必传：`c51` / `ste` / `aac`）｜`languageFlag` Boolean 可选｜`extFlags` String 可选，值是 JSON 序列化后的字符串，如 `"{\"hqScene\":\"normal\"}"` 或 `"{\"chorusFlag\":true}"`
- **bitrate 枚举**：`128` 标准、`320` 极高、`999` 无损、`1999` Hi-Res；不传则取用户可听的最高档。单位是档位值，不是 bps。
- **返回**：歌曲对象；`playUrl` 是播放地址、`playUrlExpireTime` 是地址过期时间、`br` 是实际码率、`gain` / `peak` 是增益、`openApiTraceInfo` 是链路 ID（全曲试听上报需透传）
- **注意**：先检查 `visible` / `playFlag`，`false` 时不要播放；返回受限时按 `message` / `subCode` 处理，见 §8。

#### `/openapi/music/basic/song/playurl/get/v2` — 只要播放地址
- **方法**：GET / POST　**bizContent**：`{"songId":"<encId>","bitrate":320}`
- **参数**：`songId` String 必填｜`bitrate` Int 可选（默认 320，取不到会降级）｜`effects` / `level` / `immerseType` 与 `song/detail/get/v2` 相同
- **返回**：`data.url` 为播放地址（歌曲详情接口里同义字段叫 `playUrl`）。
- **用法**：只需要 URL、不需要完整歌曲信息时用它；批量场景用下一个接口。

#### `/openapi/music/basic/batch/song/playurl/get` — 批量播放地址
- **方法**：GET / POST　**bizContent**：`{"songIds":["<encId1>","<encId2>"],"bitrate":128,"effects":"eac3"}`
- **参数**：`songIds` List\<String> 必填，**≤500 个**｜`bitrate` Int 可选｜`effects` String 可选

#### `/openapi/music/basic/song/lyric/get/v2` — 逐行歌词
- **方法**：GET / POST　**bizContent**：`{"songId":"<encId>"}`
- **返回**：`data.songId`、`lyric`（LRC 时间戳文本）、`transLyric`（翻译）、`txtLyric`（非滚动文本）、`romalrc`（罗马音）、`noLyric`、`pureMusic`
- **规则**：`noLyric=true` 或 `pureMusic=true` 时不要向用户展示歌词；`transLyric` 可能为 `null`。

#### `/openapi/music/basic/song/lyric/word/by/word/get` — 逐字歌词
- **方法**：GET / POST　**bizContent**：`{"songId":"<encId>"}`
- **规则**：仅头部曲库有逐字歌词；拿不到时回退到 `song/lyric/get/v2`。

#### `/openapi/music/basic/play/data/record` — 播放数据回传
- **方法**：POST（官方允许 GET/POST）　**CLI**：播放控制内部自动上报
- **规则**：一次播放要报**两条**：开始时 `action="startplay"`，结束时 `action="play"`（结束报文必须显式改成 `play`）。搜索/推荐/歌单返回的 `alg`、歌曲详情返回的 `openApiTraceInfo` 有值就原样透传；搜索场景无 `alg` 时填 `search`。

开始播放：

```json
{
  "id": "<encId>", "action": "startplay", "type": "song",
  "bitrate": 320, "file": 4, "startLogTime": 1730000000000,
  "alg": "<上游返回的alg>", "sourceId": "search", "sourceType": "search"
}
```

结束播放（复制开始报文，把 `action` 改成 `play`，再追加 `time`、`end`）：

```json
{
  "id": "<encId>", "action": "play", "type": "song",
  "bitrate": 320, "file": 4, "startLogTime": 1730000000000,
  "alg": "<上游返回的alg>", "sourceId": "search", "sourceType": "search",
  "time": 210, "end": "playend"
}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `id` | 是 | 当前资源加密 ID（播客用 `encVoiceId`） |
| `action` | 是 | `startplay` 开始，`play` 结束 |
| `type` | 是 | `song` 歌曲，`dj` 播客 |
| `bitrate` | 是 | `128`/`320`/`999`/`1999`/`3999`/`4999`/`4099`/`2999`/`5999`（后 5 个高档位仅用于上报）；播客固定 `128` |
| `file` | 是 | `0` 下载、`1` 本地、`2` 缓存、`3` 云盘、`4` 线上；不确定填 `4` |
| `startLogTime` | 是 | 开始播放毫秒时间戳 |
| `time` | 结束必填 | 实际收听秒数（不含暂停），>0 且 ≤ 歌曲时长；倍速与跳转按实际收听时长累计 |
| `end` | 结束必填 | `playend` 自然结束｜`interrupt` 被第三方打断｜`exception` 播放异常｜`ui` 用户切歌/暂停；不确定填 `playend` |
| `alg` | 是 | 上游（搜索/推荐/歌单）返回的算法字段，有则原样透传；无值时搜索场景填 `search` |
| `sourceId` / `sourceType` | 是 | 播放来源；`sourceType` 取 `list`/`album`/`dailySongRecommend`/`userfm`/`search`/`djradio`，`sourceId` 为对应歌单/专辑 ID 或该类型名 |
| `isAudition` | 否 | `1` 片段试听、`2` 全曲试听 |
| `auditionStart` / `auditionEnd` | 否 | 试听片段起止秒，取自上游接口 |
| `openApiTraceInfo` | 否 | 歌曲详情返回的链路 ID；全曲试听上报时透传，无值则不传 |
| `categoryId` / `ext` | 否 | 播客分类 / 设备识别码 JSON 字符串 |

### 5.3 用户与歌单

#### 用户资料
| 端点 | 方法 | bizContent | 返回 |
|---|---|---|---|
| `/openapi/music/basic/user/profile/get/v2` | GET | `{}` | `id`, `nickname`, `avatarUrl`, `gender`(0未知/1男/2女), `signature`, `redVipLevel`, `vipDetail` |
| `/openapi/music/basic/user/info/bytoken/get/v2` | POST | `{"accessToken":"<AT>"}` | 令牌对应的用户信息 |

#### 歌单读取
| 端点 | 方法 | bizContent | 返回 / 规则 |
|---|---|---|---|
| `/openapi/music/basic/playlist/star/get/v2` | GET | `{}` | 红心歌单对象（`id`, `name`, `trackCount`, `coverImgUrl`, …）；偏好分析入口 |
| `/openapi/music/basic/playlist/created/get/v2` | GET | `{"limit":100,"offset":0}` | 我创建的歌单；`recordCount` + `records`，`limit` ≤500 |
| `/openapi/music/basic/playlist/subed/get/v2` | GET | `{"limit":100,"offset":0,"originalCoverFlag":false}` | 我收藏的歌单；`originalCoverFlag` 控制无水印封面 |
| `/openapi/music/basic/playlist/detail/get/v2` | GET/POST | `{"playlistId":"<encId>"}` | 歌单详情；可选 `originalCoverFlag` / `newCoverFlag` |
| `/openapi/music/basic/playlist/song/list/get/v3` | GET | `{"playlistId":"<encId>","limit":30,"offset":0,"qualityFlag":true}` | 歌曲列表；`limit` ≤500，建议每次 30 |
| `/openapi/music/basic/playlist/song/list/get/v5` | GET/POST | 同上 | 返回 `data.tracks` 并附带歌单信息，适合一次拿全 |

`specialType`：`0` 普通、`5` 红心、`10` 置顶、`20` 尾部、`100` 官方、`200` 视频、`300` 分享。
红心歌单曲目里的 `extMap.addTime`（毫秒）是收藏时间，`songTag[]` 是曲风标签，可用于偏好统计。

#### 歌单写入
| 端点 | 方法 | bizContent | 说明 |
|---|---|---|---|
| `/openapi/music/basic/playlist/create` | POST | `{"playlistName":"跑步"}` | 创建歌单；**CLI**：`ncm-cli playlist create --playlistName "xxx" --userInput "..."`。HTTP 直调时以 manifest 下发的 `bodyParams` 为准 |
| `/openapi/music/basic/playlist/song/batch/like` | POST | `{"playlistId":"<encId>","songIdList":["<encId>","<encId>"]}` | 批量加歌 |
| `/openapi/music/basic/playlist/song/batch/delete` | POST | `{"playlistId":"<encId>","songIdList":["<encId>"]}` | 批量删歌，上限 500；仅创建者可编辑 |
| `/openapi/music/basic/playlist/song/like/v2` | POST | `{"songId":"<encId>","isLike":true}` | 红心/取消红心；`isLike=false` 为删除 |
| `/openapi/music/basic/playlist/sub/v2` | POST | `{"id":"<encId>"}` | 收藏歌单 |
| `/openapi/music/basic/playlist/unsub/v2` | POST | `{"id":"<encId>"}` | 取消收藏歌单 |

**创建后的 ID**：`playlist/create` 返回 `data` 中的新歌单对象，取其歌单 ID（加密 ID）作为下一步的 `playlistId`；若响应未直接给出，调 `playlist/created/get/v2` 取最新一条。给用户链接所需的明文 ID（歌单对象里通常也叫 `originalId`）从 `playlist/detail/get/v2` 或 `created/get/v2` 的响应中取；取不到就不要编造链接。

**写入后校验**：重新调用 `playlist/detail/get/v2`，确认 `data.trackCount` 增加了本次加入的曲目数，再向用户报告结果。

### 5.4 专辑、艺人与榜单

| 端点 | 方法 | bizContent | 返回 / 规则 |
|---|---|---|---|
| `/openapi/music/basic/album/detail/get/v2` | POST | `{"albumId":"<encId>","descFlag":true}` | 专辑详情；`descFlag` 控制是否带简介 |
| `/openapi/music/basic/album/song/list/get/v3` | GET/POST | `{"albumId":"<encId>","limit":30,"offset":0}` | 专辑曲目 |
| `/openapi/music/basic/artist/detail/get/v2` | GET/POST | `{"artistId":"<encId>","extFlag":true}` | 艺人详情；可选 `personalFlag` / `creatorRoleFlag` / `subCountFlag` / `identityFlag` |
| `/openapi/music/basic/artists/hotsong/list/get/v2` | GET/POST | `{"artistsId":"<encId>","top":50,"qualityFlag":true}` | 艺人热门歌曲；参数名是 `artistsId`（复数），`top` 为前 N 首、最多 60 |
| `/openapi/music/basic/toplist/get/v2` | GET/POST | `{}` | 榜单列表；`category` 取 `OFFICIAL`/`FEATURE`/`MUSIC_STYLE`/`GLOBAL`/`MORE`/`LANGUAGE` |

### 5.5 工具与文件

| 端点 | 方法 | bizContent | 说明 |
|---|---|---|---|
| `/openapi/music/basic/common/middle/short/url` | POST | `{"sourceUrl":"<长链>","expireTimeSeconds":1296000}` | 长链转短链，返回 `data.shortUrl`；默认有效期 15 天 |
| `/openapi/music/cloud/file/upload/check` | POST | 由 CLI 下发 | 上传前秒传检查 |
| `/openapi/whale/nos/upload/token`、`/openapi/whale/nos/upload/token/docid` | GET | 由 CLI 下发 | 取 NOS 上传凭证；文件数据直传 `wanproxy.127.net` / `wanproxy-web.127.net` |
| `/openapi/music/cloud/file/upload` | POST | 由 CLI 下发 | 提交云盘上传 |
| `/openapi/music/cloud/file/upload/status` | POST | 由 CLI 下发 | 查询后台上传任务状态 |
| `/openapi/music/cloud/file/download` | GET | 由 CLI 下发 | 云盘文件下载 |

云盘上传的参数由 CLI / manifest 下发，自建客户端应使用 `ncm-cli cloudupload --help` 获取当次参数，不要自行拼装。

### 5.6 动态命令清单（manifest）

- **端点**：`POST /openapi/v1/ncm/cli/manifest`，**bizContent**：`{"cli_version":"0.1.7","cached_versions":{}}`，需带 `accessToken`。
- **返回**：服务端下发的命令树；CLI 缓存到 `~/.config/ncm-cli/cache/manifest.json`。
- **用途**：搜索、歌词、笔记发布、创建歌单等命令的具体端点与参数由它下发；`ncm-cli commands` 展示的是「静态命令 + manifest 动态命令」。该接口同样受 appId 授权控制。

---

## 6. CLI 命令面

### 6.1 静态命令

```bash
ncm-cli --help                         # 全部可用命令
ncm-cli commands                       # 当前命令树（含动态命令）
ncm-cli <command> --help               # 单个命令的准确参数，参数不要猜
ncm-cli configure                      # 配置向导：appId / privateKey / 播放器
ncm-cli config get <key>               # 读取配置，如 config get player
ncm-cli config set <key> <value>       # 写入配置
ncm-cli login --check                  # 登录态检查
ncm-cli login --background             # 后台扫码登录，把链接发给用户
ncm-cli search song --keyword "xxx"    # 搜索歌曲
ncm-cli playlist create --playlistName "xxx"
ncm-cli play --song --encrypted-id <加密ID> --original-id <明文ID>
ncm-cli pause | resume | next | prev | state
ncm-cli volume 60                      # 0-100
ncm-cli tui                            # 全屏播放器
ncm-cli cloudupload file --file <路径> # 云盘文件上传
ncm-cli diag report                    # 上报诊断日志
```

### 6.2 Agent 执行规则

1. 首次使用前：`ncm-cli --version` 检查安装，`ncm-cli login --check` 检查登录态。
2. **每次会话先取命令树**：`ncm-cli commands`。命令集合会随服务端 manifest 变化，不要依赖记忆中的命令。
3. **参数不猜**：需要某个命令的参数时执行 `ncm-cli <command> --help`。
4. 除播放控制类命令外，调用动态命令时附加 `--userInput "<用户最近一轮输入的简短概述，≤200 字>"`；该值会变成 `X-User-Input` 请求头（§6.3）。
5. 输出含「请先登录 / 未授权」时，执行 `ncm-cli login --background` 并把链接给用户，跑完整个登录流程后再重试原命令。
6. 输出「请求总量超限」时，直接把该原因转述给用户并停止，不要重试或二次加工。
7. 返回给用户的资源尽量给可点击链接，且链接里用**明文 ID**：
   `https://music.163.com/#/song?id=<明文ID>`、`.../#/playlist?id=<明文ID>`、`.../#/album?id=<明文ID>`、`.../#/artist?id=<明文ID>`。
8. 歌曲 `visible=false` 时**禁止**播放或加入队列。多首结果时先播第一首，其余加入播放队列（队列命令以 `ncm-cli commands` 输出为准，不要猜）。

### 6.3 CLI 命令如何翻译成 HTTP（动态命令执行器）

manifest 中每个命令条目包含：

```text
path          : "/openapi/..."（可含 {pathParam} 占位符）
http_method   : "GET" | "POST"
auth_required : bool
auth_provider : "netease"（默认）
pathParams / queryParams / bodyParams : 按位置分类的参数定义
命令名、描述、参数校验（zod）
```

执行时：

- `GET`：`queryParams` 拼到 query string。
- `POST`：`{...queryParams, ...bodyParams}` 组成 JSON body。
- `--userInput "<摘要>"` 转成请求头 `X-User-Input: <encodeURIComponent(摘要)>`。
- 公共参数（`appId` / `signType` / `timestamp` / `device` / `bizContent` / `accessToken` / `sign`）由客户端统一注入并签名，见 §3。
- **注意**：本节描述 CLI 动态命令执行器的内部组装方式，**不是自建 HTTP 客户端的 body 格式**；自建客户端一律按 §3.1 构造（GET 拼公共参数、POST 发整个公共参数对象）。

---

## 7. Agent 决策流程

### 7.1 意图 → 接口速查

| 用户想 | 调这个 | 前置 |
|---|---|---|
| 找某首歌 | `search/song/get/v3` | 任意 token |
| 一次找歌/歌手/专辑/歌单 | `complex/search` | 任意 token |
| 看歌词 | `song/lyric/get/v2` | 加密 `songId` |
| 播放 / 拿音频地址 | `song/detail/get/v2`（`withUrl:true`）或 `song/playurl/get/v2` | 加密 `songId` + 检查 `visible` |
| 批量拿播放地址 | `batch/song/playurl/get` | ≤500 个加密 ID |
| 看我的资料 / 红心 | `user/profile/get/v2` / `playlist/star/get/v2` | **实名 token** |
| 看歌单内容 | `playlist/detail/get/v2` → `playlist/song/list/get/v3` | 加密 `playlistId` |
| 建歌单并加歌 | `playlist/create` → `playlist/song/batch/like` | 实名 token |
| 收藏/取消红心 | `playlist/song/like/v2` | 实名 token |
| 分析偏好 | `playlist/star/get/v2` → `playlist/song/list/get/v3`（取 `songTag` / `extMap.addTime`） | 实名 token |
| 生成短链 | `common/middle/short/url` | 任意 token |

### 7.2 典型任务：搜索并播放一首歌

```text
1. 准备：config set appId/privateKey → login --check（未登录则 login --background）
2. 取命令树：ncm-cli commands；确认 search song 的参数（--help）
3. 搜索：ncm-cli search song --keyword "xxx" --userInput "..."
        或 HTTP §5.1，得到歌曲列表
4. 过滤：丢弃 visible=false / playFlag=false 的结果，取第一条
5. 记录两个 ID：id = 加密 ID（后续 API 用），originalId = 明文 ID（链接用）
6. 取详情/播放地址：song/detail/get/v2 {songId: id, withUrl: true, bitrate: 320}
7. 播放：ncm-cli play --song --encrypted-id <id> --original-id <originalId>
        多首时先播第一首，其余进队列
8. 上报：play/data/record 先 action=startplay，播放结束再 action=play（透传 alg）
9. 回复用户：歌名 + https://music.163.com/#/song?id=<originalId>
```

### 7.3 典型任务：按偏好建歌单

```text
1. 实名登录（匿名 token 读不到用户资产）
2. playlist/star/get/v2 → 拿红心歌单 id
3. playlist/song/list/get/v3 {playlistId, limit:200, offset:0} → 取 songTag / addTime 做偏好统计
4. 用偏好关键词多次 search/song/get/v3（或 complex/search），去重、过滤 visible=false
5. playlist/create {playlistName} → 拿新歌单 id
6. playlist/song/batch/like {playlistId, songIdList}
7. playlist/detail/get/v2 → 校验 trackCount
8. 回复用户：https://music.163.com/#/playlist?id=<明文ID>
```

---

## 8. 错误码与处理

### 8.1 公共错误码

| code | 含义 | agent 该做什么 |
|---|---|---|
| `200` | 成功 | 读 `data` |
| `300` / `301` | 应用未授权当前接口 | 停止重试；换有该接口权限的 appId 或申请开通。manifest 接口也受同一授权控制，不能作为绕过手段 |
| `401` / `402` | 非法参数 / 非法业务参数 | 检查 `bizContent` 字段名、类型、必填项 |
| `404` | 接口或资源未找到 | 检查端点路径、ID 是否有效；路径确认无误时换用 §3.1 的另一个 Base URL |
| `500` | 业务/系统异常 | 可重试 1 次，仍失败则转述 `message` |
| `1401` | 不支持的 HTTP 方法 | GET/POST 换一种 |
| `1402` / `1403` / `1405` | 非法 sign / signType / appId | 检查私钥、签名串、appId |
| `1404` | 非法 timestamp | 校准本机时间（与北京时间偏差 <5 分钟） |
| `1406` | accessToken 过期或 refreshToken 过期 | 先按 §8.2 确认 `message` 指向 token 过期；实名 token 调 `token/refresh/v2`，匿名 token 直接重新登录；刷新仍失败也重新登录 |
| `1407` / `1408` | 账号异常 / accessToken 无效 | 直接重新扫码登录，不要刷新 |
| `2402`–`2406` | 缺少 sign / signType / timestamp / appId、未完成加密配置 | 补齐公共参数与加密配置 |
| `2501`–`2508` | `device` 缺少 channel / deviceId / deviceType / appVer / os / osVer / brand / model | 按 §3.2 补齐 `device` 字段 |
| `2509` | 缺少 accessToken | 先登录拿 token |
| `2511` | 缺少 clientIp | 在 `device` 中填入真实公网 IP |
| `-444` / `-445` / `-446` | 请求太过频繁 | 退避后有限次重试；不要立刻连续重试 |
| `-461` | 请求总量超限 | **直接告知用户并停止**，不做二次加工 |

### 8.2 按 `message` 兜底

**判定顺序**：`300/301`、`2509`、`2511`、`2402`–`2508`、`1402`–`1405` 等协议与授权类错误直接按 §8.1 处理；只有业务类码（歌曲/播放接口上的 `1406`、`-444/-446/-461`、`4xx`）需要先读 `message`。同一个 `code` 在不同网关层含义可能不同（例如播放/歌曲接口上的 `1406`、`-444/-446/-461` 也可能用于版权、VIP、试听或频控），这类情况**以 `message` / `subCode` 为准，不要只按数字硬编码**：

- 文案含「未登录 / 请先登录 / 未授权」→ 登录流程。
- 文案含「无版权 / 下架 / VIP / 试听 / 付费」→ 换歌或降低码率，不重试同一请求。
- 文案含「总量超限」→ 立即停止并原样转述；仅「太过频繁」可退避后有限次重试。
- `subCode: 10003 / 10004` → 需前往手机端收听或购买，转述给用户。

---

## 9. ID、字段与返回值规则

### 9.1 歌曲对象（搜索 / 歌单曲目 / 详情通用）

| 字段 | 说明 |
|---|---|
| `id` | **加密 ID**，32 位 hex；所有 API 参数用这个 |
| `originalId` | **明文数字 ID**；只用于拼 `music.163.com` 链接与唤起客户端，部分响应才有 |
| `name` / `duration` / `artists[]` / `album` / `coverImgUrl` | 基础信息；`duration` 单位毫秒 |
| `visible` | 是否有版权；`false` 时禁止播放、禁止加入队列 |
| `playFlag` / `downloadFlag` | 是否可播放 / 下载 |
| `payPlayFlag` / `payDownloadFlag` / `vipFlag` / `vipPlayFlag` | 付费 / VIP 限制 |
| `liked` / `freeTrailFlag` | 是否已红心 / 是否支持试听 |
| `songTag[]` | 曲风标签，偏好分析用 |
| `alg` | 算法标识，播放数据上报必须透传 |
| `maxBrLevel` / `plLevel` / `dlLevel` | 歌曲最大音质 / 用户可播 / 可下载档位 |

### 9.2 通用规则

- **API 参数一律加密 ID**：`songId`、`playlistId`、`albumId`、`artistId`、`songIdList[]`、`songIds[]` 都传 32 位 hex；明文数字 ID 用于网页链接、唤起客户端和 `ncm-cli play --original-id`，不要出现在 API 参数里。搜索结果若没有 `originalId`，从 `song/detail/get/v2` 或 CLI 输出获取；仍拿不到就不要编造链接。
- **分页**：`offset` 从 0 开始，`limit` 常见上限 500，搜索类建议 ≤100，歌单曲目建议 30；列表总数看 `data.recordCount`。
- **bitrate**：档位值 `128` / `320` / `999` / `1999`（不是 128000/320000）；取不到指定档位时服务端会降级返回。
- **token 字段**：`accessToken` / `refreshToken` 预留 ≥512 字符，含 `+` `/` `=`；拼 query 时必须 URL 编码。
- **时间**：`timestamp`、`startLogTime` 用毫秒；`expireTime` / `expiresTime` / `auditionStart` / `auditionEnd` / `time` 用秒（或按下发单位）。
- **链接**：`https://music.163.com/#/song?id=<明文ID>`、`.../playlist?id=<明文ID>`、`.../album?id=<明文ID>`、`.../artist?id=<明文ID>`。

---

## 附录 A · 全量端点索引

开放平台文档中心共 282 个端点，下列按能力域分组，**只收录官方文档清单中的路径**。§4–§5 中另有 CLI 专属端点不在其中：`/openapi/music/cloud/file/upload/check`、`/upload`、`/upload/status`、`/download`、`/openapi/whale/nos/upload/token`、`/token/docid`、`/openapi/v1/ncm/cli/manifest`、`/openapi/music/basic/do/noting`、`/openapi/music/basic/playlist/create`。个别路径按官方清单原样收录（如 `.../recommend/playlist/ge`），使用前与文档中心核对。未在 §4–§5 展开的端点，参数见附录 B 的官方文档中心；调用时仍需 §3 的公共参数与签名，且需要 appId 具备该接口权限。

### 认证与账号（26）

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

### 搜索与发现（28）

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

### 歌曲、歌词与音质（66）

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

### 歌单与收藏（26）

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

### 专辑与艺人（15）

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

### 推荐、电台与榜单（19）

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

### 评论、社区与一起听（28）

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

### 播客、声音与有声书（50）

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

### 会员、交易与直播（15）

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

### 云盘与文件（2）

- `/openapi/music/basic/common/middle/short/url`
- `/openapi/music/resource/short/url/get`

### 基础设施与其他（7）

- `/openapi/middle/interest/exchangecode/generate`
- `/openapi/middle/interest/exchangecode/query`
- `/openapi/music/basic/mv/detail/get/v2`
- `/openapi/music/basic/play/data/record`
- `/openapi/music/common/popup/get`
- `/openapi/music/common/recently/heard/get`
- `/openapi/openapi/music/common/popup/get`


---

## 附录 B · 参考

- 官方开发者文档中心：<https://developer.music.163.com/>
- 文档分类树：`GET https://interface.music.163.com/api/openplatform/developer/api/doc/category`
- 文档详情：`GET https://interface.music.163.com/api/openplatform/developer/apidoc/get?docId=<id>`
- CLI 包：`@music163/ncm-cli@0.1.7`（<https://www.npmjs.com/package/@music163/ncm-cli>）
- 官方 Agent Skills：<https://github.com/NetEase/skills>
- 本地参考材料（仅本机环境可用）：`ncm-cli-api.md`、`analysis/ncm-openapi-reference.md`、`analysis/ncm-openapi-endpoints-all.md`（位于 `E:/workplace/ncm/`）
