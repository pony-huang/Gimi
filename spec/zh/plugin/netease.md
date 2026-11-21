# 网易云音乐开放平台接口使用规范

[English](../../en/plugin/netease.md) · 简体中文

> 面向 agent：要完成一个音乐操作，该调哪个接口、传什么参数、拿到什么、出错怎么办。
> 只写可执行的规则。签名、公共参数、错误码以官方文档中心为准（附录 B）；CLI 命令面以 `@music163/ncm-cli` 0.1.7 为准。
> 端点清单是过期快照，使用前务必与官方文档中心核对。

## 0. 先记住这 6 条

1. **两条调用路径**：能执行 shell 就优先用 `ncm-cli`（签名、device、token 刷新由它负责）；服务端集成或 CLI 未覆盖的端点才走裸 HTTP（§3）。
2. **ID 有两套**：加密 ID（32 位 hex）用于所有 API 参数；明文数字 ID 用于 `music.163.com` 链接、唤起客户端和 `ncm-cli play --original-id`。绝不混用。
3. **除匿名登录、二维码 key、`grantCode` 换 token 外，所有接口都要 `accessToken`**；二维码状态轮询必须带**匿名** token。先拿 token 再调业务接口。
4. **签名覆盖除 `sign` 外的全部非空公共参数**：按名字排序拼 `k=v&...`，RSA-SHA256 私钥签名，输出 base64（§3.4）。
5. **`bizContent` 与 `device` 都是 JSON 序列化后的字符串**，不是嵌套对象。
6. **网关按 appId 逐接口授权**：`code 300/301` 是没权限，不是参数错，重试无用——换有权限的 appId 或申请开通；manifest 接口同样受限。

## 1. 选哪条路径

| | `ncm-cli`（有 shell 时首选） | 裸 HTTP OpenAPI |
|---|---|---|
| 入口 | `ncm-cli <command> [args]` | `https://openncm.music.163.com/openapi/...` |
| 签名 / device / token | CLI 自动处理 | 自己实现（§3） |
| 命令来源 | `ncm-cli commands`（静态 + manifest 动态） | §4–§5 + 官方文档中心 |
| 适合 | 搜索、播放、播控、歌单、云盘上传、TUI | 服务端集成、批量任务、CLI 未暴露的端点 |
| 前置 | Node.js ≥ 18、appId/privateKey、登录 | appId/privateKey（PKCS#8 PEM）、accessToken |

两者底层同协议：CLI 动态命令最终翻译成 §3 的 HTTP 请求。

## 2. 凭证与准备

| 凭证 | 用途 | 获取 |
|---|---|---|
| `appId` | 应用 ID，所有请求的公共参数 | 开放平台控制台 |
| `privateKey` | RSA 私钥，仅本地签名，绝不上送 | 控制台，PKCS#8 PEM |
| `appSecret` (`clientSecret`) | 刷新 accessToken | 控制台 |
| `accessToken` | 登录态，除 §0.3 外必传 | §4 |

```bash
npm install -g @music163/ncm-cli
ncm-cli --version                          # 需 Node.js >= 18
ncm-cli config set appId <APP_ID>
ncm-cli config set privateKey <PEM路径或PEM文本>
ncm-cli config set player mpv              # 或 orpheus（仅 macOS）
ncm-cli login --check
ncm-cli login --background                 # 把二维码链接交给用户
```

- `privateKey`：写路径则读文件，否则按 PEM 文本使用。环境变量同样生效：`NETEASE_APP_ID`、`NETEASE_PRIVATE_KEY`、`NETEASE_APP_SECRET`。
- 播放还需 `mpv`（`mpv --version` 检查）；`ncm-cli config get player` 查看当前播放器。
- appId 必须开通目标接口权限并配好加密，否则所有数据接口返回 `300/301`。

## 3. HTTP 协议（自建客户端）

