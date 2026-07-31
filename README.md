<div align="center">

# Gimi

**An AI assistant on your phone.**

Chat, talk, and get things done — alarms, files, music, calendar — all in one app.

[English](README.md) · [简体中文](README.zh-CN.md)

</div>

---

## What it does

You bring your own API key. Gimi gives its model access to your device: alarms, calendar, media,
screen brightness, folders you pick, and any external tools you add. No vendor lock-in. No
subscription. Actions need your approval before they run.

## Features

### Chat

Stream replies. Attach photos from camera or gallery. Switch models mid-conversation. Run up to
three tasks at once.

### Voice

Tap-to-talk or set a wake word for hands-free use. Wake-word detection runs on-device. Chinese model
included; English model is a small download.

### Built-in tools

|              |                                                       |
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

Connect remote MCP servers (SSE or Streamable HTTP) for extra tools. Add one manually or paste a
JSON config to import several at once.

### Skills

Install instruction packs from a URL or local ZIP. A skill bundles guidance and resources the
assistant can use.

### Working files

Pick folders for the assistant to search. Only folders you authorize are visible. Revoke anytime.

### You're in control

- Sensitive actions pause and wait for Allow/Reject.
- Enable only the tools you want.
- Permissions page explains what each one does.
- API keys stay on-device.

## Supported providers

|                 |               |                   |                     |
|-----------------|---------------|-------------------|---------------------|
| **OpenAI**      | **Anthropic** | **DeepSeek**      | **Moonshot (Kimi)** |
| **GLM (Zhipu)** | **MiniMax**   | **MiMo (Xiaomi)** |                     |

OpenAI-compatible and Anthropic-style endpoints are supported. Other services work via custom API
address.

Also configurable: a quick model for conversation titles, a speech recognition model, and a speech
synthesis model.

## Getting started

1. Install the APK. Requires Android 14+.
2. *Settings → Model services*: pick a provider, paste your key, tap **Test**.
3. *Settings → Default models*: choose your chat model. Add voice models if needed.
4. *Settings → Permissions*: grant what you're comfortable with. All optional.
5. Start chatting.

Optional: enable voice wake, add MCP servers, install skills, authorize working folders.

## Thanks

- [GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)
- [mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
- [google/adk-kotlin](https://github.com/google/adk-kotlin)

## License

[Apache License 2.0](LICENSE)
