<div align="center">

<img src="doc/icon.webp" width="120" alt="Gimi" />

# Gimi

**你手机上的 AI 助手。**

聊天、语音、干活 —— 闹钟、文件、音乐、日程，一个应用搞定。

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

---

## 应用截图

<p align="center">
  <img src="doc/img1.jpg" width="24%" alt="聊天" />
  <img src="doc/img2.jpg" width="24%" alt="语音" />
  <img src="doc/img3.jpg" width="24%" alt="工具" />
  <img src="doc/img4.png" width="24%" alt="模型服务" />
</p>

## 能干什么

你填自己的 API Key。Gimi 让模型能操作你的设备：闹钟、日历、媒体播放、屏幕亮度、你指定的文件夹，以及你接入的外部工具。不锁定厂商，不收费。

## 功能

### 聊天

流式回复，支持拍照或相册发图，也能从其他应用分享图片和文档。本地文件搜索结果直接呈现在对话里，还能实时看到每一次工具调用。

### 语音

点按说话，或者设唤醒词后台待命。连接蓝牙耳机后，Gimi 用离线唤醒模型在后台监听 —— 说声「吉米」再报出任务，不动手机就能执行。

### 内置工具

| 工具    | 能力                   |
|--------|------------------------|
| **时间** | 闹钟、倒计时、看时间        |
| **日历** | 查看和创建日程            |
| **媒体** | 播放、暂停、切歌（支持其他应用） |
| **音量** | 读取和调节媒体音量          |
| **显示** | 亮度、自动亮度、息屏时间      |
| **位置** | 获取位置、地图打开          |
| **文件** | 搜索图片、视频、音频和授权的文档 |
| **应用** | 查看、搜索、打开应用；拍照、录像 |
| **联系** | 拨号、发短信、查联系人       |
| **网页** | 联网搜索、打开链接          |
| **设置** | 跳转系统设置页            |

### MCP

连接远程 MCP 服务器（SSE 或 Streamable HTTP），想加什么工具都行。手动新建，或者把文档里的 `mcpServers` JSON、甚至一条 curl 命令直接粘进来，Gimi 会帮你解析。可设置 Bearer Token 和自定义请求头，测试连接，并查看每个服务器暴露的工具、资源和提示词。随时可停用。

#### 推荐服务器

| 服务器          | 能带来什么                                 | 文档                                                        | 接入方式                                               |
|-----------------|--------------------------------------------|-------------------------------------------------------------|--------------------------------------------------------|
| **Mem0**        | 长期记忆 —— 跨会话记住你和你的偏好           | [docs.mem0.ai/platform/mem0-mcp](https://docs.mem0.ai/platform/mem0-mcp) | `https://mcp.mem0.ai/mcp`（Streamable HTTP，需 API Key） |
| **高德 AMap**   | 地图：地理编码、路径规划、周边搜索、天气      | [lbs.amap.com/api/mcp-server](https://lbs.amap.com/api/mcp-server)       | `https://mcp.amap.com/mcp?key=你的Key`（Streamable HTTP） |

在 *设置 → MCP* 里点「导入」，把上面文档中的 `mcpServers` JSON 或 curl 片段粘进去即可。

### 插件

安装 APK 插件扩展能力，刷新列表即生效，无需重启。插件自带工具，还支持应用内授权流程。内置插件有 **Spotify**（搜索、播放、歌单、你的音乐库）、**知乎**（搜索、热榜、问答）和 **V2EX**（提醒、节点/主题/回复浏览、账号信息，需 Personal Access Token）。第三方可以通过公开的插件 API 自己开发。

### 技能

从链接或本地 ZIP 安装技能包。技能就是一份指引和资源，装好后需要用的时候助手会自动调用。(不支持脚本执行)

### 工作文件

指定助手能搜的文件夹。只看得到你授权的目录，随时可撤销。

### 应用更新

*设置 → 检查更新*，有新版本直接应用内下载安装。版本发布自 GitHub。

### 掌控权在你

- 敏感操作暂停等你允许或拒绝。
- 只开放你想用的工具。
- 权限页说清楚每项权限干什么用。
- API Key 存在本机，不经过第三方。

## 模型服务

自带 API Key。Gimi 内置以下服务商的预设，也兼容任何 OpenAI 兼容或 Anthropic 协议的端点。

| 服务商                                                                                              | 接口协议                |
|-----------------------------------------------------------------------------------------------------|-------------------------|
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** | OpenAI 兼容 |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** | Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** | OpenAI 兼容 / Anthropic 协议 |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Moonshot" /> **月之暗面 Kimi** | OpenAI 兼容 / Anthropic 协议 |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **智谱 GLM** | OpenAI 兼容 / Anthropic 协议 |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** | OpenAI 兼容 / Anthropic 协议 |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **小米 MiMo** | OpenAI 兼容 / Anthropic 协议 |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/gemini-color.svg" width="16" alt="Gemini" /> **Gemini** | 原生 Gemini 接口 |

可配置：快速模型（会话标题）、语音识别模型、语音合成模型。

## 快速上手

1. 装 APK。需要 Android 14+。
2. *设置 → 模型服务*：选服务商，贴 Key，点「检测」。
3. *设置 → 默认模型*：选对话模型。需要语音就一并配置。
4. *设置 → 工具*：选择允许助手使用的本地工具。全可选。
5. *设置 → 权限管理*：看说明，愿意给的就给。全可选。
6. 开始聊。

想扩展的话：开语音唤醒、加 MCP 服务器、装插件或技能、授权工作文件夹。

## 隐私

Gimi 不收集任何数据：无统计埋点、无遥测、无账号，开发者也没有服务器。对话保存在你的设备上，
网络请求只发往你自行配置的服务（模型服务、语音、MCP 服务器）以及用于检查更新的 GitHub。
详见[隐私政策](PRIVACY.zh-CN.md) · [Privacy Policy](PRIVACY.md)。

## 致谢

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)
- [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)

## 开源协议

[Apache License 2.0](LICENSE)
