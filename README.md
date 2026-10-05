<div align="center">

<img src="doc/icon.webp" width="120" alt="Gimi" />

# Gimi

**An Android AI assistant with conversations, attachments, and settings stored on the device.**

Supports text and voice interaction, phone tools, background app control, and extensions through official tools, APK plugins, MCP, and skills.

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

---

## Overview

With your own API key, use chat to check your calendar, set alarms, play media, adjust brightness,
search and read authorized files, and call official provider tools, plugins, and remote MCP tools.
After Shizuku authorization, models with image understanding can also inspect and operate other apps
in the background.

## Feature reference

### Chat

Stream replies. Attach photos from camera or gallery, add documents or audio supported by the model,
or receive images shared from other apps. View images and local file search results in the
conversation. Thinking, tool calls, and results are grouped by turn and can be expanded for details.

Configure each conversation independently: its model, MCP connections, official tools, and reasoning
effort. Tool loading mode (load on demand or load everything each turn) is a global switch in
*Settings → Tool access*, with load-all as the default on a fresh install. The permission mode
(request approval or full approval) is global too, and can be toggled from the *Add to chat* panel
in the composer. When it needs more
information, the Agent can ask for typed input or offer choices right in the composer.

If the latest turn fails, is interrupted, or is stopped, edit the original message or retry it. If that
turn already called a tool, Gimi warns that resending may run the operation again; completed actions are
not undone.

Tasks can continue when the app moves to the background. With notifications allowed, Gimi can notify
you about tool execution, requests for approval or input, and task completion. Return to the
conversation to see the response and task status.

In an empty conversation, Agent-generated task suggestions can use enabled tools, plugins, and the
context you allow. Tap one to start; turn them off, refresh them now, or set their background update
interval in Settings.

### Voice

Tap the microphone to record, then stop to transcribe speech into the composer. Edit the text before
sending. Configure speech recognition and synthesis separately in *Settings → Default models*.
MiniMax speech recognition is supported; choose a voice for reply playback, with online voice-list
retrieval for MiniMax.

Automatic reading of complete replies is optional and defaults to off on a fresh install. Both voice
input and reply playback require the corresponding online model to be configured.

### Built-in tools

| Tool         | What it does                                          |
|--------------|-------------------------------------------------------|
| **Time**     | Alarms, timers, clock                                 |
| **Calendar** | View and create events                                |
| **Media**    | Play, pause, skip — works across apps                 |
| **Audio**    | Read and adjust media volume                          |
| **Display**  | Brightness, auto-brightness, screen timeout           |
| **Location** | Get location, open in maps                            |
| **Files**    | Search photos, videos, audio, and authorized documents; read accessible local files |
| **Apps**     | List, search, open apps; take photo/video             |
| **Contact**  | Dial, message, lookup                                 |
| **Web**      | Web search, open links                                |
| **Settings** | Jump to system settings pages                         |

Enable *Custom tools* in *Settings → Tool access* to choose individual local tools. When it is off,
all local tools are available, subject to system permissions and the permission mode.

### Background app control

Let the Agent inspect and operate other apps in the background while you continue using your phone.
Supported actions include opening apps, observing screens, clicking elements, filling text fields,
coordinate taps, swipes, Back, and waits. The screen is observed again after actions, and stale
targets are rejected.

Install Shizuku, start its service in ADB shell mode through wireless debugging or ADB, then authorize
Gimi in *Settings → Background app control*. Shizuku root mode is currently unsupported. The chat
model must support image understanding. Relevant tools are provided automatically while Shizuku is
running and do not need to be enabled individually in the local tool list.

Text input and native element actions also require the *Gimi Background App Control · Text Input*
Accessibility service. The device and target app must support independent background operation.
Only one background app-control task can run at a time; the settings page shows status and permissions.

### Official tools

Configure and enable the provider and its official tools in *Settings → API access*, then choose
capabilities in the conversation's *Official tools* panel. Availability depends on the provider,
API protocol, model, and credentials.

