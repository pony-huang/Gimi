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

你填自己的 API Key。Gimi 让模型能操作你的设备：闹钟、日历、媒体播放、屏幕亮度、你指定的文件夹，以及你接入的外部工具。

## 功能

### 聊天

流式回复，支持拍照或相册发图。

### 语音

点按说话，或者设唤醒词后台待命。

### 内置工具

|        |                  |
|--------|------------------|
| **时间** | 闹钟、倒计时、看时间       |
| **日历** | 查看和创建日程          |
| **媒体** | 播放、暂停、切歌（支持其他应用） |
| **音量** | 读取和调节媒体音量        |
| **显示** | 亮度、自动亮度、息屏时间     |
| **位置** | 获取位置、地图打开        |
| **文件** | 搜索图片、视频、音频和授权的文档 |
| **应用** | 查看、搜索、打开应用；拍照、录像 |
| **联系** | 拨号、发短信、查联系人      |
| **网页** | 联网搜索、打开链接        |
| **设置** | 跳转系统设置页          |

### MCP

连接远程 MCP 服务器（SSE 或 Streamable HTTP）扩展能力。

### 技能

从链接或本地 ZIP 安装技能包。技能就是一份指引和资源，装好后需要用的时候助手会自动调用。(不支持脚本执行)

### 工作文件

指定助手能搜的文件夹。只看得到你授权的目录，随时可撤销。

### 掌控权在你

- 敏感操作暂停等你允许或拒绝。
- 只开放你想用的工具。
- 权限页说清楚每项权限干什么用。
- API Key 存在本机，不经过第三方。

## 支持的模型服务

|            |               |              |               |
|------------|---------------|--------------|---------------|
| **OpenAI** | **Anthropic** | **DeepSeek** | **月之暗面 Kimi** |
| **智谱 GLM** | **MiniMax**   | **小米 MiMo**  |               |

支持 OpenAI 兼容和 Anthropic 协议。

可配置：快速模型（会话标题）、语音识别模型、语音合成模型。

## 快速上手

1. 装 APK。需要 Android 14+。
2. *设置 → 模型服务*：选服务商，贴 Key，点「检测」。
3. *设置 → 默认模型*：选对话模型。需要语音就一并配置。
4. *设置 → 权限*：看说明，愿意给的就给。全可选。
5. 开始聊。

想扩展的话：开语音唤醒、加 MCP 服务器、装技能、授权工作文件夹。

## 致谢

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)

## 开源协议

[Apache License 2.0](LICENSE)
