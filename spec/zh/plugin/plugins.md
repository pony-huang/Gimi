# 插件

[English](../../en/plugin/plugins.md) · 简体中文

应用内置一组可安装插件，源码位于仓库根目录的 `plugins/`。每个插件是独立 APK，向 Agent 暴露工具。本页仅记录各插件对接的平台及其官方地址；实现细节见 `plugins/<name>/` 源码。

## 官方地址

| 插件 | 对接平台 | 官方地址 |
|---|---|---|
| `netease` | 网易云音乐 | <https://music.163.com>（接口使用详解：[netease.md](netease.md)） |
| `spotify` | Spotify Web API | <https://developer.spotify.com/documentation/web-api>（详细接入参考：[spotify.md](spotify.md)） |
| `v2ex` | V2EX API v2 | <https://www.v2ex.com>（令牌管理：<https://www.v2ex.com/settings/tokens>） |
| `weibo` | 微博开放平台 | <https://open.weibo.com> |
| `xiaohongshu` | 小红书 | <https://www.xiaohongshu.com> |
| `zhihu` | 知乎开放平台 | <https://developer.zhihu.com> |

## 修改插件对接行为前

在为插件新增或修改 Web API 端点、OAuth、工具 schema、播放行为或平台内容处理逻辑前，先重新获取该平台的官方 schema 与相关端点/政策页面——上表链接及带核查日期的配套文档（如 [spotify.md](spotify.md)）都不是冻结的 API 契约。