| Provider | Capabilities |
|----------|--------------|
| **OpenAI** | Web search |
| **Anthropic** | Web search |
| **MiniMax** | Web search, text-to-image, reference-image generation |
| **MiMo (Xiaomi)** | Web search |
| **GLM (Zhipu)** | Web search, webpage reading |
| **Kimi** | Kimi tool collection (formulas) |

MiniMax image generation can be used with chat models from other providers, using the MiniMax
service's credentials. Native capabilities such as web search remain subject to provider and
protocol restrictions.

### MCP

Connect remote MCP servers (SSE or Streamable HTTP) to give the assistant any tools you want. Add a
server manually, or paste an `mcpServers` JSON or a curl snippet straight from the docs — Gimi parses
it for you. Set a bearer token or custom headers, test the connection, and inspect the tools,
resources, and prompts each server exposes. Disable any server anytime.

In *Settings → MCP*, tap **Import** and paste the `mcpServers` JSON or curl snippet from the server
documentation.

### Plugins

Install APK plugins to add capabilities. Refresh the list to apply them immediately — no restart
needed. Plugins bring their own tools and can run an in-app authorization flow.

The project provides these plugin APKs as separate GitHub Release downloads; install them separately:

- **Spotify**: search, playback, playlists, and your library
- **知乎 Zhihu**: search, hot lists, and Q&A
- **V2EX**: notifications, node/topic/reply browsing, and account information (requires a Personal Access Token)
- **微博 Weibo**: trending searches, AI search summaries, posts from the authorized account, and Super Topic browsing, posting, comments, and replies (requires App ID and App Secret)
- **小红书 Xiaohongshu**: login, feeds and search, profiles and notes, comments and interactions, notifications, and image/video publishing

**About Xiaohongshu**: The plugin operates the website directly with an on-device WebView, without
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

### Folders and workspace

Authorize local document folders in *Settings → Folders* and revoke access anytime. Searching shared
photos, videos, and audio uses the corresponding system permissions.

Chat attachments are saved in a local workspace. View, open, or delete multiple files in
*Settings → Workspace*. Deleting a conversation does not automatically delete its attachments;
users manage those files themselves.

### App updates

The app checks GitHub for updates in the background on a cold start and shows an indicator on the
Settings entry when a new version is available. Use *Settings → About → Check for updates* to check
manually, review release notes, and download and install an update.

### Authorization and data

- Sensitive actions pause and wait for approval or rejection.
- The permission mode — request approval or full approval — is global; full approval automatically
  allows tool calls that require confirmation.
- Local tools can be restricted individually through *Custom tools*; system permissions are granted as needed.
- The Permissions page describes the purpose of each permission.
- API keys are stored on the device and used only to call the corresponding configured services.

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

Configure the default chat model, a quick model for conversation titles and task suggestions, speech
recognition, speech synthesis, and a playback voice separately. Attachment, reasoning, and tool-call
capabilities depend on the selected model.

## Getting started

1. Install the APK matching your device. Requires Android 14+.
2. *Settings → API access*: choose a provider, enter the key and endpoint, test the connection, and enable the service.
3. *Settings → Default models*: choose chat and quick models. For voice, configure recognition, synthesis, and a playback voice separately.
4. *Settings → Tool access*: optionally enable *Custom tools* and choose local tools, or change the tool loading mode.
5. *Settings → Permissions*: grant system permissions as needed; allow notifications for background task alerts.
6. Optional: configure on-device or Mem0 memory in *Settings → Memory*, and manage empty-chat task
   suggestions in *Settings → Smart recommendations*.
7. Start chatting.

Optional extensions include MCP servers, plugins, skills, official tools, authorized folders, and
Shizuku background app control.

## Privacy

Gimi collects nothing: no analytics, no telemetry, no accounts, no developer servers. Conversations
stay on your device; network requests serve configured or enabled model, speech, MCP, plugin, and
Mem0 services, skill downloads, and GitHub updates. See [PRIVACY.md](PRIVACY.md) ·
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

## Development and release workflow

[See the release guide](docs/releasing.md) for CI checks, automated changelogs, version PRs, signing setup, and release retries.