- Base URL：默认 `https://openncm.music.163.com`（CLI 0.1.7 所用）；文档示例用 `https://openapi.music.163.com`，路径参数一致，仅当目标路径 404 时才换。
- **GET**：所有公共参数拼进 query，`URLSearchParams` 编码。**POST**：**整个公共参数对象**作为 JSON body（`Content-Type: application/json`），不是只放 `bizContent`。
- 标注 `GET/POST` 的接口默认用 GET；写操作或卡片明确要求时才用 POST。
- 响应 JSON `{code, message, data, subCode}`：**只有 `code === 200` 成功**。列表通常在 `data.records` + `data.recordCount`，也可能是裸 `data` / `data.tracks`，按卡片取。

公共参数：`appId`、`signType`=`RSA_SHA256`、`timestamp`（毫秒，与服务端偏差 <5 分钟）、`device`（JSON 字符串）、`bizContent`（JSON 字符串，空传 `{}`）、`accessToken`（按 §0.3）、`sign`（§3.4）。

`device`（JSON 字符串）字段：`deviceType`=`openapi`、`os`=`ncmcli`、`appVer`=`0.1.7`（`x.x.x`）、`channel`=`ncmcli`、`model`=`Windows_x64_cli`、`brand`=`ncmcli`、`osVer`=`10.0.26300`、`clientIp`=真实公网 IP（否则 `2511`）、`deviceId`=`ncmcli_<随机>`（≤64 字符，不含 `= ; 空格 + |`，与匿名 token 绑定并持久化）、`netStatus`=`4g`（`wifi/2g/3g/4g/5g`）、`flowFlag`=`init`。这些值由平台分配；不清楚就直接用这套已验证的 CLI 0.1.7 取值。

请求头：`User-Agent: ncm-0.1.7`、`Referer: https://music.163.com/`、`Content-Type: application/json`（仅 POST）、`X-User-Input: <encodeURIComponent(会话摘要)>`（§6）。

### 3.4 签名算法

1. 取除 `sign` 外的公共参数，剔除 `null`/`undefined`/空串；只签公共参数，不签请求头。
2. 值转成字符串（`device` / `bizContent` 保持序列化 JSON 原文，不额外转义）。
3. 按参数名 ASCII 升序排序。
4. 拼成 `k=v&...`，**不 URL 编码**。
5. 用 PKCS#8 私钥做 RSA-SHA256 签名，结果取 **base64** 写入 `sign`。
6. GET 发送时再对整个 query 做 URL 编码；POST 直接把 `sign` 放进 JSON body。

```js
const canonical = Object.entries(params)
  .filter(([k, v]) => k !== "sign" && v != null && v !== "")
  .sort(([a], [b]) => (a < b) - (a > b))
  .map(([k, v]) => `${k}=${v}`).join("&");
const sign = crypto.createSign("SHA256").update(canonical).sign(privateKeyPem, "base64");
```

Python：用同样的 canonical 串，然后 `base64.b64encode(key.sign(canonical.encode(), padding.PKCS1v15(), hashes.SHA256())).decode()`。

端到端最小示例（匿名登录 → 搜索 → 详情）略去，按 §3.1–§3.4 + §4–§5 自行拼装。`clientIp` 必须是真实出口 IP，否则 `2511`。

## 4. 认证与 token 生命周期

顺序：匿名登录（游客）→ 需要用户资产时扫码登录 → 过期用 refreshToken 刷新 → 刷新失败重登。

| 端点（方法） | bizContent | 返回 / 规则 |
|---|---|---|
| `/openapi/music/basic/oauth2/login/anonymous` (POST) | `{"clientId":"<appId>"}`，不带 token | `data.accessToken` 是**字符串**，永不过期；另有 refreshToken/scopes。同一 `deviceId` 对应固定 token，缓存复用；换 `deviceId` 即新用户。游客可读内容、播音频，读不到用户资产 |
| `/openapi/music/basic/user/oauth2/qrcodekey/get/v2` (GET) | `{"type":2,"expiredKey":"300"}`，不带 token | `qrCodeUrl`（预留 8–512 字符）、`uniKey`；5 分钟有效，扫一次即失效 |
| `/openapi/music/basic/oauth2/device/login/qrcode/get` (GET) | `{"key":"<uniKey>","clientId":"<appId>"}` | **只能带匿名 token**（实名 token 返回 400 `device has login success`）。`data.accessToken` 是**对象**，新 token 在 `data.accessToken.accessToken`。status：800 已失效 / 801 待扫码 / 802 授权中 / 803 成功 / 804 错误；每 2–3 秒轮询至 803 / 800 / 804 或 5 分钟超时；804 重新取码 |
| `/openapi/music/basic/user/oauth2/token/refresh/v2` (GET) | `{"clientId","clientSecret","refreshToken"}` | AT 7 天、RT 20 天，一起续期；`clientId` 必须与 refreshToken 来源 appId 一致；匿名 token 不能刷新（1406） |

