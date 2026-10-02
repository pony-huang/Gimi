<div align="center">

<img src="doc/icon.webp" width="120" alt="Gimi" />

# Gimi

**Android AI 助手，对话、附件和配置保存在设备本地。**

支持文本与语音交互、手机工具调用、后台静默操作，以及官方工具、APK 插件、MCP 和技能扩展。

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

---

## 功能概述

配置自己的 API Key 后，可在对话中查询日程、设置闹钟、播放媒体、调节亮度、搜索和读取已授权文件，也可调用模型服务的官方工具、插件和远程 MCP 工具。完成 Shizuku 授权后，支持图片理解的模型还可在后台查看和操作其他应用。

## 功能说明

### 聊天

流式回复，支持拍照、相册发图，以及按模型能力添加文档或音频附件，也可接收其他应用分享的图片。图片和本地文件搜索结果可直接在对话中查看；思考内容、工具调用和返回结果按轮次分组展示，可展开查看。

每个会话可单独选择模型、MCP 连接、官方工具和推理强度。工具加载方式（按需加载或全量加载）是全局设置，位于“设置 → 工具授权”页，新安装默认全量加载；权限模式（“请求批准”/“完全批准”）同样为全局开关，可在输入栏的“添加到聊天”面板里切换。Agent 需要补充信息时，会在输入栏中请求文字回复或让你从选项中选择。

最新一轮因失败、中断或手动停止而未完成时，可编辑原消息或重试；若这一轮已调用工具，重新发送前会提醒你操作可能再次执行，已完成的操作不会撤销。

任务可在应用切到后台后继续运行。允许通知后，工具执行、需要批准或补充输入，以及任务完成时可收到通知；返回会话可继续查看回复和任务状态。

空会话可展示由 Agent 根据已启用工具、插件和你允许提供的上下文生成的任务建议，点一下即可开始；可在设置中关闭、立即刷新或调整后台更新间隔。

### 语音

点按麦克风录音，停止后识别为输入栏中的文字，可编辑后发送。语音识别和语音合成模型分别在“设置 → 默认模型”配置；支持 MiniMax 语音识别，并可选择回复朗读的音色，MiniMax 音色列表支持在线获取。

可开启自动朗读完整回复，新安装默认关闭。语音输入与回复朗读均需要配置对应的在线模型。

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

在“设置 → 工具授权”中开启“自定义工具”后，可逐项选择允许使用的本地工具；关闭时全部本地工具可用，具体操作仍受系统权限和权限模式约束。

### 后台静默操作

让 Agent 在后台查看和操作其他应用，不打断当前手机上的操作。支持打开应用、查看界面、按界面元素点击或填写文字、坐标点击、滑动、返回和等待；操作后会重新观察界面，过期的界面目标会被拒绝。

使用前需安装 Shizuku，以无线调试或 ADB 启动 ADB shell 模式服务，并在“设置 → 后台静默操作”授权 Gimi。当前不支持 Shizuku root 模式；所选对话模型需支持图片理解。Shizuku 运行时会自动提供相关工具，无需在本地工具列表中逐项开启。

文字输入和原生界面元素操作还需开启“Gimi 后台静默操作 · 文字输入”无障碍服务。设备和目标应用需支持后台独立运行，同一时间只支持一个后台操作任务；设置页可查看运行状态和授权情况。

### 官方工具

在“设置 → API 接入”中配置并启用对应服务及其官方工具，再在会话的“官方工具”面板选择使用的能力。可用工具取决于服务、接口协议、模型和凭据配置。

| 服务 | 能力 |
|------|------|
| **OpenAI** | 联网搜索 |
| **Anthropic** | 联网搜索 |
| **MiniMax** | 联网搜索、文生图、参考图生成 |
| **小米 MiMo** | 联网搜索 |
| **智谱 GLM** | 联网搜索、网页读取 |
| **Kimi** | Kimi 工具集（formulas） |

MiniMax 图像生成可与其他服务的对话模型混用，使用 MiniMax 服务的凭据；原生联网搜索等能力仍受对应服务和协议限制。

### MCP

连接远程 MCP 服务器（SSE 或 Streamable HTTP），想加什么工具都行。手动新建，或者把文档里的 `mcpServers` JSON、甚至一条 curl 命令直接粘进来，Gimi 会帮你解析。可设置 Bearer Token 和自定义请求头，测试连接，并查看每个服务器暴露的工具、资源和提示词；可随时停用。

#### 推荐服务器

