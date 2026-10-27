<div align="center">

<img src="doc/icon.webp" width="192" alt="Gimi" />

# Gimi

**Android AI 助手，对话、附件和配置保存在设备本地。**

支持文本与语音交互、Agent 生成的原生卡片、手机工具调用、后台静默操作，以及
AppFunction、官方工具、APK 插件、MCP、远程 A2A Agent、技能和云端沙盒扩展。

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

---

## 功能概述

配置自己的 API Key 后，可在对话中查询日程、设置闹钟、播放媒体、调节亮度、搜索和读取可访问文件，也可调用模型服务的官方工具、插件、远程 MCP 服务、远程 A2A Agent，以及其他 Android 应用启用的 AppFunction。完成 Shizuku 授权后，支持图片理解的模型还可在后台查看和操作其他应用。配置 E2B API Key 后，还可在隔离的云端沙盒中执行命令和代码。

## 功能说明

### 聊天

流式回复，支持拍照、相册发图，以及按模型能力添加文档或音频附件，也可接收其他应用分享的图片。图片和本地文件搜索结果可直接在对话中查看；思考内容、工具调用和返回结果按轮次分组展示，可展开查看。

Agent 回复中还可以包含选项卡、音乐播放器、简化天气卡、表格、面板、链接和媒体控件等原生 A2UI 界面。这些卡片根据经过校验的目录数据生成，会随回复流式更新，并能把交互结果发送回 Agent。

每个会话可单独选择模型、MCP 连接、官方工具和推理强度。工具加载方式（按需加载或全量加载）是全局设置，位于“设置 → 工具授权”页，新安装默认全量加载；权限模式（“请求批准”/“完全批准”）同样为全局开关，可在输入栏的“添加到聊天”面板里切换。Agent 需要补充信息时，会在输入栏中请求文字回复或让你从选项中选择。输入栏会显示当前模型，点按后可从底部抽屉切换所有已启用服务下的模型。

任务可在应用切到后台后继续运行。允许通知后，工具执行、需要批准或补充输入，以及任务完成时可收到通知；返回会话可继续查看回复和任务状态。

空会话可展示由 Agent 根据已启用工具、插件和你允许提供的上下文生成的任务建议，点一下即可开始；可在设置中关闭、立即刷新或调整后台更新间隔。

### 语音

点按麦克风录音；点击停止按钮会把转写结果写入输入栏，可编辑后发送，点击发送按钮则转写后直接发出。语音识别和语音合成模型分别在“设置 → 默认模型”配置；语音识别支持 MiniMax 和 MiMo（小米），并可选择回复朗读的音色，MiniMax 音色列表支持在线获取。

可自动朗读完整回复。

### 内置工具

| 工具    | 能力                   |
|--------|------------------------|
| **时间** | 闹钟、倒计时、看时间        |
| **日历** | 查看和创建日程            |
| **媒体** | 播放、暂停、切歌（支持其他应用） |
| **音量** | 读取和调节媒体音量          |
| **显示** | 亮度、自动亮度、息屏时间      |
| **位置** | 获取位置、地图打开          |
| **文件** | 搜索图片、视频、音频和授权的文档，读取可访问的本地文件 |
| **应用** | 查看、搜索、打开应用；拍照、录像 |
| **联系** | 拨号、发短信、查联系人       |
| **网页** | 联网搜索、打开链接          |
| **设置** | 跳转系统设置页            |

在“设置 → 工具授权”中开启“自定义工具”后，可逐项选择允许使用的本地工具；关闭时全部本地工具可用。

### AppFunction

在支持该能力的 Android 16+ 设备上，Gimi 可以发现其他已安装应用声明的功能。进入“设置 →
AppFunciton”并启用应用后，其 AppFunction 会提供给 Agent 使用；页面会展示每个应用的功能、
参数和当前系统状态。

跨应用执行需要系统特权权限 `EXECUTE_APP_FUNCTIONS`，目标应用也可能进一步限制 Gimi 可以调用的
功能。许多设备没有为这项权限提供普通设置开关。若需在支持的设备上进行本地测试，请按
[AppFunction 执行会话指南](spec/zh/appfunctions.md)通过 ADB 临时以 Shell 身份启动 Gimi。

### 后台静默操作

让 Agent 在后台查看和操作其他应用，不打断当前手机上的操作。支持打开应用、查看界面、按界面元素点击或填写文字、坐标点击、滑动、返回和等待；操作后会重新观察界面。

使用前需安装 Shizuku，以无线调试或 ADB 启动 ADB shell 模式服务，然后在“设置 → Shizuku”中开启后台操作并授权 Gimi。所选对话模型需支持图片理解；Shizuku 运行时会自动提供相关工具，无需在本地工具列表中逐项开启。