其他：`/openapi/music/basic/user/oauth2/token/get/v2`（grantCode 换 token，不带 token）、`/openapi/music/basic/user/info/bytoken/get/v2` (POST)、`/openapi/music/basic/do/noting` (GET，校验凭证)、`/openapi/music/basic/user/profile/get/v2`（§5.3）。

**token 异常**：先按 §8.2 用 `message` 确认是过期；只有实名 token 才刷新，匿名 token 直接重新扫码；刷新失败或 `1407`/`1408` 也重登。token 按无上限字符串存储（≥512 字符）。

## 5. 接口卡片

参数中的 ID 一律加密；卡片默认需 `accessToken`；**读我的资料 / 红心歌单、写歌单、红心操作需实名 token**。未写 CLI 命令的端点由服务端 manifest 下发（§5.6）。

### 5.1 搜索与发现

| 意图 | 端点 | 关键 bizContent |
|---|---|---|
| 搜歌曲 | `/openapi/music/basic/search/song/get/v3` | `keyword`, `limit`, `offset` |
| 搜专辑 | `/openapi/music/basic/search/album/get/v2` | `keyword`, `limit`, `offset` |
| 搜歌手 | `/openapi/music/basic/search/artists/get/v2` | `keyword`, `limit`, `offset` |
| 搜歌单 | `/openapi/music/basic/search/playlist/get/v2` | `keyword`, `limit`, `offset` |
| 按标签找歌单 | `/openapi/music/basic/search/playlist/bytag/get/v2` | `tag`, `limit`, `offset` |
| 综合搜索 | `/openapi/music/basic/complex/search` | `keyword` |
| 热搜词 | `/openapi/music/basic/search/hot/keyword/get/v2` | `limit` |
| 联想词 | `/openapi/music/basic/search/suggest/keyword/get/v2` | `keyword` |

- `search/song/get/v3`（GET/POST；CLI `search song --keyword "x" --userInput "…"`）`{"keyword","limit"≤100,"offset"≥0,"qualityFlag"}` → `data.records` + `data.recordCount`（也可能直接是数组）。结果 `id` 是加密 ID。新接入用 v3，不用旧的 v2。
- `complex/search` `{"keyword","identityFlag","subCountFlag","qualityFlag"}` → `data.{songs,artists,albums,playlists,voicelist,bestMatchResources,bestMatchResourceTypes}`。意图模糊或要多类型候选时用它。
- 其余搜索限制：专辑 `limit` ≤300（建议 ≤100），歌手两个开关默认 false，按标签 `limit` ≤500，热搜无 `keyword`，联想词仅用于输入补全。

### 5.2 歌曲、歌词与播放

