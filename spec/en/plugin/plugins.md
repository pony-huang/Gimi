# Plugins

English · [简体中文](../../zh/plugin/plugins.md)

The app ships a set of installable plugins under the `plugins/` directory. Each plugin is a separate APK that exposes tools to the agent. This page only records what each plugin integrates with and where its official reference lives; see the source under `plugins/<name>/` for implementation details.

## Official references

| Plugin | Integrates with | Official address |
|---|---|---|
| `netease` | NetEase Cloud Music | <https://music.163.com> (API usage detail: [netease.md](netease.md)) |
| `spotify` | Spotify Web API | <https://developer.spotify.com/documentation/web-api> (detailed integration reference: [spotify.md](spotify.md)) |
| `v2ex` | V2EX API v2 | <https://www.v2ex.com> (tokens: <https://www.v2ex.com/settings/tokens>) |
| `weibo` | Weibo Open Platform (微博开放平台) | <https://open.weibo.com> |
| `xiaohongshu` | Xiaohongshu (小红书) | <https://www.xiaohongshu.com> |
| `zhihu` | Zhihu Open Platform (知乎开放平台) | <https://developer.zhihu.com> |

## Changing a plugin's integration

Before adding or changing Web API endpoints, OAuth, tool schemas, playback behavior, or platform content handling for a plugin, re-fetch that platform's linked official schema and relevant endpoint/policy pages — the references above and any dated companion doc (e.g. [spotify.md](spotify.md)) are not frozen API contracts.
