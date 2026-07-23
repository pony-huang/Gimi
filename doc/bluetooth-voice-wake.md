# Bluetooth voice wake

The Bluetooth voice wake feature listens only through a connected Bluetooth HFP/SCO microphone.
Wake-word recognition is performed locally. Audio is sent to the configured speech-to-text service
only after the local wake word has matched.

The wake model is bundled in the APK at `assets/voice/vosk-model-small-cn-0.22.zip`. The checked-in
archive originates from:

- `https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip`
- SHA-256: `3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba`

Vosk API and `vosk-model-small-cn-0.22` are distributed under the Apache License 2.0. The model is
extracted into the app's private files directory on first enable, so the device does not need to
download it at runtime. Bundling adds about 42 MB to the APK.

An English wake model can be downloaded at runtime from the Vosk model index (Apache License 2.0):

- `https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip`
- SHA-256: `30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498`
- Size: 41,205,931 bytes (~41 MB)

Each model is installed under `files/voice/wake-model/<model-id>/` and verified against its
hardcoded SHA-256 before activation. Wake keywords are stored per model language
(`wake_keyword.<languageTag>`), so switching models restores that language's saved keyword or its
default (`你好助手` / `hey assistant`). Voice-confirmation word lists and the TTS confirmation prompt
follow the active wake model's language, not the app locale.

Android requires the microphone foreground service to be started while an Activity is visible. The
service is deliberately not restarted after reboot or an OS process kill; the user must enable it
again from Settings.