- `song/detail/get/v2`（GET；CLI 经播放命令）`{"songId","withUrl":true,"bitrate":320,…}` → `playUrl`、`playUrlExpireTime`、`br`、`gain`/`peak`、`openApiTraceInfo`（全曲试听上报需透传）。先看 `visible`/`playFlag`，`false` 时禁止播放。`bitrate` 是档位 128/320/999/1999（不是 bps），不传取可听最高档。可选 `effects` eac3/ac4、`level` vivid/sky/jymaster/jyeffect（`sky` 需配 `immerseType` c51/ste/aac）、`extFlags`（JSON 字符串）。
- `song/playurl/get/v2` `{"songId","bitrate"}` → `data.url`（详情里同义字段叫 `playUrl`）；只要 URL、不要元数据时用。
- `batch/song/playurl/get` `{"songIds":[…≤500],"bitrate","effects"}`。
- `song/lyric/get/v2` `{"songId"}` → `lyric`（LRC）、`transLyric`、`txtLyric`、`romalrc`；`noLyric`/`pureMusic` 时不要展示歌词，`transLyric` 可能为 null。`song/lyric/word/by/word/get` 仅头部曲库有，拿不到回退逐行歌词。
- `play/data/record`（POST；CLI 自动上报）一次播放报两条：`startplay` 然后 `play`（结束报文必须显式把 `action` 改为 `play`）。搜索/推荐/歌单返回的 `alg`、详情返回的 `openApiTraceInfo` 有则透传，搜索场景无 `alg` 时填 `search`。字段：`id`（加密 ID，播客用 `encVoiceId`）、`action`、`type` song/dj、`bitrate` 128/320/999/1999/3999/4999/4099/2999/5999（后五档仅上报用，播客固定 128）、`file` 0 下载/1 本地/2 缓存/3 云盘/4 线上（不确定填 4）、`startLogTime` 毫秒、`time` 实际收听秒数（>0 且 ≤ 时长，不含暂停）、`end` playend/interrupt/exception/ui（不确定填 playend）、`sourceId`/`sourceType`（list/album/dailySongRecommend/userfm/search/djradio）、`isAudition` 1/2、`auditionStart`/`auditionEnd`、`categoryId`/`ext`。

### 5.3 用户与歌单

| 读取端点 | bizContent | 返回 |
|---|---|---|
| `/openapi/music/basic/user/profile/get/v2` | `{}` | `id`、`nickname`、`avatarUrl`、`gender`（0 未知/1 男/2 女）、`signature`、`redVipLevel`、`vipDetail` |
| `/openapi/music/basic/playlist/star/get/v2` | `{}` | 红心歌单对象（`id`、`name`、`trackCount`、`coverImgUrl`）；偏好分析入口 |
| `/openapi/music/basic/playlist/created/get/v2` | `{"limit":100,"offset":0}` | 我创建的；`recordCount`+`records`，`limit`≤500 |
| `/openapi/music/basic/playlist/subed/get/v2` | `{"limit":100,"offset":0,"originalCoverFlag":false}` | 我收藏的；`originalCoverFlag` 控制无水印封面 |
| `/openapi/music/basic/playlist/detail/get/v2` | `{"playlistId":"<enc>"}` | 歌单详情；可选 `originalCoverFlag`/`newCoverFlag` |
| `/openapi/music/basic/playlist/song/list/get/v3` | `{"playlistId":"<enc>","limit":30,"offset":0,"qualityFlag":true}` | 曲目；`limit`≤500，建议 30 |
| `/openapi/music/basic/playlist/song/list/get/v5` | 同上 | `data.tracks` + 歌单元信息 |

`specialType`：0 普通 / 5 红心 / 10 置顶 / 20 尾部 / 100 官方 / 200 视频 / 300 分享。红心曲目带 `extMap.addTime`（收藏毫秒）与 `songTag[]`（曲风），可用于偏好统计。

| 写入端点 | bizContent | 作用 |
|---|---|---|
| `/openapi/music/basic/playlist/create` | `{"playlistName":"跑步"}` | CLI `playlist create --playlistName "x" --userInput "…"`；HTTP 以 manifest `bodyParams` 为准 |
| `/openapi/music/basic/playlist/song/batch/like` | `{"playlistId":"<enc>","songIdList":["<enc>"]}` | 批量加歌 |
| `/openapi/music/basic/playlist/song/batch/delete` | 同上 | 批量删歌，≤500，仅创建者可编辑 |
| `/openapi/music/basic/playlist/song/like/v2` | `{"songId":"<enc>","isLike":true}` | 红心/取消（`false` 删除） |
| `/openapi/music/basic/playlist/sub/v2` / `unsub/v2` | `{"id":"<enc>"}` | 收藏 / 取消收藏歌单 |

**创建后**：从 `data` 取新歌单的加密 `playlistId`；若无则调 `created/get/v2` 取最新一条。链接所需明文 `originalId` 从 `detail/get/v2` 或 `created/get/v2` 取；取不到就不要编造链接。**写入后校验**：重新调 `detail/get/v2`，确认 `data.trackCount` 增加了本次加入数量。

