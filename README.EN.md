<div align="center">

<img src="doc/icon.webp" width="120" alt="Gimi" />

# Gimi

**An AI assistant that travels with you.**

Chat or talk naturally, then put your phone's alarms, calendar, files, music, plugins, and MCP tools to work.

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[简体中文](README.md) · [English](README.EN.md)

</div>

---

## Screenshots

<p align="center">
  <img src="doc/assert/8a1200932b48e2d3e7a5c85614152dfd.jpg" width="24%" alt="Task suggestions" />
  <img src="doc/assert/973ec8f0697877bd9042082aba464546.jpg" width="24%" alt="Local file search in chat" />
  <img src="doc/assert/0cc4c99f5dc1bd08c1531a932775cc86.jpg" width="24%" alt="Plugin management" />
  <img src="doc/assert/dad67e7e0bd1e7045ec58d4282ce2627.jpg" width="24%" alt="Settings" />
</p>

<p align="center">
  <video src="https://github.com/pony-huang/Gimi/raw/refs/heads/main/doc/assert/Demo1.mp4" controls playsinline width="320">
    <a href="doc/assert/Demo1.mp4">Watch the demo video</a>
  </video>
</p>

## What it does

You bring your own API key. Gimi gives its model access to your device within the permissions you
choose: alarms, calendar, media, screen brightness, folders you pick, plugins, and external tools.
No vendor lock-in. No subscription.

## Features

### Chat

Stream replies. Attach photos from camera or gallery, or share images and documents from other apps.
Images can be previewed directly in chat and local file search results render in the conversation.
Choose whether to show each tool call and its result.

In an empty conversation, Agent-generated task suggestions can use enabled tools, plugins, and the
context you allow. Tap one to start; turn them off, refresh them now, or set their background update
interval in Settings.

### Voice

Tap-to-talk, or set a wake word for hands-free use. Paired with a Bluetooth headset, Gimi listens in
the background with an offline wake-word model — say "Gimi" and speak a task, and it runs without
touching the screen.

### Built-in tools

| Tool         | What it does                                          |
|--------------|-------------------------------------------------------|
| **Time**     | Alarms, timers, clock                                 |
| **Calendar** | View and create events                                |
| **Media**    | Play, pause, skip — works across apps                 |
| **Audio**    | Read and adjust media volume                          |
| **Display**  | Brightness, auto-brightness, screen timeout           |
| **Location** | Get location, open in maps                            |
| **Files**    | Search photos, videos, audio, documents you've shared |
| **Apps**     | List, search, open apps; take photo/video             |
| **Contact**  | Dial, message, lookup                                 |
| **Web**      | Web search, open links                                |
| **Settings** | Jump to system settings pages                         |

### MCP

Connect remote MCP servers (SSE or Streamable HTTP) to give the assistant any tools you want. Add a
server manually, or paste an `mcpServers` JSON or a curl snippet straight from the docs — Gimi parses
it for you. Set a bearer token or custom headers, test the connection, and inspect the tools,
resources, and prompts each server exposes. Disable any server anytime.

#### Recommended servers

| Server          | What it gives you                                   | Docs                                                        | Connect                                              |
|-----------------|-----------------------------------------------------|-------------------------------------------------------------|------------------------------------------------------|
| **AMap (高德)** | Maps: geocoding, route planning, POI search, weather | [lbs.amap.com/api/mcp-server/summary](https://lbs.amap.com/api/mcp-server/summary) | `https://mcp.amap.com/mcp?key=YOUR_KEY` (Streamable HTTP) |

In *Settings → MCP*, tap **Import** and paste the `mcpServers` JSON or curl snippet from the docs
above.

### Plugins

Install APK plugins for extra capabilities, then refresh the list — no restart needed. Plugins bring
their own tools and can run an in-app authorization flow. Bundled plugins include **Spotify**
(search, playback, playlists, your library), **知乎 Zhihu** (search, hot lists, Q&A), **V2EX**
(notifications, node/topic/reply browsing, own account — via Personal Access Token), and
**小红书 Xiaohongshu** (login, feeds, search, profiles,
comments, notifications, interactions, and
image/video publishing). The Xiaohongshu plugin operates the website directly with an on-device
WebView and does not require an MCP server or relay URL, but it is currently less stable. Prefer the
[xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp) MCP server. Third parties
can build their own with the public plugin API.

### Memory

On-device memory is used by default to save and recall relevant information in later conversations.
You can enable Mem0 long-term memory in *Settings → Memory*. Turning memory off stops both saving
and recall; the Mem0 token is stored securely on the device.

### Skills

Install instruction packs from a URL or local ZIP. A skill bundles guidance and resources the
assistant can use when needed. (No script execution)

### Working files

Pick folders for the assistant to search. Only folders you authorize are visible. Revoke anytime.

### App updates

Check for new versions and install in place from *Settings → Check for updates*. Releases ship from
GitHub.

### You're in control

- Sensitive actions pause and wait for Allow/Reject.
- Enable only the tools you want.
- Permissions page explains what each one does.
- API keys stay on-device; never pass through third parties.

## Model services

Bring your own API key. Gimi ships with presets for the providers below, and works with any
OpenAI-compatible or Anthropic-style endpoint.

| Provider                                                                                             | API protocol            |
|------------------------------------------------------------------------------------------------------|-------------------------|
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** | OpenAI-compatible |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** | Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** | OpenAI & Anthropic |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Moonshot" /> **Moonshot (Kimi)** | OpenAI & Anthropic |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **GLM (Zhipu)** | OpenAI & Anthropic |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** | OpenAI & Anthropic |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **MiMo (Xiaomi)** | OpenAI & Anthropic |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/gemini-color.svg" width="16" alt="Gemini" /> **Gemini** | Native Gemini API |

Also configurable: a quick model for conversation titles, a speech recognition model, and a speech
synthesis model.

## Getting started

1. Install the APK. Requires Android 14+.
2. *Settings → Model services*: pick a provider, paste your key, tap **Test**.
3. *Settings → Default models*: choose your chat model. Add voice models if needed.
4. *Settings → Tools*: enable the local tools you're comfortable with. All optional.
5. *Settings → Permissions*: grant what you need. All optional.
6. Optional: configure on-device or Mem0 memory in *Settings → Memory*, and manage empty-chat task
   suggestions in *Settings → Smart recommendations*.
7. Start chatting.

Optional: enable voice wake, add MCP servers, install plugins or skills, authorize working folders.

## Privacy

Gimi collects nothing: no analytics, no telemetry, no accounts, no developer servers. Conversations
stay on your device; network traffic only goes to the services you configure (model providers,
speech, MCP servers) and to GitHub for app updates. See [PRIVACY.md](PRIVACY.md) ·
[隐私政策](PRIVACY.zh-CN.md).

## Thanks

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)
- [MediaPipe Text Embedder for Android](https://developers.google.com/edge/mediapipe/solutions/text/text_embedder/android)
- [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)
- [V2EX API](https://www.v2ex.com/go/v2exapi)
- [Zhihu Open Platform](https://developer.zhihu.com/docs)
- [AMap MCP service](https://lbs.amap.com/api/mcp-server/summary)

## License

[Apache License 2.0](LICENSE)
