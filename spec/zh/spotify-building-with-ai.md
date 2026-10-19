# Spotify：AI 辅助开发与后续接入参考

[English](../en/spotify-building-with-ai.md) · 简体中文

核查日期：2026-10-19。本文是分析与项目建议，不是官方文档全文翻译，也不代表现有插件已满足全部要求。后续实现以重新获取的官方契约与专题文档为准。

## 这篇文章解决什么问题

官方 [Building with AI](https://developer.spotify.com/documentation/web-api/tutorials/building-with-ai) 面向使用 AI 编程工具的开发者：提供机器可读 API 文档、提示词范例和代码审查要点。它的主要价值是把“模型记忆里的 API”替换成可核对的外部契约。它没有提供本项目的 Android 接入实现，也不能单独视为运行时 AI 使用 Spotify 数据的许可。

## 官方资料如何使用

### 先核对 API 契约

[官方 OpenAPI schema](https://developer.spotify.com/reference/web-api/open-api-schema.yaml) 已成功获取，当前文件声明 `openapi: '3.0.3'`。后续每项功能都要核对路径、HTTP 方法、参数所在位置、必填项、限制、OAuth scopes、成功状态及响应结构。schema 下载副本只放 `temp/`，不要把本文日期当成永久版本锁定。

与刚修复的问题对应，schema 中 `PUT/DELETE /me/library` 的 `uris` 是查询参数；`GET /me/library/contains` 返回布尔数组。歌单条目新接口是 `/playlists/{playlist_id}/items`。这些例子用于说明核对维度，不能代替接入时重新查阅完整 operation。来源：[OpenAPI schema](https://developer.spotify.com/reference/web-api/open-api-schema.yaml)、[收藏接口](https://developer.spotify.com/documentation/web-api/reference/save-library-items)。

### 授权按客户端边界选择

移动端等无法安全保管 Client Secret 的客户端应使用 Authorization Code with PKCE；有安全后端时再评估普通 Authorization Code。Client Credentials 只能用于公共数据，不能替代用户授权；Implicit Grant 已废弃。来源：[PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)、[授权概览](https://developer.spotify.com/documentation/web-api/concepts/authorization)。

回调通常要求 HTTPS；loopback 例外可以使用显式 IP 的 HTTP，如 `127.0.0.1` 或 `[::1]`。`localhost` 不允许。URI 要与登记值一致；显式 loopback IP 有动态端口例外。这是专题文档对教程简化示例的补充，不能外推成任意 HTTP 地址都可用。来源：[Redirect URIs](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)。

### 权限和令牌生命周期

根据已实现功能核对最小 scopes，并在功能增加时重新评估授权需求。来源：[Scopes](https://developer.spotify.com/documentation/web-api/concepts/scopes)。

当前刷新文档规定，Developer Dashboard 应用签发的 refresh token 生命周期为授权起六个月，刷新 access token 不会延长它；`expires_in` 表示 access token 的期限。收到新的 refresh token 时替换，未返回时保留原值；`invalid_grant` 应清理失效授权并引导重新登录。不要把刷新失败当作无限重试条件。来源：[Refreshing tokens](https://developer.spotify.com/documentation/web-api/tutorials/refreshing-tokens)。

### 限流和错误按语义处理

429 的 `Retry-After` 单位为秒，应退避等待。全局限流按滚动三十秒窗口计算，开发模式与扩展配额模式不同；部分端点另有限制，不能推定一个固定通用 QPS。来源：[Rate limits](https://developer.spotify.com/documentation/web-api/concepts/rate-limits)。

官方通用说明明确 204 没有响应体，错误应结合状态码与服务端详情判断；403 不应一概解释成缺少 scope。来源：[API calls](https://developer.spotify.com/documentation/web-api/concepts/api-calls)。

播放接口使用 `PUT /me/player/play`，专辑、艺人、歌单使用 `context_uri`，单曲使用 `uris` 数组；与其他 Player 操作一起使用时执行顺序不受保证。来源：[Start/Resume Playback](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback)。

项目建议：对每个 operation 明确空成功响应、对象、数组及错误响应的处理方式。把“请求已被接受”“状态查询观察到的结果”“设备上实际生效”分开报告，状态确认采用有限次数查询。修改队列、切歌等操作结果不明时，先查询状态再决定是否重试，避免重复副作用。设备音量不受支持与设备不在线分别处理。这是本项目基于已遇问题形成的实现策略，专辑能播放不能证明单曲 URI 无效。

### AI 编程与产品数据用途分别审查

教程建议限制内容缓存、标注 Spotify 来源并避免使用 Spotify 数据训练模型；详细适用范围应查 [Developer Terms](https://developer.spotify.com/terms) 和 [Developer Policy](https://developer.spotify.com/policy)。

政策 II.4 要求展示内容时标注 Spotify，并为相关元数据、封面等提供回链；I 节要求提供断开账号连接的入口并在断开后清理相关个人数据。III.3 禁止提供语音控制 Spotify 的应用或语音助手功能；III.14 的措辞还涵盖将 Spotify 内容输入机器学习或 AI 模型。因此，AI 辅助写代码与运行时把工具结果交给模型是两个问题。对本项目的语音入口、模型上下文和记忆留存，应单独核对实际数据流及适用条款；本次未完成该审计，也没有改动这些功能。来源：[Developer Policy](https://developer.spotify.com/policy)。

## 后续接入的项目工作顺序

以下是项目建议，不是官方教程原文：

1. 明确用户动作、所需数据和副作用，加载本参考及对应官方 operation；记录核查日期。资料冲突时明确记录差异，再核实，不能凭模型记忆选一个。
2. 列出请求/响应映射：路径、方法、query/body、URI 类型、scopes、返回形状、错误与重试语义。开发模式或账号限制另查官方配额资料。
3. 在 `:plugins:spotify` 的 HTTP/OAuth 边界完成适配；模型工具只暴露经过验证的参数和结果。跨模块工作继续遵循根目录架构约束。
4. 用模拟网关或 HTTP 连接覆盖实际风险边界，包括空响应、数组、401/429、刷新失效、取消传播及不确定的修改结果。测试不调用真实付费模型或使用真实凭据。
5. 按改动风险完成模块测试和编译；设备相关行为再用真实 Connect 会话核查。报告构建、安装和业务验证各自的结果。
6. 官方契约或本项目策略发生变化时，同步更新中英文参考；`AGENTS.md` 只保留英文入口，避免重复维护整份规则。

## 当前插件的差异与待核对项

这是 2026-10-19 的局部源码检查，不是完整安全或合规审计，也不意味着以下工作已实施：

| 观察 | 后续接入时的处理方向 |
| --- | --- |
| `SpotifyAuth.kt` 使用 Client Secret 的 Basic 认证换取/刷新令牌 | 为 Android 公共客户端评估 PKCE，或将秘密保留在安全后端；用户手动填写 Secret 不会改变客户端的保密能力 |
| `parseRedirectUri` 接受 `localhost`，相关注释也称其受支持 | 依据当前回调专题文档收紧校验并同步已有测试与说明 |
| `TokenStore.kt` 将令牌写入插件私有 SharedPreferences | 核对静态存储、备份、日志和退出登录后的生命周期；私有文件本身不等同于加密存储 |
| `SpotifyAuth.SCOPES` 集中声明插件权限 | 对照实际启用功能和当前 operation 检查最小集合，不能仅凭权限列表长就认定每项都多余 |
| 用户报告单曲请求被接受但未起播、设备掉线及状态滞后 | 保留为设备复测项；HTTP 模拟测试或本地禁用缓存不能证明 Spotify 服务端状态已实时更新 |

源码入口：[SpotifyAuth.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/SpotifyAuth.kt)、[TokenStore.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/TokenStore.kt)、[SpotifyApi.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/SpotifyApi.kt)、[SpotifyToolset.kt](../../plugins/spotify/src/main/java/github/ponyhuang/gimi/plugin/spotify/SpotifyToolset.kt)。运行问题来自本次会话的用户测试报告，未将其扩展成所有设备的普遍结论。