### 5.4 专辑、艺人与榜单

| 端点 | bizContent | 返回 / 规则 |
|---|---|---|
| `/openapi/music/basic/album/detail/get/v2` (POST) | `{"albumId":"<enc>","descFlag":true}` | 详情；`descFlag` 带简介 |
| `/openapi/music/basic/album/song/list/get/v3` | `{"albumId":"<enc>","limit":30,"offset":0}` | 专辑曲目 |
| `/openapi/music/basic/artist/detail/get/v2` | `{"artistId":"<enc>","extFlag":true}` | 详情；可选 `personalFlag`/`creatorRoleFlag`/`subCountFlag`/`identityFlag` |
| `/openapi/music/basic/artists/hotsong/list/get/v2` | `{"artistsId":"<enc>","top":50,"qualityFlag":true}` | 热门歌曲；参数名是 `artistsId`（复数），`top` ≤60 |
| `/openapi/music/basic/toplist/get/v2` | `{}` | 榜单；`category` 取 OFFICIAL/FEATURE/MUSIC_STYLE/GLOBAL/MORE/LANGUAGE |

### 5.5 工具与文件

| 端点 | bizContent | 用途 |
|---|---|---|
| `/openapi/music/basic/common/middle/short/url` (POST) | `{"sourceUrl":"<url>","expireTimeSeconds":1296000}` | 返回 `data.shortUrl`；默认 15 天 |
| `/openapi/music/cloud/file/upload/check`、`/upload`、`/upload/status`、`/download`、`/openapi/whale/nos/upload/token`、`/token/docid` | 由 CLI 下发 | 云盘上传/下载；文件数据直传 `wanproxy.127.net` / `wanproxy-web.127.net` |

云盘参数由 CLI/manifest 下发，用 `ncm-cli cloudupload --help` 获取，不要自行拼装。

### 5.6 动态命令清单（manifest）

- `POST /openapi/v1/ncm/cli/manifest`，`{"cli_version":"0.1.7","cached_versions":{}}`，需 `accessToken`，且受 appId 授权控制。
- 返回命令树，缓存于 `~/.config/ncm-cli/cache/manifest.json`。搜索、歌词、笔记发布、创建歌单等命令的具体端点与参数由它下发；`ncm-cli commands` 展示静态 + manifest 命令。

## 6. CLI 命令面

### 6.1 静态命令

```bash
ncm-cli --help | commands | <command> --help   # 列表 / 命令树 / 精确参数（不要猜）
ncm-cli configure                              # 配置向导
ncm-cli config get <key> | config set <key> <value>
ncm-cli login --check | login --background     # 后台扫码，把链接交给用户
ncm-cli search song --keyword "xxx"
ncm-cli playlist create --playlistName "xxx"
ncm-cli play --song --encrypted-id <enc> --original-id <plain>
ncm-cli pause | resume | next | prev | state | volume 60 | tui
ncm-cli cloudupload file --file <path> | diag report
```

### 6.2 Agent 规则

1. 首次使用：`ncm-cli --version`，再 `ncm-cli login --check`。
2. **每次会话先取命令树** `ncm-cli commands`：命令集合随 manifest 变化，不要依赖记忆。
3. **参数不猜**：`ncm-cli <command> --help`。
4. 除播控类命令外，动态命令附加 `--userInput "<最近一轮输入的简述，≤200 字>"`，会变成 `X-User-Input`。
5. 输出含「请先登录 / 未授权」→ `login --background`，把链接给用户，跑完登录流程后重试原命令。
6. 输出「请求总量超限」→ 原样转述并停止，不重试、不二次加工。
7. 给用户可点击链接，用**明文 ID**：`https://music.163.com/#/song?id=<plain>`、`.../playlist?id=<plain>`、`.../album?id=<plain>`、`.../artist?id=<plain>`。
8. `visible=false` 的歌**禁止**播放或入队。多首结果先播第一首，其余入队（队列命令以 `ncm-cli commands` 为准，不要猜）。

§6.3（CLI 执行器：`queryParams` 进 query、`bodyParams`+`queryParams` 组成 POST JSON、`--userInput` 转成请求头、公共参数统一注入并签名）属内部实现；自建客户端一律按 §3.1。

