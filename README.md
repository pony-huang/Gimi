<div align="center">

# Gimi

**An AI assistant that actually lives on your phone.**

Chat with it, talk to it, and let it get things done — set alarms, find files,
control music, check your calendar — without leaving the app.

[English](README.md) · [简体中文](README.zh-CN.md)

`Android 15+` · `Jetpack Compose` · `Material 3` · [`Apache-2.0`](LICENSE)

</div>

---

## What is Gimi?

Most AI apps are a text box with a model behind it. Gimi is a text box with your
**phone** behind it.

You bring your own API key from a provider you already use, and Gimi turns that
model into an assistant that can reach the things on your device: your alarms,
your calendar, your media playback, your screen brightness, folders you choose to
share, and any external tools you plug in.

Nothing is locked to one vendor. Nothing is hidden behind a subscription. Every
action the assistant wants to take can be reviewed before it happens.

## Highlights

### 💬 A chat that does more than chat

Stream replies in real time, attach photos from your camera or gallery, browse
your conversation history in a side drawer, and read replies aloud. Switch models
mid-conversation without losing your place. Run up to three tasks at once and
watch each one's progress.

### 🎙️ Voice, hands-free

Tap the mic to dictate, or set a **wake word** and let Gimi listen in the
background — pair a Bluetooth headset and you can ask for something without
touching your phone at all. Wake-word detection runs entirely on-device; the
Chinese model ships with the app and the English model is a small optional
download.

### 🧰 Tools that touch the real device

The assistant has a built-in toolbox it can reach for when your request calls for
it:

| | |
|---|---|
| ⏰ **Time** | Set alarms and timers, check the clock |
| 📅 **Calendar** | See what's coming up, create events |
| 🎵 **Media** | Play, pause, skip tracks — including in other apps |
| 🔊 **Audio** | Read and adjust media volume |
| 💡 **Display** | Screen brightness, auto-brightness, screen timeout |
| 📍 **Location** | Where you are, open it on a map |
| 📁 **Files** | Search photos, videos, audio, and documents you've shared |
| 📱 **Apps** | List, search, and open installed apps; take a photo or video |
| 📞 **Contact** | Dial a number, draft a message, look up a contact |
| 🌐 **Web** | Search the web and open links |
| ⚙️ **Settings** | Jump straight to the right system settings page |

### 🔌 Bring your own tools with MCP

Connect remote **MCP servers** (SSE or Streamable HTTP) to give the assistant
abilities Gimi doesn't ship with. Add one by hand, or paste an existing
`mcpServers` JSON config to import several at once. Toggle each server on or off
whenever you like.

### 📦 Skills

Install reusable instruction packs from a URL or a local ZIP file. A skill is
just a bundle of guidance and resources the assistant can draw on — install it
once and it's available whenever it's relevant.

### 📂 Working files

Pick folders you want the assistant to be able to search. It can only see the
folders you explicitly authorize, and you can revoke access at any time.

### 🔒 You stay in charge

- **Ask before acting** — sensitive tool calls pause and wait for your Allow or Reject.
- **Per-tool authorization** — turn the toolbox down to exactly the tools you want available.
- **Plain-language permissions** — one screen explains what each Android permission is for and lets you grant or revoke it.
- **Nothing extra phones home** — your API keys stay on your device and talk directly to the provider you chose.

## Supported model providers

Add your own API key for any of these:

<div align="center">

| | | | |
|---|---|---|---|
| **OpenAI** | **Anthropic** | **DeepSeek** | **Moonshot (Kimi)** |
| **GLM (Zhipu)** | **MiniMax** | **MiMo (Xiaomi)** | |

</div>

Both OpenAI-compatible and Anthropic-style endpoints are supported, so most other
services work too — just point Gimi at a custom API address.

Beyond chat models, you can also configure:

- a **quick model** that names your conversations automatically,
- a **speech recognition model** for voice input,
- a **speech synthesis model** and voice for reading replies aloud.

## Getting started

1. **Install the app.** Grab a release APK. Gimi requires Android 15 or newer.
2. **Add a model service.** Open *Settings → Model services*, pick a provider,
   paste your API key, and tap **Test** to make sure it works.
3. **Pick your defaults.** In *Settings → Default models*, choose the model your
   assistant should use. Add voice models here too if you want to talk to it.
4. **Grant permissions.** *Settings → Permissions* walks you through what's
   needed and why. Everything is optional — skip anything you don't want.
5. **Start chatting.** That's it.

Optional extras, whenever you're ready: turn on **Voice wake** for hands-free use,
add an **MCP server** for extra tools, install a **skill**, or authorize a
**working folder**.

## Under the hood

<details>
<summary>A little about how it's put together</summary>

Gimi is a multi-module Android app written entirely in Kotlin with Jetpack
Compose and Material 3. Each capability — chat, voice, models, tools, skills —
owns its own `domain` / `data` / `feature` modules, so features stay independent
of one another.

Agent orchestration is powered by **ADK Kotlin**, chat markdown is rendered by
**multiplatform-markdown-renderer**, wake-word detection uses **Vosk**, tool
lookup uses on-device sentence embeddings, and external tools speak the
**Model Context Protocol**. See [`AGENTS.md`](AGENTS.md) for the full
contributor guide.

</details>

## Acknowledgements

Gimi stands on the shoulders of some excellent open-source work:

- **[GetStream/chat-ai-samples](https://github.com/GetStream/chat-ai-samples)** — a
  wonderful reference for what a modern Android AI chat experience should feel
  like.
- **[mikepenz/multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)** —
  renders every markdown reply you see in the chat.
- **[google/adk-kotlin](https://github.com/google/adk-kotlin)** — the agent
  toolkit that lets the assistant plan, call tools, and follow through.

Thank you to the maintainers and contributors of all three. 🙏

## License

Gimi is released under the [Apache License 2.0](LICENSE).

<div align="center">

Made with Kotlin and a lot of coffee.

</div>