文字输入和原生界面元素操作还需开启“Gimi”无障碍服务；设置页可查看运行状态和授权情况。

后台应用以小窗浮层显示，需在“设置 → 权限管理”开启“悬浮窗”权限。

### 官方工具

在“设置 → API 接入”中配置并启用对应服务及其官方工具，再在会话的“官方工具”面板选择使用的能力。

| 服务 | 能力 |
|------|------|
| **Anthropic** | 联网搜索 |
| **MiniMax** | 联网搜索、文生图、参考图生成 |
| **智谱 GLM** | 联网搜索、网页读取 |
| **Kimi** | Kimi 联网搜索 Basic / Pro、网页抓取 |

厂商原生执行的联网搜索（Anthropic、MiniMax）由厂商远端完成检索，在对应服务的 Anthropic 协议下提供。MiniMax 图像生成可与其他服务的对话模型混用，使用 MiniMax 服务的凭据。

### MCP

连接远程 MCP 服务器（Streamable HTTP），想加什么工具都行。手动新建，或者把文档里的 `mcpServers` JSON、甚至一条 curl 命令直接粘进来，Gimi 会帮你解析。可设置 Bearer Token 和自定义请求头，测试连接，并查看每个服务器暴露的工具和资源；可随时停用。

在 *设置 → MCP* 里点「导入」，把服务器文档中的 `mcpServers` JSON 或 curl 片段粘进去即可。

### 远程 Agent（A2A）

在“设置 → A2A”中接入实现 A2A 协议的 Agent。填写 Agent 地址和可选的 Bearer Token，启用后
Gimi 会测试连接并读取 Agent Card，展示其声明的技能、输入/输出格式和流式传输能力。已启用的
Agent 会作为对话中的任务委派目标，主助手可以把合适的任务交给远程 Agent，并在当前会话中使用
返回结果。

Android 客户端通过 HTTP 使用 A2A JSON-RPC。仓库内提供了本地示例服务及手机接入步骤，参见
[A2A 示例服务指南](a2a-server/README.md)。

### 云端沙盒

在隔离的云端环境中执行命令和代码。在“设置 → 沙盒”中开启 E2B，填写域名和 API Key；密钥、环境变量和其余配置都使用 Android Keystore 加密保存在本机。模板默认 `code-interpreter-v1`，自定义模板需包含可访问的 Code Interpreter 才能执行代码；闲置超时默认 300 秒。

每个会话拥有独立环境：同一会话跨轮保留文件、安装的依赖和各语言内核变量，不同会话相互隔离。命令和代码调用可传入单次环境变量和超时，命令还可指定工作目录；Agent 可运行 Python 或 JavaScript 并在有状态内核中保留变量、启动后台命令并获取进程 ID（传入端口时还会返回预览地址），也可读写和列出文本文件（上限 1 MiB）。环境在首次调用时创建，闲置超时或配置变更、关闭后销毁。