## 7. Agent 决策流程

### 7.1 意图 → 接口

| 用户想 | 调这个 | 前置 |
|---|---|---|
| 找某首歌 | `search/song/get/v3` | 任意 token |
| 一次找多种类型 | `complex/search` | 任意 token |
| 看歌词 | `song/lyric/get/v2` | 加密 `songId` |
| 播放 / 拿音频地址 | `song/detail/get/v2`（`withUrl:true`）或 `song/playurl/get/v2` | 加密 `songId` + 查 `visible` |
| 批量拿地址 | `batch/song/playurl/get` | ≤500 个加密 ID |
| 我的资料 / 红心 | `user/profile/get/v2` / `playlist/star/get/v2` | **实名 token** |
| 歌单内容 | `playlist/detail/get/v2` → `playlist/song/list/get/v3` | 加密 `playlistId` |
| 建歌单并加歌 | `playlist/create` → `playlist/song/batch/like` | 实名 token |
| 红心/取消 | `playlist/song/like/v2` | 实名 token |
| 分析偏好 | `playlist/star/get/v2` → `playlist/song/list/get/v3`（`songTag`/`addTime`） | 实名 token |
| 生成短链 | `common/middle/short/url` | 任意 token |

### 7.2 搜索并播放

1. `config set appId/privateKey` → `login --check`（否则 `login --background`）→ `ncm-cli commands` → `search song --help`。
2. 搜索（`ncm-cli search song` 或 §5.1）→ 丢弃 `visible=false`/`playFlag=false` → 取第一条。
3. 记录 `id`（加密，API 用）+ `originalId`（明文，链接用）→ `song/detail/get/v2 {songId:id, withUrl:true, bitrate:320}`。
4. `ncm-cli play --song --encrypted-id <id> --original-id <originalId>`；第一首播放，其余入队。
5. `play/data/record`：先 `startplay`，结束再 `play`（透传 `alg`）→ 回复歌名 + `https://music.163.com/#/song?id=<originalId>`。

### 7.3 按偏好建歌单

1. 实名登录（匿名读不到用户资产）→ `playlist/star/get/v2` → 红心歌单 id。
2. `playlist/song/list/get/v3 {playlistId, limit:200, offset:0}` → 取 `songTag`/`addTime` 做偏好分析。
3. 用偏好关键词多次 `search/song/get/v3`（或 `complex/search`），去重、过滤 `visible=false`。
4. `playlist/create {playlistName}` → 新 id → `playlist/song/batch/like {playlistId, songIdList}`。
5. `playlist/detail/get/v2` → 校验 `trackCount` → 回复 `https://music.163.com/#/playlist?id=<plain>`。

## 8. 错误码与处理

| code | 含义 | 做法 |
|---|---|---|
| `200` | 成功 | 读 `data` |
| `300`/`301` | appId 未授权该接口 | 停止重试；换 appId 或申请开通；manifest 同样受限 |
| `401`/`402` | 参数 / 业务参数非法 | 检查 `bizContent` 字段名、类型、必填 |
| `404` | 接口 / 资源不存在 | 查路径与 ID；路径无误时换 §3 的另一个 Base URL |
| `500` | 业务 / 系统异常 | 重试 1 次，仍失败转述 `message` |
| `1401` | HTTP 方法不支持 | GET↔POST 互换 |
| `1402`/`1403`/`1405` | sign/signType/appId 非法 | 查私钥、签名串、appId |
| `1404` | timestamp 非法 | 校准时间，与服务端偏差 <5 分钟 |
| `1406` | token 过期 | 按 §8.2 用 `message` 确认；实名才刷新，匿名直接重登；刷新失败也重登 |
| `1407`/`1408` | 账号异常 / token 无效 | 扫码重登，不刷新 |
| `2402`–`2406` | 缺 sign/signType/timestamp/appId 或未配加密 | 补齐公共参数与加密配置 |
| `2501`–`2508` | `device` 缺 channel/deviceId/deviceType/appVer/os/osVer/brand/model | 按 §3 补齐 `device` |
| `2509` | 缺 accessToken | 登录拿 token |
| `2511` | 缺 clientIp | 在 `device` 填真实公网 IP |
| `-444`/`-445`/`-446` | 请求太过频繁 | 退避后有限次重试，不要密集重试 |
| `-461` | 请求总量超限 | **告知用户并停止** |

