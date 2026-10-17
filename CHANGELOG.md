# 更新日志

版本 PR 根据 Conventional Commits 自动生成后续版本记录；合并前请整理用户可见的变化与升级注意事项。

## [0.12.1](https://github.com/pony-huang/Gimi/compare/v0.12.0...v0.12.1) (2026-10-17)


### Features

* **agent:** add flashlight tools and remove unused categories ([cf3a50e](https://github.com/pony-huang/Gimi/commit/cf3a50eea76c75152211b227ec5892a26d63cabb))
* **chat:** remove search result cards and clarify local images ([a439eb3](https://github.com/pony-huang/Gimi/commit/a439eb3184515bb4fdb5bdbe892a4e70114f60ed))
* **chat:** support copying input request prompts ([1b9d2d1](https://github.com/pony-huang/Gimi/commit/1b9d2d102e68cdb4413a51b4e45b32d79a72112a))
* **chat:** unify request cards and reuse voice composer ([edb2fae](https://github.com/pony-huang/Gimi/commit/edb2faed48488eb036945a6c54cfd2dd2fc77e2d))
* **mobileuse:** refine display controls and sandbox tools ([9d60f15](https://github.com/pony-huang/Gimi/commit/9d60f159f592a0d0a24873b85998e87fa7d35cb9))
* **sandbox:** clarify capabilities and refine settings layout ([4474ca1](https://github.com/pony-huang/Gimi/commit/4474ca18706ced3748f916fad846c77d0e9bc727))


### Bug Fixes

* **mobileuse:** enlarge preview and expand window movement ([c891251](https://github.com/pony-huang/Gimi/commit/c891251963b038d9b671a9b26bdf0541b10a7971))

## [0.12.0](https://github.com/pony-huang/Gimi/compare/v0.11.1...v0.12.0) (2026-10-16)


### ⚠ BREAKING CHANGES

* **agent:** OfficialToolBinding.ProviderDeclaration.wireName is renamed to declarationName, and the openai_web_search and mimo_web_search catalog entries are removed.
* **agent:** separate Claude mapping and streaming
* **agent:** adopt ADK ChatCompletions backend
* **mcp:** MCP supports Streamable HTTP only. Existing SSE configurations are ignored and must be replaced with HTTP endpoints.

### 新增

* **agent:** add bounded reflection retries for read tools ([478d7ed](https://github.com/pony-huang/Gimi/commit/478d7ed9dd9927430f1454023f1249a486057e45))
* **agent:** add Claude prompt caching, JSON output and adaptive thinking ([3742387](https://github.com/pony-huang/Gimi/commit/3742387b86b83d44f4e1d21e522106cedc2ad0c8))
* **chat:** align bottom sheets with compact settings style ([b5944a6](https://github.com/pony-huang/Gimi/commit/b5944a63f55027db56e6427645ba610540282334))
* **chat:** unify controls and use a model bottom sheet ([f80a95d](https://github.com/pony-huang/Gimi/commit/f80a95d0600c15ce91088553b529d770a5604e82))
* **chat:** 支持听写转写与直接发送 ([99b2cbb](https://github.com/pony-huang/Gimi/commit/99b2cbb58eec251ad4a8c8c93dc03fac721e5a87))
* improve sandbox endpoints, recommendations, and UI ([a0dfc83](https://github.com/pony-huang/Gimi/commit/a0dfc83e5654d383fd1fe624d12cb916a0bbbed8))
* **mcp:** adopt official toolset and remove SSE support ([9a4bce7](https://github.com/pony-huang/Gimi/commit/9a4bce7f6a0f829ba2c0bec8b15eacb3a0195bc1))
* **mobileuse:** rename the accessibility service to Gimi ([562dc69](https://github.com/pony-huang/Gimi/commit/562dc69180c92f9b9648b930ea18cbbb6b165cba))
* **permissions:** 统一悬浮窗授权并移除小窗设置 ([dfe6a2c](https://github.com/pony-huang/Gimi/commit/dfe6a2cbda64d083e0bafe69e2405ec5ac3d9662))
* **sandbox:** add account template dropdown selection ([c907a0e](https://github.com/pony-huang/Gimi/commit/c907a0e3a3b942a2805073c90b45b08d8016f47d))
* **sandbox:** add E2B settings and conversation tools ([4423ee1](https://github.com/pony-huang/Gimi/commit/4423ee1d3a33c34c1ab9b12066500f6a90efe9b8))
* **settings:** move the Shizuku switch into its own page ([2c1b823](https://github.com/pony-huang/Gimi/commit/2c1b823a8ff3e7b37070fc0be3bb274bb281c12b))
* **settings:** refine sandbox configuration and menu labels ([7eec979](https://github.com/pony-huang/Gimi/commit/7eec9796196c61b9254fbc8af94061af71bff43d))
* **settings:** unify settings and model configuration UI ([c1651ea](https://github.com/pony-huang/Gimi/commit/c1651ea4cec84da807c21dc531335cf8667e6cbe))
* **skills:** connect environment tools and scripts to E2B ([fdb254a](https://github.com/pony-huang/Gimi/commit/fdb254a3dff180f5ad86e12c519914def9bc4bad))
* **skills:** install skill archives from the cloud sandbox ([9b29345](https://github.com/pony-huang/Gimi/commit/9b2934581a6e884e4eead61ca1b47674360b0c8b))


### 修复

* **agent:** batch tool confirmations before resuming invocation ([549c7a1](https://github.com/pony-huang/Gimi/commit/549c7a182e160d7ec15c2b04120e622a103f9ec6))
* **agent:** declare official tools in each protocol's native form ([ac50fe0](https://github.com/pony-huang/Gimi/commit/ac50fe026d99ab6fd7942cedcddb8291318f9425))
* **chat:** dedupe the error bubble an ADK error event and turn failure share ([4e01a0e](https://github.com/pony-huang/Gimi/commit/4e01a0e4012ab03d490ed35c1c61b0ac1a0c6817))
* **chat:** keep every text part id unique within a message ([322a380](https://github.com/pony-huang/Gimi/commit/322a38002b394b7c3159b941b51004e708fdbca3))
* **chat:** stabilize the model picker sheet height ([f4e7d08](https://github.com/pony-huang/Gimi/commit/f4e7d08d80e902e49a47ce3c1e373ea4c05cac24))
* **conversation:** publish the drawer row before persisting its preview ([dfb1c8d](https://github.com/pony-huang/Gimi/commit/dfb1c8da3f0f4ded3b4fc131f5d131bfc4e5d061))
* **mcp:** align tool details with settings UI style ([54eb507](https://github.com/pony-huang/Gimi/commit/54eb507f4bd89bdd559495344aa427b31f7c1f44))
* **sandbox:** animate E2B configuration toggle ([fe9ddeb](https://github.com/pony-huang/Gimi/commit/fe9ddebcdf1ce400671c79976f9e7129bc62224e))
* **sandbox:** center template refresh button and status ([1d68b8b](https://github.com/pony-huang/Gimi/commit/1d68b8b0e8708c856bbd341e33ec9b90199a778f))
* **sandbox:** smooth advanced settings and group environment fields ([614fe21](https://github.com/pony-huang/Gimi/commit/614fe2122401f0a074e1db079d4bfccc2b500ec1))
* **settings:** shrink the update changelog typography ([1251bd9](https://github.com/pony-huang/Gimi/commit/1251bd9a62027df6e89d4e8b28acf7aec72be5e3))


### 优化

* **conversation:** read a bounded event window and persist the drawer preview ([b022be1](https://github.com/pony-huang/Gimi/commit/b022be1a998f8ff2bd5c9cacf79fa32624bb3330))


### 内部重构

* **agent:** adopt ADK ChatCompletions backend ([3b9e5b4](https://github.com/pony-huang/Gimi/commit/3b9e5b448e708df626ff0524928ae25fe3d93f40))
* **agent:** separate Claude mapping and streaming ([f0a3266](https://github.com/pony-huang/Gimi/commit/f0a32663e99a85d4688f186258f9e1c3cdef9a07))

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
