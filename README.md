<div align="center">

<img src="doc/icon.webp" width="192" alt="Gimi" />

# Gimi

**An Android AI assistant with your own API keys, phone tools, and extensible agents.**

[![CI](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml/badge.svg)](https://github.com/pony-huang/Gimi/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/pony-huang/Gimi)](https://github.com/pony-huang/Gimi/releases/latest)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

## Features

- **Chat and voice**: streaming replies, image/document/audio attachments where supported, speech input and reply playback, and native A2UI interactive cards.
- **Phone tools**: calendars, alarms, media, volume, brightness, location, contacts, apps, and local file search and reading.
- **Background tasks**: continue conversations in the background, with notifications for approvals, input requests, and completion.
- **Personalization**: per-conversation models and tools, task suggestions, on-device memory, and optional Mem0 memory.
- **Local workspace**: store and manage attachments on the device; export conversations as JSONL or ZIP.
- **Extensions**: official provider tools, MCP servers, remote A2A agents, APK plugins, skills, AppFunctions, and an E2B cloud sandbox.

## Getting started

Requires **Android 14+** and your own model API key.

1. Download a matching app APK from [Releases](https://github.com/pony-huang/Gimi/releases/latest).
2. In **Settings → API access**, configure a provider, test the connection, and enable it.
3. In **Settings → Default models**, select a chat model and a quick model for titles and suggestions. Configure speech models and a voice if needed.
4. Grant permissions as needed and start chatting. Manage suggestions, A2UI cards, and tool loading in **Settings → Personalized services**.

The global permission mode is available in the composer's **Add to chat** panel: *request approval* pauses sensitive tool calls for confirmation; *full approval* allows them automatically.

## Models and official tools

Presets: <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/openai.svg" width="16" alt="OpenAI" /> **OpenAI** · <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/anthropic.svg" width="16" alt="Anthropic" /> **Anthropic** · <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/deepseek-color.svg" width="16" alt="DeepSeek" /> **DeepSeek** · <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/kimi-color.svg" width="16" alt="Kimi" /> **Kimi** · <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/zhipu-color.svg" width="16" alt="GLM" /> **GLM** · <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/minimax-color.svg" width="16" alt="MiniMax" /> **MiniMax** · <img src="https://raw.githubusercontent.com/lobehub/lobe-icons/refs/heads/master/packages/static-svg/icons/xiaomimimo.svg" width="16" alt="MiMo" /> **MiMo**

Gimi supports Chat Completions and Anthropic-compatible endpoints. Attachment, reasoning, and tool capabilities depend on the selected model and protocol.

Enable official tools in **Settings → API access**, then select them in the conversation:

| Provider | Official tools |
|---|---|
| DeepSeek | Web search and fetch |
| Anthropic | Web search |
| MiniMax | Web search, text-to-image, reference-image generation |
| GLM | Web search and webpage reading |
| Kimi | Search Basic / Pro and web fetch |

**Why no Gemini on mobile?** Gimi does not currently support Gemini on phones. The [Google Gen AI Kotlin SDK](https://github.com/googleapis/kotlin-genai#readme) supports Android, but its security guidance discourages embedding API keys or Google Cloud credentials in public mobile clients and recommends [Firebase AI Logic with App Check](https://firebase.google.com/docs/ai-logic) or a secure backend. Gimi has not adopted either architecture.

## Extensions

Configure optional capabilities in Settings:

| Extension | Purpose and requirements |
|---|---|
| **MCP** | Remote tools over Streamable HTTP; import `mcpServers` JSON or curl snippets. Supports bearer tokens and custom headers. |
| **A2A** | Delegate tasks to remote agents using an Agent URL and optional bearer token. |
| **Plugins** | Install separate APKs from Releases or the in-app subscription catalog; downloads are SHA-256 verified. |
| **Skills** | Install instructions and resources from a URL or local ZIP; run scripts with the sandbox configured. |
| **Sandbox** | Configure an E2B API key for shell commands, Python/JavaScript, and files in an isolated environment per conversation. |
| **AppFunctions** | Discover other apps' functions on supported Android 16+ devices; execution requires privileged system access. See the [execution guide](spec/en/appfunctions.md). |
| **Shizuku** | Background app control requires Shizuku in ADB shell mode, an image-capable model, and overlay permission; text input and native element actions also need the Gimi Accessibility service. |

Available plugins:

| Plugin | Capabilities |
|---|---|
| Spotify | Search, playback, playlists, and library |
| Zhihu | Search, hot lists, synthesized answers, knowledge bases, and document tasks |
| V2EX | Nodes, topics, replies, notifications, and account information |
| Weibo | Trending searches, AI summaries, Super Topics, posts, and comments |
| Xiaohongshu | Sign-in, feeds, search, interactions, and image/video publishing through an on-device WebView |
| NetEase Cloud Music | Sign-in, search, lyrics, playlists, and authorized audio URLs; no playback control |

Some plugins require their own platform credentials and authorization. Third-party plugins can use the public [plugin API](plugin-api/).

## Privacy and permissions

Conversations, attachments, and settings are stored on the device. The developer operates no servers and collects no analytics or telemetry. Relevant content is sent to the model and other services you configure or enable; these services follow their own privacy policies.

Grant system permissions as needed and revoke them in Android settings. Document search across shared storage requires all-files access. See the [Privacy Policy](PRIVACY.md).

## Contributing and documentation

Issues, feature suggestions, translations, and code contributions are welcome. Describe the use case and discuss substantial changes in an issue first.

- [Contributing](CONTRIBUTING.md)
- [Development and releases](spec/en/releasing.md)
- [AppFunction execution](spec/en/appfunctions.md)
- [A2A sample server](a2a-server/README.md)
- [Changelog](CHANGELOG.md)

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
