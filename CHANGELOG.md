# Changelog

## [0.5.0](https://github.com/pony-huang/Gimi/compare/v0.4.0...v0.5.0) (2026-09-05)


### Features

* add agent recommendations ([75053a0](https://github.com/pony-huang/Gimi/commit/75053a0807b10d2e2ccd5a1137dcda966646e3f7))
* **agent:** support manual MCP server configuration from chat ([0a36b2e](https://github.com/pony-huang/Gimi/commit/0a36b2e45e6852671a846b37ab51f63e9c7b2971))
* **agent:** 本地文件搜索零结果按分词放宽重试并引导换关键字 ([47cdca0](https://github.com/pony-huang/Gimi/commit/47cdca05c1a7cb2dfae12f07a40cd4f2fca3db0a))
* **assistant:** consolidate assistant voice feature and surface UI ([cd88d99](https://github.com/pony-huang/Gimi/commit/cd88d99b0f72a160baccd48e737d413255776563))
* **assistant:** redesign voice wake surface as capsule and panel ([b917ece](https://github.com/pony-huang/Gimi/commit/b917ece84dec626d8a3843ddb21bd2b4faca0da8))
* **assistant:** remove overlapping header status title ([f605b18](https://github.com/pony-huang/Gimi/commit/f605b181dd0627944c17baf9ca454aa80df467eb))
* **chat:** Markdown 代码块接入语法高亮与语言头 ([71c752a](https://github.com/pony-huang/Gimi/commit/71c752a28f7b1e00b54e79a0bc7e33094b3bd379))
* **chat:** send message on hardware Enter key ([421d4a7](https://github.com/pony-huang/Gimi/commit/421d4a70c7fff49ac6951399d3433c49c758e6c8))
* **ci:** publish plugin APKs in release workflow ([f999096](https://github.com/pony-huang/Gimi/commit/f99909689639c5d967a5056ae064ed38dc8a3ba8))
* **conversation:** 本地搜索工具名判断与解析失败诊断日志 ([03b3e9e](https://github.com/pony-huang/Gimi/commit/03b3e9ef0227149f7e3c78865e03c72d68589242))
* **designsystem:** share chat bubble and markdown text components ([ce001be](https://github.com/pony-huang/Gimi/commit/ce001be6b2551b076c969d08fe43f7d90ff68b4b))
* manage Mem0 cloud memories ([dbbee9b](https://github.com/pony-huang/Gimi/commit/dbbee9bcf1f6458b1965accfeebcad155eedbcae))
* **memory:** Mem0 关闭时隐藏 Token 配置区 ([1d3e89f](https://github.com/pony-huang/Gimi/commit/1d3e89fc7ac6556b4046f357fe67fda34c5dbb5b))
* **plugin:** add config-page actions and save feedback ([2689dd6](https://github.com/pony-huang/Gimi/commit/2689dd65b6d522fc10783b3dc64945a00aba53df))
* **plugin:** add Spotify plugin and context-attach SPI ([dff10f2](https://github.com/pony-huang/Gimi/commit/dff10f2b15b16b243663dd9c96472cc7fbd72934))
* **plugin:** display plugin app icons in settings list ([0b880c9](https://github.com/pony-huang/Gimi/commit/0b880c995a676fff7f858e63c383b9161c265dbf))
* **plugin:** dynamic plugin loading and settings management ([c1c6a3f](https://github.com/pony-huang/Gimi/commit/c1c6a3fc1e28004315af755d55af4350f50ee4a1))
* **plugin:** hot-reload plugins and in-app WebView auth ([8b019ee](https://github.com/pony-huang/Gimi/commit/8b019ee4aa4320ab1f7d459c3ab16ffe22e2d112))
* **plugin:** pull-to-refresh & slim plugin list rows ([7aa71ce](https://github.com/pony-huang/Gimi/commit/7aa71ce84d483f4d8d4411c54e7570e7526b4226))
* **plugins:** add V2EX plugin ([e9253ea](https://github.com/pony-huang/Gimi/commit/e9253eab262753892410837eadd35c614cc87408))
* **plugins:** list common V2EX nodes and aliases in tool instructions ([931858f](https://github.com/pony-huang/Gimi/commit/931858f8dac6a9e7fd49127ee5d0c49078c98580))
* **plugins:** switch V2EX plugin to API 2.0 ([9e0e565](https://github.com/pony-huang/Gimi/commit/9e0e5653a7b9d78b55e6543f80e375398fc64d2d))
* **plugins:** v2ex 图标替换为官方 Simple Icons mark ([ea832cc](https://github.com/pony-huang/Gimi/commit/ea832ccfbb55f682b252becb219322e3c65ac88c))
* **plugins:** 小红书/Spotify 插件配置应用图标 ([d59c6b8](https://github.com/pony-huang/Gimi/commit/d59c6b8c31613775ade166f8d150b99381d9525c))
* **plugin:** wire AgentPlugin toolSets into agent runtime ([5862632](https://github.com/pony-huang/Gimi/commit/5862632ee367b9eb10710ce659a1c12fd1242745))
* **plugin:** 移除小红书发布功能 ([e21e777](https://github.com/pony-huang/Gimi/commit/e21e7774b283fa5c05388adbe06cfacea74b8982))
* **recommendation:** 推荐关闭时隐藏更新配置区 ([b336c9e](https://github.com/pony-huang/Gimi/commit/b336c9e05bc0a7a18ea29c29fcfce739804a0819))
* **recommendation:** 更新失败按 30/60/120/180 秒自动重试 ([4f42165](https://github.com/pony-huang/Gimi/commit/4f421651d8110b4acd81f8ea5151b30004b9b844))
* reuse current chat for voice assistant ([1547ced](https://github.com/pony-huang/Gimi/commit/1547ced4ba53820d491c0d48d0ebbf52ff3b4e53))
* **settings:** polish tools-related copy ([0f22355](https://github.com/pony-huang/Gimi/commit/0f22355003b660de9bd302461ec33a60c8e258bd))
* **settings:** shorten tool entries to Tools and MCP ([fed15f9](https://github.com/pony-huang/Gimi/commit/fed15f9ba853228f7e7196af0d91d31110fbc28e))
* show wake capture in current chat ([d2cb32b](https://github.com/pony-huang/Gimi/commit/d2cb32bae10383ccf3336f33b18573877a361ea9))
* **spotify:** align tools with official Web API and streamline schema ([e9acfa6](https://github.com/pony-huang/Gimi/commit/e9acfa6fe105421460eaac209d5bee913c4b7a6e))
* **spotify:** align tools with official Web API and streamline schema ([3021dcc](https://github.com/pony-huang/Gimi/commit/3021dcc965b8956abb138af13e21e4c54f743a37))
* **toolauth:** 自定义工具关闭时隐藏配置工具行 ([204638a](https://github.com/pony-huang/Gimi/commit/204638a22f513f835de32441ca97a6cd98bab365))
* **zhihu:** add quota, knowledge base and async task tools ([ceeac84](https://github.com/pony-huang/Gimi/commit/ceeac84e197e76de240b582603a204ffa0b123c5))
* 优化语音唤醒逻辑功能；移除冗余配置；支持自定义唤醒词语 ([888b0b6](https://github.com/pony-huang/Gimi/commit/888b0b6658c8e2019c8aeed5dbb88f5a7e0412ea))
* 优化语音唤醒逻辑功能；移除冗余配置；支持自定义唤醒词语 ([d543156](https://github.com/pony-huang/Gimi/commit/d543156b988a470cfb78b5e755a210242ddf3046))
* 忽略目录 ([8eadb12](https://github.com/pony-huang/Gimi/commit/8eadb128c89348b0f5869af0d1930dd8a0d37c18))
* 推荐面板改为扩展 FAB，任务生成数提升到 6 ([6930dc7](https://github.com/pony-huang/Gimi/commit/6930dc73a0b26230eba14a0b88ad160ec3d0b671))
* 推荐面板贴底对齐胶囊，卡片瘦身并支持长按看全文 ([e79a2d1](https://github.com/pony-huang/Gimi/commit/e79a2d1a132cd998d66d2939d8de0725720167c9))
* 新增推理强度配置（以openai, claude为准）；优化UI界面设计 ([f933676](https://github.com/pony-huang/Gimi/commit/f9336767cec1a6c057a4ce7b6ce45937f0d21853))


### Bug Fixes

* **agent:** 系统工具跳转外部应用改为等待返回值避免回合中断 ([5ce330d](https://github.com/pony-huang/Gimi/commit/5ce330de0de4746ae89be9e2246624e320435999))
* **app:** wire data:appearance into app and feature:chat dependency graphs ([b60f6b2](https://github.com/pony-huang/Gimi/commit/b60f6b2e5cc9fb1ae0503bb1c1ab543fe6787d5c))
* **app:** 修复若干问题 ([3b4f41b](https://github.com/pony-huang/Gimi/commit/3b4f41b07ec0c4d2680b6d0482c476d8c62e9814))
* **build:** suppress R8 missing-class for snakeyaml java.beans ([55eb927](https://github.com/pony-huang/Gimi/commit/55eb927f9524d9a830cde0879b11744d626be320))
* **chat:** 查看全部结果页共享会话 ViewModel 并优先取非空结果 ([e2510ca](https://github.com/pony-huang/Gimi/commit/e2510cabf019b765818c635b66d9c2a2b417ece2))
* **chat:** 确认占位响应不再挤掉本地文件轮播的真实结果 ([0c085d0](https://github.com/pony-huang/Gimi/commit/0c085d0795b65b1b83d8399780a5f5933ae81e9d))
* **data:plugin:** 改用 Map 构造器规避 R8 保存配置闪退 ([898ffa6](https://github.com/pony-huang/Gimi/commit/898ffa67fd6e0b1f51356173a080d625e55deeb0))
* **feature:plugin:** 内置浏览器 WebView 黑屏，授权 URL 未加载 ([972d9e4](https://github.com/pony-huang/Gimi/commit/972d9e4e5e118c4c4d31e81281105e40772f31df))
* **mcp:** 空态主视觉替换为 MCP 品牌图标 ([35b7bae](https://github.com/pony-huang/Gimi/commit/35b7bae6f6dee023b57f24f529cc67b8008295d8))
* **memory:** Mem0 开关改为乐观置位以展开 Token 配置区 ([088683d](https://github.com/pony-huang/Gimi/commit/088683d59a9aee4b6e5262a324fd8c1c76c15796))
* parse recommendation array responses ([b968a8b](https://github.com/pony-huang/Gimi/commit/b968a8b1b9749787dc479188b0539235c4369dc8))
* **permissions:** 操作卡与权限列表卡之间补充组间距 ([0802515](https://github.com/pony-huang/Gimi/commit/08025154e849e1d0e3e68918338f67924371dcc1))
* **plugin:** align xiaohongshu automation with xpzouying/xiaohongshu-mcp ([d2653ad](https://github.com/pony-huang/Gimi/commit/d2653adc6c6ff23e85d9856962f0789317f7301c))
* **plugin:** preserve Kotlin/coroutines ABI bridge for DCL plugins ([4d7d402](https://github.com/pony-huang/Gimi/commit/4d7d402c368a45d3261392b2632ee65e7d058896))
* **plugins:** drop nonexistent created field from V2EX node projection ([27affb9](https://github.com/pony-huang/Gimi/commit/27affb9b6c625a77b4f1770f035660a02f98757f))
* **plugin:** self-contained https upgrade for xiaohongshu search ([d1d36f3](https://github.com/pony-huang/Gimi/commit/d1d36f33c3ddeb33aafea72faee0cc5c36b226e7))
* **plugin:** stop xiaohongshu search hanging on data-readiness wait ([e189abe](https://github.com/pony-huang/Gimi/commit/e189abe7e1f05497d956e25a7e674faa0534fe08))
* preserve release plugin configuration ([86a9983](https://github.com/pony-huang/Gimi/commit/86a99832d2f233441630203a917828f1c09edc27))
* reuse wake session after recognition ([7c35e8d](https://github.com/pony-huang/Gimi/commit/7c35e8de54ec7d8adc0ff2689f39747f95387e41))
* **test:** 修复推荐测试快照数量 + Minimax 时序 flaky ([d0ddf60](https://github.com/pony-huang/Gimi/commit/d0ddf60d1ae9973370572aa6f3ded02f19d40093))


### Code Refactoring

* **agent:** decouple data:agent from data:plugin via PluginRuntimeProvider ([8c2c60d](https://github.com/pony-huang/Gimi/commit/8c2c60dc0a88262b093f471a183f9aae1bf24e6d))
* **app:** narrow Recommendation startup contract via domain interface ([73bed93](https://github.com/pony-huang/Gimi/commit/73bed93aedb21ee19093dba8c7c1a54117e9bd23))
* **app:** slim AppNavigation.kt by moving chat logic to feature:chat ([818cfde](https://github.com/pony-huang/Gimi/commit/818cfde7c56ea818a8fc1e18417e3a910fc027ca))
* **app:** wire global appearance settings via new domain:appearance module ([218477e](https://github.com/pony-huang/Gimi/commit/218477e7b68c408f01fd4d12d390ea2e3a1bc723))
* **app:** 收窄 R8 keep 规则以允许死代码收缩 ([6503049](https://github.com/pony-huang/Gimi/commit/6503049d8aacf335166beb3f5fe5af3962bb46cd))
* **chat:** 添加到聊天抽屉同步 One UI 风格 ([e7cd930](https://github.com/pony-huang/Gimi/commit/e7cd930b0029343c9b9cbd7ce6cd63421eccf634))
* complete Android structure remediation ([c63b365](https://github.com/pony-huang/Gimi/commit/c63b3659ba310d6d49a55ae9a5326e07fe12c36b))
* **core:** adopt gimi convention plugins in core:audio, core:designsystem ([f273897](https://github.com/pony-huang/Gimi/commit/f273897dd54ea947992eb158c3ef1615ee656544))
* **core:** separate conversation test fixtures from core:testing ([f4ef389](https://github.com/pony-huang/Gimi/commit/f4ef389e31f9491ed23342094d963ccd93c26d9f))
* **designsystem:** tighten API per CORE-02/03/04 ([1848677](https://github.com/pony-huang/Gimi/commit/1848677701cef027b7385128124a5ab3bda6537d))
* **designsystem:** 新增 One UI 风格偏好组件与画布配色 ([9080eee](https://github.com/pony-huang/Gimi/commit/9080eee61f075bb656465093da757804966f5f7b))
* **feature:** self-contain PreferenceScaffold in feature modules ([582bef5](https://github.com/pony-huang/Gimi/commit/582bef525adf76ef630a13bbadb33314da7d0d57))
* **mcp:** 移除导入页顶部帮助文案 ([eabf2b1](https://github.com/pony-huang/Gimi/commit/eabf2b18b081053222a7ec0deb925be74c92abff))
* **plugin:** harden API clients, cache tools, dedupe JSON conversion ([e49f393](https://github.com/pony-huang/Gimi/commit/e49f393a08010b874cc07afe9b608ea2f518d88e))
* **plugins:** tighten V2EX tool instructions ([642dea4](https://github.com/pony-huang/Gimi/commit/642dea419c4aef5cc64b4de7f8051e4fa47c41ec))
* **serialization:** 首包数据类序列化从 Gson 迁移到 kotlinx.serialization ([dd341ee](https://github.com/pony-huang/Gimi/commit/dd341ee8129ae3fc866a18047690ff5b87a69957))
* **ui:** 其余设置子页迁移分组卡片并移除 PreferenceCard ([a77a8be](https://github.com/pony-huang/Gimi/commit/a77a8be8c85bf3a7d99fa8e4d49490177c492871))
* **ui:** 设置主页与模型设置迁移 One UI 分组卡片 ([ba43564](https://github.com/pony-huang/Gimi/commit/ba43564804d33949090171b0d24e8be486353ea0))
* **voicewake:** scaffold VoiceAudioPipeline coordinator in data:voicewake ([a870891](https://github.com/pony-huang/Gimi/commit/a870891f552c48ed638a2c747eb2270d87ec1f93))
* **voicewake:** 唤醒模型共性描述收敛为标题下备注 ([f5e6d2f](https://github.com/pony-huang/Gimi/commit/f5e6d2f4c88a5dfc96d82f1c6238fe11e43c22de))


### Documentation

* center README demo video ([9174469](https://github.com/pony-huang/Gimi/commit/917446962cc92f10e2c253fe7b2932a532d704ab))
* clarify plugin overview ([0855d82](https://github.com/pony-huang/Gimi/commit/0855d820879630411504fb49a95482780c97787a))
* document worktree temp sharing via symlinkDirectories ([311b776](https://github.com/pony-huang/Gimi/commit/311b77682cdf926622579c1eb8415e43f2d5680e))
* **plugin-api:** annotate browser as generic auth/capture channel ([36434d5](https://github.com/pony-huang/Gimi/commit/36434d5a4bf36eae8de6fd0732765009988fb7d4))
* README 头部添加产品图标与 CI/Release/License 徽章 ([086def6](https://github.com/pony-huang/Gimi/commit/086def602bc51d187e4dc0a3b64043fc863fc253))
* README 截图区新增模型服务页面截图 ([93886b6](https://github.com/pony-huang/Gimi/commit/93886b6b78e1c3032f30247b13ae6bfc6bfc9a4c))
* refine README demo copy ([2a5902d](https://github.com/pony-huang/Gimi/commit/2a5902dd6cc5d7bd796248f84d82a9f006b57503))
* refresh README screenshots ([a35bb21](https://github.com/pony-huang/Gimi/commit/a35bb216dc5772c5ebd59490969e0aae59b96c1e))
* sync README with plugins, MCP and provider updates ([4334f6c](https://github.com/pony-huang/Gimi/commit/4334f6cd7803b9106c0542cd4b46d0c25d15a8f0))
* update README demo video ([7606ec4](https://github.com/pony-huang/Gimi/commit/7606ec4a4ded0638c253b590311548fec13f279c))
* 新增中英文隐私政策并在 README 挂链接 ([94ce8bb](https://github.com/pony-huang/Gimi/commit/94ce8bbf6b4b02fdbc255216f8886c86a55a9103))
* 明确设备查询用 adb、其余操作优先 android CLI ([417963d](https://github.com/pony-huang/Gimi/commit/417963dbb94cf03de175b1c3ef8c96453b7b05b0))
* 补充 MediaPipe ([732b316](https://github.com/pony-huang/Gimi/commit/732b31687a0507311d910f6a8914738b307c2231))

## Changelog

本文件由 [release-please](https://github.com/googleapis/release-please) 自动维护：
合并机器人创建的 Release PR 时，新版本的变更段落会自动插入到下方。
