# 更新日志

版本 PR 根据 Conventional Commits 自动生成后续版本记录；合并前请整理用户可见的变化与升级注意事项。

## [0.11.1](https://github.com/pony-huang/Gimi/compare/v0.11.0...v0.11.1) (2026-10-10)


### 新增

* **logging:** add log viewer export and 24-hour retention ([017a375](https://github.com/pony-huang/Gimi/commit/017a37543f0e4bafc0b6f98ef326b6285e858d07))
* **settings:** render update notes with default Markdown ([828df7e](https://github.com/pony-huang/Gimi/commit/828df7ee4b534af2caa479e1a888453fe1d26718))


### 修复

* **app:** 支持测试版与正式版共存 ([6f0f8af](https://github.com/pony-huang/Gimi/commit/6f0f8afbf89ccea8bf001b58b8b45f679b857492))
* **chat:** preserve reading position when returning to app ([f6fa299](https://github.com/pony-huang/Gimi/commit/f6fa299de35c91db5af8b8faa9c9a34e721dbcfe))
* **chat:** retain unsent drafts and consume shared attachments ([9f63d9d](https://github.com/pony-huang/Gimi/commit/9f63d9d07cad85940fe3e950dbba7e25a670420f))
* **chat:** show latest conversations when opening drawer ([8a07a0f](https://github.com/pony-huang/Gimi/commit/8a07a0fa3af372a327e8936e529ca3a98cf76796))
* **mcp:** 允许 Streamable HTTP/SSE 端点使用明文 HTTP ([786c098](https://github.com/pony-huang/Gimi/commit/786c098f4012fc60b73a732e35d7ee67676061dd))
* **mcp:** 把工具异常兜成 tool_result，避免污染下一轮 Anthropic 请求 ([20fae85](https://github.com/pony-huang/Gimi/commit/20fae85c934c176c971326d05c79dbef77576eb3))

## [0.11.0](https://github.com/pony-huang/Gimi/compare/v0.10.2...v0.11.0) (2026-10-05)


### 新增

* **chat:** 优化最近会话列表并支持搜索与批量删除 ([f2d46b4](https://github.com/pony-huang/Gimi/commit/f2d46b428d786f4e22fe5105b0c9cdf0838caf21))
* **mcp:** 新增服务器操作菜单和可展开的工具详情页 ([52edfde](https://github.com/pony-huang/Gimi/commit/52edfdeedadaa9be51b4f68e052fd55df854633f))
* **mobileuse:** 优化小窗拖动缩放交互并精简授权文案 ([987570b](https://github.com/pony-huang/Gimi/commit/987570b0d8157e34ad0fb35999711a7dc4c70f85))
* **mobileuse:** 支持副屏旋转并在任务结束后隐藏浮窗 ([aea0b9b](https://github.com/pony-huang/Gimi/commit/aea0b9b5834fe2c4a28a4c2dbcecd429095a6a7e))
* **settings:** order tools group by how often each is used ([dd48c66](https://github.com/pony-huang/Gimi/commit/dd48c66762fba9d45f8989c17cf91500f832c840))


### 修复

* **chat:** 简化消息重试并移除编辑与二次确认，底层暂不支持session event 编辑 ([4db1e6d](https://github.com/pony-huang/Gimi/commit/4db1e6dce92e1361d717dd815ae55912dd1bfec5))
* **mobileuse:** smooth bubble motion and hide only at edges ([e4ea59c](https://github.com/pony-huang/Gimi/commit/e4ea59c6c10972b610425ec564582cfc54d13b64))
* **plugin:** 跳过不兼容插件并提示升级 ([055f3c2](https://github.com/pony-huang/Gimi/commit/055f3c2098fdc7f29c55fd6a74dc8c54624bfc51))
* **release:** remove default changelog category heading ([87c850f](https://github.com/pony-huang/Gimi/commit/87c850fcbe16afbf74a79eab2e4f2f83e2b5c924))

## 0.10.2

以已发布的 v0.10.2 作为发布流程初始化基线。历史更新说明请查看 [GitHub Releases](https://github.com/pony-huang/Gimi/releases)。
