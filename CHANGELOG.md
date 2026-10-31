# 更新日志

版本 PR 根据 Conventional Commits 自动生成后续版本记录；合并前请整理用户可见的变化与升级注意事项。

## [0.14.1](https://github.com/pony-huang/Gimi/compare/v0.14.0...v0.14.1) (2026-10-31)


### Features

* **a2ui:** generate cards asynchronously with inline retry ([074bed7](https://github.com/pony-huang/Gimi/commit/074bed70912a4a6d5b82ed5ea70b06fac7b87f3f))
* **agent:** load Anthropic tools through system messages ([e5ffe76](https://github.com/pony-huang/Gimi/commit/e5ffe76302323af70590a008c50396b14a152f62))
* **chat:** add thinking mode toggle with grouped effort options ([b5afaaa](https://github.com/pony-huang/Gimi/commit/b5afaaa02e4de6fa2a600277968e7790cc436016))
* **recommendation:** add A2UI toggle to personalized services ([d5a50fe](https://github.com/pony-huang/Gimi/commit/d5a50fe2dbfbca6bf4fa6d6765f4c5fe343c9081))
* **recommendation:** note A2UI and tool loading costs in settings ([7907d8c](https://github.com/pony-huang/Gimi/commit/7907d8cefd6068f204a8a3f3de68218b690a9fc5))


### Bug Fixes

* **agent:** adapt DeepSeek deferred tool references ([6bfe98c](https://github.com/pony-huang/Gimi/commit/6bfe98c360b42de4b73400fb197318291c45966e))
* **agent:** load plugin toolsets on demand safely ([9904b70](https://github.com/pony-huang/Gimi/commit/9904b70839b8933827fc0d5b8bef95bf01fe39a7))
* **agent:** remove noisy logging plugin ([3e4a82f](https://github.com/pony-huang/Gimi/commit/3e4a82f9aee1c1fae70f6cfe591872ba3dd2631b))
* **settings:** align personalized services description ([31149b7](https://github.com/pony-huang/Gimi/commit/31149b71936e54190b16b568749a2c6a5cd1f391))

## [0.14.0](https://github.com/pony-huang/Gimi/compare/v0.13.1...v0.14.0) (2026-10-29)


### Features

* **agent:** upgrade ADK to 1.5.0 and add parallel subtasks ([634c92a](https://github.com/pony-huang/Gimi/commit/634c92a29927559cf57b0db15649b4aecc14744e))
* **chat:** add copy action and compact message controls ([aeb7d85](https://github.com/pony-huang/Gimi/commit/aeb7d8570f5d7554a32de1d5e733bd9d65d5f914))
* **conversation:** export history and delete individual sessions ([56da33d](https://github.com/pony-huang/Gimi/commit/56da33d45f98d48ae6592015fe5084f4d3af336b))
* **kimi:** replace formula tools with web search APIs ([c9a1fb7](https://github.com/pony-huang/Gimi/commit/c9a1fb7c8f54eae0a51f1a3b31f794f1def74ba9))
* **modelsettings:** add searchable pickers and refine settings UI ([3e1252d](https://github.com/pony-huang/Gimi/commit/3e1252dc04d2175c2e8f1b7cac8b7e35546eb616))
* prepare v0.14.0 ([0a19c97](https://github.com/pony-huang/Gimi/commit/0a19c97f945406f91d080673d498f2522264c889))
* **settings:** refine personalized services and permission groups ([7fbcdac](https://github.com/pony-huang/Gimi/commit/7fbcdac46dc91ad789e58e253b8e9076389e1689))


### Bug Fixes

* **a2a:** isolate remote agent failures from chat ([6cbce84](https://github.com/pony-huang/Gimi/commit/6cbce843bafbc26622631e1cf64af484e2a07232))
* **build:** avoid resource-only tests in empty feature modules ([cb59738](https://github.com/pony-huang/Gimi/commit/cb59738b9896f66f0e6be7bb31f23b6dc0c82e68))
* **chat:** compact composer controls and unify input hint ([1132d58](https://github.com/pony-huang/Gimi/commit/1132d5874a5fd3b0b445b41dee9ffb46b3829baa))
* **conversation:** use managed storage for export staging ([151b229](https://github.com/pony-huang/Gimi/commit/151b22923961758ce00d640382a9e48ffe01d2e4))
* **designsystem:** align function icon tint with other icons ([a2330c2](https://github.com/pony-huang/Gimi/commit/a2330c26dbf4e1887b700dfa5979dfd276d95a52))
* **plugin:** put overflow menu before switch in plugin list rows ([5d7dc91](https://github.com/pony-huang/Gimi/commit/5d7dc9166449ee1f3fec28f7c6a1806dbbd6b750))

## [0.13.1](https://github.com/pony-huang/Gimi/compare/v0.13.0...v0.13.1) (2026-10-21)


### Bug Fixes

* **mcp:** preserve official runtime lookup targets in release ([e9a2d3b](https://github.com/pony-huang/Gimi/commit/e9a2d3b52ff1713766350ea37167fbe930e29963))
* **mcp:** preserve official runtime lookup targets in release ([11d34dc](https://github.com/pony-huang/Gimi/commit/11d34dcc12333521722e77e02e2a7f7d9355b764))

## [0.13.0](https://github.com/pony-huang/Gimi/compare/v0.12.2...v0.13.0) (2026-10-20)


### Features

* **a2ui:** add native news list component ([2396cff](https://github.com/pony-huang/Gimi/commit/2396cff79ed922c3224731a1bbf5db0015710f98))
* **a2ui:** add native read-only data table ([2412cee](https://github.com/pony-huang/Gimi/commit/2412ceeaab767b92e0cd59a83da7e1ae1a8e02c3))
* **a2ui:** add native weather cards and routing ([46abe03](https://github.com/pony-huang/Gimi/commit/46abe03f0c0f0d2851bc6174c98ed20adaae3e40))
* **a2ui:** add Spotify mini player ([90ee2be](https://github.com/pony-huang/Gimi/commit/90ee2bec78b476f4adfcfb66163ab951c475b0ae))
* **a2ui:** animate surface child state transitions ([5887aae](https://github.com/pony-huang/Gimi/commit/5887aae170ab4ac2177018e883ec901470055f74))
* **a2ui:** generalize player card to any music provider ([ba14fb5](https://github.com/pony-huang/Gimi/commit/ba14fb5dac56926761a6c69cc9954e5f381a0412))
* **a2ui:** refine card styling and input generation guidance ([bc8da6b](https://github.com/pony-huang/Gimi/commit/bc8da6b3e917d554819e3eb404e6a3dcb11d7fb4))
* **a2ui:** require controls for state-changing requests ([0dfb174](https://github.com/pony-huang/Gimi/commit/0dfb174d1c16c0ee1a998a8316855bc67636125c))
* **chat:** integrate A2UI native agent surfaces ([766329a](https://github.com/pony-huang/Gimi/commit/766329af2491ae9d369f0fe4a103572813033738))
* **chat:** move run progress into tool timeline with elapsed time ([6b41c88](https://github.com/pony-huang/Gimi/commit/6b41c88077756cbd38ad541cc451278ba4714848))
* **chat:** streamline run status and activity summary ([0d89a4f](https://github.com/pony-huang/Gimi/commit/0d89a4f6157acd3d4b3adf4604afac53defe8233))
* **chat:** support A2UI media components ([77951fe](https://github.com/pony-huang/Gimi/commit/77951fe625f31ec887fd4603b574a5f9656fc7ec))
* **plugin:** add reusable OAuth status and external links ([cd423d3](https://github.com/pony-huang/Gimi/commit/cd423d37c1bebf9537d39210f5d469aff5cba0ab))
* **plugin:** add subscription-based plugin list and details ([8f34da1](https://github.com/pony-huang/Gimi/commit/8f34da10ac8895e076ca0e2b9874fedc733a660c))
* **plugin:** generate subscription catalog from release APKs ([31655c3](https://github.com/pony-huang/Gimi/commit/31655c3e574bc4d15f2dbec72aed21163f1ac962))
* **plugin:** merge subscription-based plugin catalog ([a1994ed](https://github.com/pony-huang/Gimi/commit/a1994ed22a5ce01e55ad3b29f609bf0a310c9170))
* **plugins:** add Weibo and Zhihu setup links ([3d181dd](https://github.com/pony-huang/Gimi/commit/3d181ddc08c2148996ca9e7ea352cdefdc607c0f))
* **v2ex:** add token settings link ([95c53c3](https://github.com/pony-huang/Gimi/commit/95c53c3f9aacba530f807691c71fab42a752689c))


### Bug Fixes

* **a2ui:** preserve surface bindings on form submission ([b447297](https://github.com/pony-huang/Gimi/commit/b4472975b3cfc65174ba75008b84c64bf1779aec))
* **a2ui:** use official media controls and center footer actions ([6d4d1b2](https://github.com/pony-huang/Gimi/commit/6d4d1b2d69ff2db918471c1eec8d07810412db6f))
* **agent:** handle tool history after cancellation ([491aefb](https://github.com/pony-huang/Gimi/commit/491aefbb864282abf41bdea2161eac41f938ad6a))
* **chat:** blend collapsed activity panel into background ([2a8fcca](https://github.com/pony-huang/Gimi/commit/2a8fcca4bab2f50a3af71533ef2635275e87ef3a))
* **chat:** constrain tool sheet list heights ([e9be027](https://github.com/pony-huang/Gimi/commit/e9be027482b3cc056cf7e88b0ad5f6ec5528dcea))
* **chat:** hide card action placeholder bubbles ([cd27b6a](https://github.com/pony-huang/Gimi/commit/cd27b6a84d70cebd775c3008b287c0e3d95a4f8d))
* **chat:** inset assistant text from composer ([a9944b9](https://github.com/pony-huang/Gimi/commit/a9944b905ae37fb58e68af097fc403bac6d7e3c0))
* **chat:** unify progress panel and align expansion arrow ([6af35cb](https://github.com/pony-huang/Gimi/commit/6af35cb783c3568177dc98be0376418bd451b46b))
* **chat:** widen assistant message text ([fe7c38a](https://github.com/pony-huang/Gimi/commit/fe7c38adbb08827ac0c1db87ba008ac84d4b8369))
* **spotify:** correct API requests and playback reporting ([6dded32](https://github.com/pony-huang/Gimi/commit/6dded3284a90b79695bbbd562fa26b9e7b1ebd6e))
* use typed dropdown anchors to prevent settings crashes ([3b7239e](https://github.com/pony-huang/Gimi/commit/3b7239e5c482d95be124b4e60e38fed30cd203c2))
* **workspace:** align cache page terminology ([e178959](https://github.com/pony-huang/Gimi/commit/e178959419b4338c3c89ce4068375c9f370ed922))

## [0.12.2](https://github.com/pony-huang/Gimi/compare/v0.12.1...v0.12.2) (2026-10-17)


### Features

* **deepseek:** add official search and fetch tools ([7f2f706](https://github.com/pony-huang/Gimi/commit/7f2f70614060fc9776f77e67ab67805b484ea7d6))
* **deepseek:** support official search and fetch tools ([1457951](https://github.com/pony-huang/Gimi/commit/14579511f593b45de52bf824ae88a9f5ea7591f1))

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
