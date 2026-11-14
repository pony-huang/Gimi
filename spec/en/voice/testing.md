# Realtime voice test coverage

[简体中文](../../zh/voice/testing.md) · English

> Inventory of the automated tests protecting realtime voice and what each one
> guards. Read [Realtime voice system](realtime-voice-system.md) first for
> the design intent; the checklist there tells you when these tests must be
> updated.


Vendor adapter tests moved to `:data:modelaccess` (with the adapters/models);
`data:voice/src/test` keeps the business layer (repositories, preferences,
routing, call controller, notification). The fake-event helper
`DashScopeTestEvents` travels with them. The Realtime native path spans
`OpenAiRealtimeProtocolTest` / `OpenAiRealtimeModelTest` and
`QwenRealtimeProtocolTest` / `QwenRealtimeModelTest` (MockWebServer WS:
config-first framing, buffered uplink flush, event mapping, barge-in semantics
(truncate / silent window), handshake classification, and the duplex-applicable
subset of the single-receiver/close-unblocks contract), the vendor speech
adapter tests (DashScope / MiniMax / MiMo / OpenAI-compatible audio) in
`:data:modelaccess`; the
`Pcm16ResamplerTest` contract in `:core:audio`, and `SpeechModelProviderTest`
in `data:modelcatalog` (covering both Realtime templates):

| File | Contract verified |
|---|---|
| `DashScopeRecognizerTest` | `run-task` payload, streaming models omit extra parameters, conversation context carried, audio not replayed on failure |
| `DashScopeSynthesizerTest` | Synthesis parameters and audio frames, `task-failed` mapping, no audio means failure |
| `DashScopeConnectionProbeTest` | Handshake success / 401 / unreachable classification |
| `MinimaxRecognizerTest` | Multipart WAV contract, `language=auto` omits the header, HTTP failure classification |
| `MinimaxSynthesizerTest` | Hex audio ordering, SSE framing tolerance, `base_resp` error localization, endpoints follow the base URL |
| `MinimaxBidiSynthesizerTest` | Session-based segment flow and window parameters, `cancel` triggers `task_cancel` |
| `MiniMaxEndpointsTest` | Three endpoints on one base, trailing slash, empty-base fallback, WS path substitution |
| `MinimaxVoiceProviderTest` | Remote voices plus three-level local fallback |
| `QwenAudioTtsVoicesTest` | Voices bound tightly to models; IDs not shared across generations |
| `SpeechRecognitionCancellationTest` | Cancellation actually cancels the underlying OkHttp Call |
| `SpeechRecognitionGatewayTest` | `encodePcm16Wav` RIFF/fmt/data layout and byte order |
| `VoiceCapabilityRegistryTest` | Resolution by `(serviceId, capability)`, unregistered capabilities fail explicitly, Realtime availability follows the native adapter declaration |
| `DefaultSpeechRepositoriesTest` | Recognition requires a configured STT model, results trimmed, synthesis takes the first key |
| `DefaultTtsVoiceRepositoryTest` | Routing by serviceId, cache key includes modelId |
| `SimpleChatPromptPreferencesTest` | Preset CRUD, corrupt data discarded, official presets are selectable but not deletable, description round-trip |
| `SpeechSettingsPreferencesTest` | Auto-playback off by default and preserved across instances |
| `VoiceAudioDeviceSelectionTest` | Connect order beats platform order, manual choice not overridden, stepwise fallback on disconnect |
| `VoiceAudioRoutingControllerTest` | Manual selection persists, disconnect clears preference, rejection keeps the actual device, `close()` restores |
| `VoiceSessionLifecycleTest` | Concurrent `close()` releases once and both callers await completion |
| `InMemoryRealtimeVoiceCallControllerTest` | Re-entry waits for old resources, mode switch waits for flush/close, `Persistence` blocks switching, both entries idempotent, Realtime mode adopts the configured default selection and voice, no session is created without one |
| `RealtimeVoiceCallNotificationContentTest` | Snapshot collapse priority, high-frequency voice updates do not change the snapshot, state reset drives service exit |

Related tests outside `data:voice`: `core:audio`'s `VoiceActivitySegmenterTest` /
`PcmPlaybackReferenceTest` / `PcmLevelTest`; `:data:modelaccess`'s
`CascadedVoiceModelTest` / `LiveConnectionContractTest` / `VoiceSentenceBufferTest` /
`RealtimeToolRoundRunnerTest`; and `data:agent`
`AdkVoiceAudioBackendTest` / `VoiceAgentConversationTest` / `RealtimeAssistantToolsResolveTest` / `RealtimeModelBaseUrlTest`.

Prompt presets:

| File | Contract verified |
|---|---|
| `domain:voice`'s `BuiltInVoicePresetsTest` | The official catalog ships unique, non-blank presets whose bodies keep the realtime section skeleton (Role … Escalation) |
| `feature:chat`'s `SimpleChatPromptViewModelTest` | A new preset is stored with a generated id, blank input is dropped, editing keeps the id, official presets are listed and viewable without entering the editor |

Tests for the default Realtime selection:

| File | Contract verified |
|---|---|
| `:data:modelaccess`'s `VoiceLiveModelFactoryTest` | Realtime/cascade dispatch by capability marker, uplink contract from the adapter declaration, session voice defaults per model generation, custom voice overrides the default |
| `data:modelcatalog`'s `ModelServiceConfigurationPersistenceTest` | Default Realtime model and voice override survive a restart and can be cleared; the service-level voice field is gone |
| `feature:modelsettings`'s `DefaultModelSettingsRealtimeTest` | Candidates only cover services declaring a native adapter, voice options follow the selected model, a custom voice is bound to its model, clearing model and clearing voice are separate writes |
| `feature:modelsettings`'s `DefaultModelSettingsViewModelCharacterizationTest` | The existing capability grouping and TTS voice fallback are unaffected by the Realtime defaults |

Both `data:voice` and `core:audio` have a `test` source set and **no
`androidTest`**. Microphone, AEC, Bluetooth routing, and `AudioTrack` can
therefore only be verified on hardware; CI cannot catch them.
