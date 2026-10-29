<div align="center">

<img src="doc/icon.webp" width="192" alt="Gimi" />

# Gimi

**An Android AI assistant with conversations, attachments, and settings stored on the device.**

Supports text and voice interaction, native agent-generated cards, phone tools, background app
control, and extensions through AppFunctions, official tools, APK plugins, MCP, remote A2A agents,
skills, and a cloud sandbox.

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

---

## Overview

With your own API key, use chat to check your calendar, set alarms, play media, adjust brightness,
search and read accessible files, and call official provider tools, plugins, remote MCP servers,
remote A2A agents, and enabled AppFunctions from other Android apps. After Shizuku authorization,
models with image understanding can also inspect and operate other apps in the background. With an
E2B API key, it can also run shell commands and code in an isolated cloud sandbox.

## Feature reference

### Chat

Stream replies. Attach photos from camera or gallery, add documents or audio supported by the model,
or receive images shared from other apps. View images and local file search results in the
conversation. Thinking, tool calls, and results are grouped by turn and can be expanded for details.

Agent replies can also contain native A2UI surfaces such as option decks, a music player, simplified
weather cards, tables, panels, links, and media controls. These cards are generated from validated
catalog data, update in place as a response streams, and send interactions back to the Agent.

Configure each conversation independently: its model, MCP connections, official tools, and reasoning
effort. Tool loading mode (load on demand or load everything each turn) is a global switch in
*Settings → Tool access*, with load-all as the default on a fresh install. The permission mode
(request approval or full approval) is global too, and can be toggled from the *Add to chat* panel
in the composer. When it needs more
information, the Agent can ask for typed input or offer choices right in the composer. The composer
shows the current model; tap it to switch models from a bottom sheet listing every enabled service.

Tasks can continue when the app moves to the background. With notifications allowed, Gimi can notify
you about tool execution, requests for approval or input, and task completion. Return to the
conversation to see the response and task status.

In an empty conversation, Agent-generated task suggestions can use enabled tools, plugins, and the
context you allow. Tap one to start; turn them off, refresh them now, or set their background update
interval in Settings.

### Voice

Tap the microphone to record. Stop to transcribe into the composer and edit the text before sending,
or use the send button to transcribe and send in one step. Configure speech recognition and synthesis
separately in *Settings → Default models*.
Speech recognition supports MiniMax and MiMo (Xiaomi); choose a voice for reply playback, with online voice-list
retrieval for MiniMax.

Complete replies can be read aloud automatically.

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

Enable *Custom tools* in *Settings → Tool access* to choose individual local tools; when it is off,
all local tools are available.

### AppFunctions

On supported Android 16+ devices, Gimi can discover capabilities declared by other installed apps.
Enable individual apps in *Settings → AppFunciton* to make their AppFunctions available to the
Agent. The settings page shows each app's functions, parameters, and current system state.

Cross-app execution requires the privileged `EXECUTE_APP_FUNCTIONS` permission, and the target app
may still restrict which functions Gimi can call. Many devices do not expose a normal settings
switch for this permission. For local testing on a supported device, use the
[AppFunction execution session guide](spec/en/appfunctions.md) to temporarily launch Gimi through
ADB with the Shell identity.

### Background app control

Let the Agent inspect and operate other apps in the background while you continue using your phone.
Supported actions include opening apps, observing screens, clicking elements, filling text fields,
coordinate taps, swipes, Back, and waits. The screen is observed again after each action.

Install Shizuku, start its service in ADB shell mode through wireless debugging or ADB, then turn on
background app control and authorize Gimi in *Settings → Shizuku*. The chat model must support image
understanding. Relevant tools are provided automatically while Shizuku is running and do not need to
be enabled individually in the local tool list.

Text input and native element actions also require the *Gimi* Accessibility service. The settings page
shows status and permissions.

The background app is shown in a floating small window and needs the display-over-other-apps
permission from *Settings → Permissions*.

