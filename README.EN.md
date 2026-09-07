<div align="center">

<img src="doc/icon.webp" width="120" alt="Gimi" />

# Gimi

**A local BYOK AI assistant for Android, similar to general-purpose conversational assistants such as Gemini, ChatGPT, and Claude.**

Provides text chat, voice interaction, and access to alarms, calendar, files, media, plugins, and MCP tools.

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

## Demo

<div align="center">
  <video src="https://github.com/user-attachments/assets/855737f5-61e6-4e77-88f4-bfe5385009eb" controls playsinline width="320">
    <a href="https://github.com/user-attachments/assets/855737f5-61e6-4e77-88f4-bfe5385009eb">Watch the demo video</a>
  </video>
</div>

## Overview

Configure your own API key, and Gimi brings chat, voice, and everyday phone tasks into one place:
check your calendar, set alarms, play media, adjust brightness, search folders you authorize, and use
the plugins and external tools you connect.

## Feature reference

### Chat

Stream replies. Attach photos from camera or gallery, or share images and documents from other apps.
Images can be previewed directly in chat and local file search results render in the conversation.
Choose whether to show each tool call and its result, or automatically read complete replies aloud.

Configure each conversation independently: choose its model, MCP connections, official tools, reasoning
effort, and tool-loading mode (on demand or all at once); choose whether tool calls request approval or
receive full approval. When it needs more information, the Agent can ask for typed input or offer choices
right in the composer.

If the latest turn fails, is interrupted, or is stopped, edit the original message or retry it. If that
turn already called a tool, Gimi warns that resending may run the operation again; completed actions are
not undone.

In an empty conversation, Agent-generated task suggestions can use enabled tools, plugins, and the
context you allow. Tap one to start; turn them off, refresh them now, or set their background update
interval in Settings.

### Voice

Tap-to-talk, or set a wake word for hands-free use. Customize the wake word for an installed offline
wake model. Gimi prefers a connected Bluetooth headset and can also use the phone microphone and speaker:
say the wake word, then speak a task, and it runs without touching the screen.

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

Install APK plugins to add capabilities. Refresh the list to apply them immediately — no restart
needed. Plugins bring their own tools and can run an in-app authorization flow.

Bundled plugins:

- **Spotify**: search, playback, playlists, and your library
- **知乎 Zhihu**: search, hot lists, and Q&A
- **V2EX**: notifications, node/topic/reply browsing, and account information (requires a Personal Access Token)
- **小红书 Xiaohongshu**: login, feeds and search, profiles and notes, comments and interactions, notifications, and image/video publishing

**About Xiaohongshu:** The plugin operates the website directly with an on-device WebView, without
an MCP server or relay URL, but it is currently less stable. Prefer the
[xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp) MCP server.

Third parties can build their own with the public plugin API.

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

### Authorization and data

- Sensitive actions pause and wait for approval or rejection.
- Each conversation can use either request approval or full approval; the latter automatically allows tool
  calls that require confirmation.
- Local tools and system permissions are enabled or granted individually by the user.
- The Permissions page describes the purpose of each permission.
- API keys remain on the device and do not pass through third-party services.

## Model services

Bring your own API key. Gimi ships with presets for the providers below, and works with any
OpenAI API or Anthropic API endpoint.

| Provider                                                                                             | API protocol            |
|------------------------------------------------------------------------------------------------------|-------------------------|
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** | OpenAI API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** | Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Moonshot" /> **Moonshot (Kimi)** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **GLM (Zhipu)** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** | OpenAI API / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **MiMo (Xiaomi)** | OpenAI API / Anthropic API |

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

Optional extensions include voice wake, MCP servers, plugins or skills, and authorized working folders.

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
