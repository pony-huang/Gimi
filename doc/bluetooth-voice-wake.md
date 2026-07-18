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

Android requires the microphone foreground service to be started while an Activity is visible. The
service is deliberately not restarted after reboot or an OS process kill; the user must enable it
again from Settings.
