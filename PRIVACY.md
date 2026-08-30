<div align="center">

# Gimi Privacy Policy

English · [简体中文](PRIVACY.zh-CN.md)

</div>

**Effective date: August 30, 2026**

This policy explains how Gimi (the "app") handles data. Gimi is an open-source (Apache License 2.0),
local-first AI assistant for your phone: **the developer runs no servers and collects or uploads no
user data.**

## What we don't collect

- No analytics, usage tracking, or telemetry of any kind
- No crash reporting, ads, or tracking SDKs
- No account or sign-up — we don't know who you are
- The developer has no server that could touch your data

## Data stored on your device

The following stays on your device and is never sent to the developer (who operates no servers):

- **Conversations**: chat content and attachments, in the app's private local database
- **Memory data**: vector embeddings computed entirely on-device
- **Settings and model service configs**: including API keys you enter, encrypted with the Android
  Keystore before storage
- **Wake-word models**: downloaded once, then run fully offline
- **Working folders**: accessed only after you explicitly authorize a folder

Uninstalling the app or clearing its data removes all of the above. If system cloud backup is on,
app data is backed up per Android's standard mechanism, subject to your device settings.

## Data sent to third-party services

Gimi itself uploads nothing, but as a bring-your-own-key AI assistant, **only when you configure and
use the following services** does relevant data go to the corresponding provider:

| Service | What is sent | When |
|---|---|---|
| Model services you configure (OpenAI, Anthropic, DeepSeek, Moonshot, Zhipu, MiniMax, MiMo, Gemini, or any OpenAI / Anthropic-compatible endpoint) | Conversation content, images you send, tool results | When you send a message or invoke a tool |
| Speech recognition / synthesis services you configure | Recorded audio or text to synthesize | When you use voice input or play spoken replies |
| MCP servers you add, plugins you install | Data required by those tools to run | When you invoke the corresponding tools |
| alphacephei.com (official Vosk model hosting) | Model download requests only, no personal data | When you download a wake-word model |
| GitHub (api.github.com and Releases) | Update-check and APK download requests only, no personal data | When you check for or install app updates in Settings |

How each provider handles your data is governed by its own privacy policy, which is outside this
project's control — configure only services you trust. Wake-word detection and memory embedding run
entirely offline on your device.

## Permissions and built-in tools

All permissions and built-in tools are optional, enabled one by one by you in *Settings → Tools /
Permissions*:

| Permission | Purpose |
|---|---|
| Microphone | Voice input, wake word |
| Bluetooth | Background voice wake via Bluetooth headset |
| Calendar (read / write) | Let the assistant view and create calendar events |
| Fine / coarse location | Let the assistant get your current location |
| Photos / video / audio | Search and play media, read media you share into chat |
| Notifications | Update notices and foreground-service notifications; notification listener for cross-app media control |
| Modify system settings | Adjust brightness, volume, screen timeout, etc. |
| Alarms | Set alarms and timers |
| Usage stats | Read recently used apps when generating recommendations |
| Install packages | Download and install app updates in-app |

**Note**: tool results (calendar events, location coordinates, media info, file search results,
etc.) become part of the conversation context and are sent to the model service you configured. You
can disable any tool or revoke any permission at any time.

## Open source and modified builds

This policy applies only to versions built or published from this repository
([github.com/pony-huang/Gimi](https://github.com/pony-huang/Gimi)). Third-party forks, modified
builds, and distributions from other sources are not covered — check with whoever publishes them.

## Disclaimer

Gimi is provided "as is" under the Apache License 2.0, without warranty of any kind, express or
implied. To the maximum extent permitted by applicable law, **the developer shall not be liable for
any direct or indirect damages arising from the use of, or inability to use, the app**, including but
not limited to data loss, privacy leaks, the conduct or billing of third-party services, or misuse of
modified builds. You decide which services to configure and what content to send, and you are
responsible for that choice.

## Changes to this policy

Updates to this policy will be published in this repository and take effect upon publication.

## Contact

Questions? Open a GitHub issue: <https://github.com/pony-huang/Gimi/issues>