| 服务器          | 能带来什么                                 | 文档                                                        | 接入方式                                               |
|-----------------|--------------------------------------------|-------------------------------------------------------------|--------------------------------------------------------|
| **高德 AMap**   | 地图：地理编码、路径规划、周边搜索、天气      | [lbs.amap.com/api/mcp-server/summary](https://lbs.amap.com/api/mcp-server/summary) | `https://mcp.amap.com/mcp?key=你的Key`（Streamable HTTP） |

在 *设置 → MCP* 里点「导入」，把上面文档中的 `mcpServers` JSON 或 curl 片段粘进去即可。

### 插件

安装 APK 插件即可扩展能力；刷新列表后立即生效，无需重启。插件自带工具，也支持应用内授权流程。

项目提供以下插件 APK，随 GitHub Release 单独发布，需另行安装：

- **Spotify**：搜索、播放、歌单和音乐库
- **知乎**：搜索、热榜和问答
- **V2EX**：提醒、节点/主题/回复浏览和账号信息（需 Personal Access Token）
- **微博**：热搜、智搜摘要、授权账号微博，以及超话浏览、发帖、评论与回复（需配置 App ID 和 App Secret）
- **小红书**：登录、浏览推荐与搜索、查看主页和笔记、评论与互动、通知，以及图文和视频发布

**小红书说明**：插件直接通过设备上的 WebView 操作网页，不需要 MCP 服务器或中转地址，但目前稳定性有限。建议优先使用 MCP 服务器 [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)。

第三方也可以通过公开的插件 API 自行开发。

### 记忆

默认使用设备本地记忆，在后续对话中保存和召回相关信息；也可在 *设置 → 记忆* 启用 Mem0 长期记忆。关闭记忆后不再保存或召回对话记忆；Mem0 Token 安全保存在设备上。

### 技能

从链接或本地 ZIP 安装技能包。技能就是一份指引和资源，装好后需要用的时候助手会自动调用。(不支持脚本执行)

### 文件夹与工作区

“设置 → 文件夹”用于授权助手搜索本机文档目录，可随时撤销；共享图片、视频和音频的搜索使用对应系统权限。

聊天中发送的附件统一保存在设备本地工作区，可在“设置 → 工作区”查看、打开和批量删除。删除会话不会自动删除这些附件，文件由用户自行管理。

### 应用更新

应用冷启动时会在后台检查 GitHub 更新，发现新版本后显示设置入口提示。也可在“设置 → 关于 → 检查更新”手动检查，查看更新内容并下载安装。

### 授权与数据

- 需要确认的敏感操作会暂停，等待允许或拒绝。
- 权限模式（“请求批准”/“完全批准”）为全局设置；完全批准会自动放行需要确认的工具调用。
- 本地工具可通过“自定义工具”逐项限制，系统权限由用户按需授权。
- 权限管理页面说明每项权限的用途。
- API Key 保存在设备本地，仅用于调用对应的已配置服务。

## 模型服务

自带 API Key。Gimi 内置以下服务商的预设，也兼容任何 OpenAI API 或 Anthropic API 端点。

| 服务商                                                                                              | 接口协议                |
|-----------------------------------------------------------------------------------------------------|-------------------------|
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** | OpenAI API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** | Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Moonshot" /> **月之暗面 Kimi** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **智谱 GLM** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **小米 MiMo** | OpenAI API / Anthropic API |

可分别配置默认对话模型、快速模型（会话标题与任务推荐）、语音识别模型、语音合成模型及播放音色。附件、推理和工具调用能力以所选模型的支持范围为准。

## 快速上手

1. 安装适配设备的 APK。需要 Android 14+。
2. *设置 → API 接入*：选择服务商，填写 Key 和服务地址，检测连接并启用服务。
3. *设置 → 默认模型*：选择对话模型和快速模型；需要语音时，分别配置语音识别、语音合成模型和播放音色。
4. *设置 → 工具授权*：按需开启“自定义工具”并选择本地工具，也可调整工具加载方式。
5. *设置 → 权限管理*：按需授予系统权限；后台任务通知需允许通知权限。
6. 可选：在 *设置 → 记忆* 配置本地或 Mem0 记忆，在 *设置 → 智能推荐* 管理空会话任务建议。
7. 开始对话。

可选扩展：添加 MCP 服务器、安装插件或技能、启用官方工具、授权文件夹，以及配置 Shizuku 后台静默操作。

## 隐私

Gimi 不收集任何数据：无统计埋点、无遥测、无账号，开发者也没有服务器。对话保存在你的设备上，
网络请求用于已配置或启用的模型、语音、MCP、插件、Mem0 等服务，以及技能下载和 GitHub 更新。
详见[隐私政策](PRIVACY.zh-CN.md) · [Privacy Policy](PRIVACY.md)。

## 致谢

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)
- [MediaPipe Text Embedder（Android）](https://developers.google.com/edge/mediapipe/solutions/text/text_embedder/android)
- [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)
- [V2EX API](https://www.v2ex.com/go/v2exapi)
- [知乎开放平台](https://developer.zhihu.com/docs)
- [高德 MCP 服务](https://lbs.amap.com/api/mcp-server/summary)

## 开源协议

[Apache License 2.0](LICENSE)