### Official tools

Configure and enable the provider and its official tools in *Settings → API access*, then choose
capabilities in the conversation's *Official tools* panel.

| Provider | Capabilities |
|----------|--------------|
| **Anthropic** | Web search |
| **MiniMax** | Web search, text-to-image, reference-image generation |
| **GLM (Zhipu)** | Web search, webpage reading |
| **Kimi** | Kimi Search Basic, Search Pro, and web fetch |

Provider-executed web search (Anthropic, MiniMax) runs on the provider's side and is offered under
the service's Anthropic protocol. MiniMax image generation can be used with chat models from other
providers, using the MiniMax service's credentials.

### MCP

Connect remote MCP servers over Streamable HTTP to give the assistant any tools you want. Add a
server manually, or paste an `mcpServers` JSON or a curl snippet straight from the docs — Gimi parses
it for you. Set a bearer token or custom headers, test the connection, and inspect the tools and
resources each server exposes. Disable any server anytime.

In *Settings → MCP*, tap **Import** and paste the `mcpServers` JSON or curl snippet from the server
documentation.

### Remote agents (A2A)

Connect agents that implement the A2A protocol in *Settings → A2A*. Add the Agent URL, an optional
Bearer token, and enable the agent; Gimi tests the connection and reads its Agent Card to show the
declared skills, input/output formats, and streaming support. Enabled agents become delegation
targets during chat, so the main assistant can hand a suitable task to a remote agent and use its
result in the conversation.

The Android client uses A2A JSON-RPC over HTTP. A small local sample server and device setup notes
are available in the [A2A server guide](a2a-server/README.md).

### Cloud sandbox

Run shell commands and code in an isolated cloud environment. Enable E2B in *Settings → Sandbox* and
enter the domain and API key; the key, the environment variables, and the rest of the configuration
are encrypted on this device. The template defaults to `code-interpreter-v1` — a custom template must
provide a Code Interpreter to run code — and the idle timeout defaults to 300 seconds.

Every conversation gets its own environment, so files, installed dependencies, and interpreter
variables carry over between turns of that conversation and stay isolated from other conversations.
Commands and code calls accept optional environment variables and a timeout; commands also accept a
working directory. The Agent can run Python or JavaScript in a persistent kernel, start a background
command and get back its process ID and, when a port is given, a preview URL, and read, write, or
list text files up to 1 MiB. Environments are created on first use and destroyed after the idle
timeout or when the configuration changes or is disabled.