详见 [E2B 文档](https://e2b.dev/docs)。

### 插件

安装 APK 插件即可扩展能力；刷新列表后立即生效，无需重启。插件自带工具，也支持应用内授权流程。

项目提供以下插件 APK，随 GitHub Release 单独发布。也可以在**设置 → 插件**中浏览订阅源并直接安装。默认订阅源是最新正式 GitHub Release 中自动生成的 `plugin-subscription.json` 附件，可在插件页右上角的订阅图标中修改；自定义源由你自行信任。安装时下载对应 APK，校验 SHA-256 后交由系统安装器。

默认订阅源收录：

- **Spotify**：搜索、播放、歌单和音乐库
- **知乎**：搜索、热榜和问答
- **V2EX**：提醒、节点/主题/回复浏览和账号信息（需 Personal Access Token）
- **微博**：热搜、智搜摘要、授权账号微博，以及超话浏览、发帖、评论与回复（需配置 App ID 和 App Secret）
- **小红书**：登录、浏览推荐与搜索、查看主页和笔记、评论与互动、通知，以及图文和视频发布
- **网易云音乐**：匿名或扫码登录、歌曲与歌词搜索、用户资料、歌单、歌曲详情、授权音频链接、创建歌单、加歌/删歌和红心操作（需 App ID、RSA PKCS#8 私钥及对应接口权限；暂不支持播放控制、云盘上传和笔记发布）

**小红书说明**：插件直接通过设备上的 WebView 操作网页，不需要 MCP 服务器或中转地址。

凭据格式、登录流程、工具范围和平台限制参见[网易云音乐插件说明](spec/zh/netease-plugin.md)。

第三方也可以通过公开的插件 API 自行开发。

### 记忆

默认使用设备本地记忆，在后续对话中保存和召回相关信息；也可在 *设置 → 记忆* 启用 Mem0 长期记忆；Mem0 Token 安全保存在设备上。

### 技能

在“设置 → Skills”中从链接或本地 ZIP 安装技能包。技能包含说明文档和配套资源；配置云端沙盒后，助手可在该会话的沙盒中运行技能脚本，也可以直接在沙盒里打包并安装技能。

### 文件与工作区

在 Android 设置中授予“所有文件访问权限”后，Gimi 可按文件名搜索所有可访问的已挂载共享存储，
包括未被 MediaStore 索引的文件；应用私有目录会被排除。只有文件搜索需要权限时才会发起请求，
拒绝后不会反复提示。共享图片、视频和音频的搜索还会遵循对应的媒体权限。

聊天中发送的附件统一保存在设备本地工作区，可在“设置 → 缓存区”查看、打开和批量删除。

### 授权与数据

- 需要确认的敏感操作会暂停，等待允许或拒绝。
- 权限模式（“请求批准”/“完全批准”）为全局设置；完全批准会自动放行需要确认的工具调用。
- 本地工具可通过“自定义工具”逐项限制，系统权限由用户按需授权。
- “所有文件访问权限”控制共享存储中的文档搜索，可随时在 Android 设置中撤销。
- 权限管理页面说明每项权限的用途。
- API Key、远程 Agent Token 和其他凭据保存在设备本地，仅用于调用对应的已配置服务或端点。

## 模型

自带 API Key。Gimi 内置以下服务商的预设，也兼容任何 Chat Completions 或 Anthropic API 端点。

| 服务商                                                                                              | 接口协议                |
|-----------------------------------------------------------------------------------------------------|-------------------------|
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** | Chat Completions |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** | Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Moonshot" /> **月之暗面 Kimi** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **智谱 GLM** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **小米 MiMo** | Chat Completions / Anthropic API |

可分别配置默认对话模型、快速模型（会话标题与任务推荐）、语音识别模型、语音合成模型及播放音色。附件、推理和工具调用能力以所选模型的支持范围为准。

## 快速上手

1. 安装适配设备的 APK。需要 Android 14+。
2. *设置 → API 接入*：选择服务商，填写 Key 和服务地址，检测连接并启用服务。
3. *设置 → 默认模型*：选择对话模型和快速模型；需要语音时，分别配置语音识别、语音合成模型和播放音色。
4. *设置 → 工具授权*：按需开启“自定义工具”并选择本地工具，也可调整工具加载方式。
5. *设置 → 权限管理*：按需授予系统权限；后台任务通知需允许通知权限。
6. 可选：在 *设置 → 记忆* 配置本地或 Mem0 记忆，在 *设置 → 智能推荐* 管理空会话任务建议。
7. 开始对话。

可选扩展：添加 MCP 服务器、接入远程 A2A Agent、启用 AppFunction、安装插件或技能、启用官方工具、授予所有文件访问权限、配置 E2B 云端沙盒，以及配置 Shizuku 后台静默操作。

## 隐私

Gimi 不收集任何数据：无统计埋点、无遥测、无账号，开发者也没有服务器。对话保存在你的设备上，
网络请求用于已配置或启用的模型、语音、MCP、远程 A2A Agent、插件、Mem0 等服务，以及技能下载、
E2B 沙盒使用和 GitHub 更新。
详见[隐私政策](PRIVACY.zh-CN.md) · [Privacy Policy](PRIVACY.md)。

## 参与贡献

欢迎反馈问题、提出功能建议、改进文档与翻译，以及提交代码贡献。贡献流程与开发规范请参阅 [CONTRIBUTING.md](CONTRIBUTING.md)（仅提供英文版）。

项目功能与界面设计主要基于作者本人的使用习惯，同时借鉴市面上成熟产品的设计与交互经验。提出改进建议时，请说明具体使用场景；较大的功能或界面调整，建议先通过 Issue 讨论。

## 致谢

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)
- [MediaPipe Text Embedder（Android）](https://developers.google.com/edge/mediapipe/solutions/text/text_embedder/android)
- [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)
- [V2EX API](https://www.v2ex.com/go/v2exapi)
- [知乎开放平台](https://developer.zhihu.com/docs)

## 开源协议

[Apache License 2.0](LICENSE)

## 开发与发布流程

参见[发布指南](spec/en/releasing.md)，了解 CI 验证、自动更新日志、版本 PR、签名配置和发布重试。

- [AppFunction 执行会话指南](spec/zh/appfunctions.md)：通过 ADB 以临时 Shell 身份运行正式版或调试版，测试跨应用 AppFunction。
- [A2A 示例服务指南](a2a-server/README.md)：启动本地 Agent，进行客户端端到端联调。
- [网易云音乐插件说明](spec/zh/netease-plugin.md)：查看接入细节和平台能力边界。