### 8.2 按 `message` 兜底

协议/授权类错误（`300/301`、`2509`、`2511`、`2402`–`2508`、`1402`–`1405`）直接按上表处理。只有业务类码（`1406`、`-444/-446/-461`，歌曲/播放接口的 `4xx`）需要先读 `message`：同一个 `code` 在不同网关层可能表示版权、VIP、试听或频控，因此以 `message`/`subCode` 判断，不要只按数字硬编码。

- 文案含「未登录 / 未授权」→ 走登录流程。
- 文案含「无版权 / 下架 / VIP / 试听 / 付费」→ 换歌或降码率，不重试同一请求。
- 文案含「总量超限」→ 立即停止并原样转述；只有「太过频繁」可退避后有限次重试。
- `subCode 10003/10004` → 需去手机端收听或购买，转述给用户。

## 9. ID、字段与返回值

### 9.1 歌曲对象（搜索 / 歌单曲目 / 详情通用）

| 字段 | 说明 |
|---|---|
| `id` | **加密 ID**，32 位 hex——所有 API 参数用它 |
| `originalId` | **明文数字 ID**——仅链接/唤起客户端，部分响应才有 |
| `name`/`duration`/`artists[]`/`album`/`coverImgUrl` | 基础信息；`duration` 毫秒 |
| `visible` | 是否有版权；`false` 禁止播放 / 入队 |
| `playFlag`/`downloadFlag` | 是否可播放 / 下载 |
| `payPlayFlag`/`payDownloadFlag`/`vipFlag`/`vipPlayFlag` | 付费 / VIP 限制 |
| `liked`/`freeTrailFlag` | 是否红心 / 是否支持试听 |
| `songTag[]` | 曲风标签，偏好分析用 |
| `alg` | 算法标识，播放上报必须透传 |
| `maxBrLevel`/`plLevel`/`dlLevel` | 歌曲最高 / 用户可播 / 可下载档位 |

### 9.2 规则

- **API 参数一律加密 ID**（`songId`、`playlistId`、`albumId`、`artistId`、`songIdList[]`、`songIds[]`）；明文 ID 只用于链接、唤起客户端和 `ncm-cli play --original-id`。结果没有 `originalId` 时从 `song/detail/get/v2` 或 CLI 输出取；仍拿不到就不要编造链接。
- **分页**：`offset` 从 0 开始；`limit` 常见上限 500（搜索建议 ≤100，歌单曲目 30）；总数看 `data.recordCount`。
- **bitrate**：档位 128/320/999/1999，不是 bps；取不到时服务端降级。
- **token**：`accessToken`/`refreshToken` ≥512 字符，可能含 `+`/`/`/`=`，拼 query 时必须 URL 编码。
- **时间**：`timestamp`/`startLogTime` 毫秒；`expireTime`/`expiresTime`/`auditionStart`/`auditionEnd`/`time` 秒（或按接口单位）。
- **链接**：`https://music.163.com/#/song?id=<plain>`、`.../playlist?id=<plain>`、`.../album?id=<plain>`、`.../artist?id=<plain>`。

## 附录 A · 端点索引

官方文档中心列出约 282 个端点，且清单会变动、以官方为准——不再在此缓存（见附录 B）。未在 §4–§5 展开的端点仍需 §3 的全部公共参数与签名，且需要 appId 具备该接口权限。

## 附录 B · 参考

- 官方开发者文档中心：<https://developer.music.163.com/>
- 文档分类树：`GET https://interface.music.163.com/api/openplatform/developer/api/doc/category`
- 文档详情：`GET https://interface.music.163.com/api/openplatform/developer/apidoc/get?docId=<id>`
- CLI 包：`@music163/ncm-cli@0.1.7`（<https://www.npmjs.com/package/@music163/ncm-cli>）
- 官方 Agent Skills：<https://github.com/NetEase/skills>