See the [E2B documentation](https://e2b.dev/docs).

### Plugins

Install APK plugins to add capabilities. Refresh the list to apply them immediately — no restart
needed. Plugins bring their own tools and can run an in-app authorization flow.

The project provides these plugin APKs as separate GitHub Release downloads. You can also open
*Settings → Plugins* to browse the plugin subscription and install them in the app. The default
subscription is the release-generated `plugin-subscription.json` attached to the latest stable GitHub Release; change it from the
subscription icon on the plugin page. Installation downloads the matching APK, verifies its SHA-256,
and hands it to the system installer. Custom subscription sources are trusted at your own risk.

The default subscription contains:

- **Spotify**: search, playback, playlists, and your library
- **知乎 Zhihu**: search, hot lists, and Q&A
- **V2EX**: notifications, node/topic/reply browsing, and account information (requires a Personal Access Token)
- **微博 Weibo**: trending searches, AI search summaries, posts from the authorized account, and Super Topic browsing, posting, comments, and replies (requires App ID and App Secret)
- **小红书 Xiaohongshu**: login, feeds and search, profiles and notes, comments and interactions, notifications, and image/video publishing
- **网易云音乐 NetEase Cloud Music**: anonymous or QR sign-in, music and lyric search, profiles, playlists, song details, authorized audio URLs, playlist creation, adding/removing songs, and liked-song actions (requires App ID, an RSA PKCS#8 private key, and endpoint permissions; playback control, cloud uploads, and note publishing are unavailable)

**About Xiaohongshu**: The plugin operates the website directly with an on-device WebView, without an
MCP server or relay URL.

flow, supported tools, and platform limitations.

Third parties can build their own with the public plugin API.

### Memory

On-device memory is used by default to save and recall relevant information in later conversations.
You can enable Mem0 long-term memory in *Settings → Memory*; the Mem0 token is stored securely on the
device.

### Skills

Install skill packs from a URL or a local ZIP in *Settings → Skills*. A skill bundles instructions
and supporting resources. With the cloud sandbox configured, the Agent can run a skill's scripts
inside that conversation's sandbox and can also prepare and install a skill archive from the sandbox
itself.

### Files and workspace

Gimi searches file names across all accessible mounted shared storage after all-files access is
granted in Android settings, including files that are not indexed by MediaStore. Private app
directories are excluded. The permission is requested when a file search needs it; if it is
declined, Gimi does not repeatedly prompt. Shared photo, video, and audio search also honors the
corresponding media permissions.

Chat attachments are saved in a local workspace. View, open, or delete multiple files in *Settings
→ Cache*.

### Authorization and data

- Sensitive actions pause and wait for approval or rejection.
- The permission mode — request approval or full approval — is global; full approval automatically
  allows tool calls that require confirmation.
- Local tools can be restricted individually through *Custom tools*; system permissions are granted as needed.
- All-files access controls document search across shared storage and can be revoked in Android
  settings at any time.
- The Permissions page describes the purpose of each permission.
- API keys, remote-agent tokens, and other credentials are stored on the device and used only to
  call the corresponding configured services or endpoints.

## Model

Bring your own API key. Gimi ships with presets for the providers below, and works with any
Chat Completions or Anthropic API endpoint.

| Provider                                                                                             | API protocol            |
|------------------------------------------------------------------------------------------------------|-------------------------|
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** | Chat Completions |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** | Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Moonshot" /> **Moonshot (Kimi)** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **GLM (Zhipu)** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** | Chat Completions / Anthropic API |
| <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **MiMo (Xiaomi)** | Chat Completions / Anthropic API |

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

Optional extensions include MCP servers, remote A2A agents, AppFunctions, plugins, skills, official
tools, all-files access, the E2B cloud sandbox, and Shizuku background app control.

## Privacy

Gimi collects nothing: no analytics, no telemetry, no accounts, no developer servers. Conversations
stay on your device; network requests serve configured or enabled model, speech, MCP, remote A2A,
plugin, and Mem0 services, skill downloads, the E2B sandbox, and GitHub updates. See
[PRIVACY.md](PRIVACY.md) · [隐私政策](PRIVACY.zh-CN.md).

## Contributing

Bug reports, feature suggestions, documentation improvements, translations, and code contributions
are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the contribution workflow and development guidelines.

The project's features and interface design are primarily shaped by the author's own usage habits,
with inspiration from established products and their interaction patterns. When proposing a change,
please describe the use case it addresses; discuss substantial feature or interface changes in an issue first.

## Thanks

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)
- [MediaPipe Text Embedder for Android](https://developers.google.com/edge/mediapipe/solutions/text/text_embedder/android)
- [xpzouying/xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)
- [V2EX API](https://www.v2ex.com/go/v2exapi)
- [Zhihu Open Platform](https://developer.zhihu.com/docs)

## License

[Apache License 2.0](LICENSE)

## Development and release workflow

[See the release guide](spec/en/releasing.md) for CI checks, automated changelogs, version PRs,
signing setup, and release retries.

- [AppFunction execution session guide](spec/en/appfunctions.md): run release or debug builds with
  the temporary Shell identity required for cross-app AppFunctions.
- [A2A sample server](a2a-server/README.md): run a local Agent for end-to-end client testing.
- [NetEase plugin notes](spec/zh/netease-plugin.md): integration details and platform limits
  (Chinese).
