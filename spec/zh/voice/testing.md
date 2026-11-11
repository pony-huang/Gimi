# 实时语音测试覆盖

[English](../../en/voice/testing.md) · 简体中文

> 实时语音自动化测试清单与各自守护的内容。先读
> [实时语音系统](realtime-voice-system.md)了解设计意图；其检查清单指明何时
> 必须同步更新这些测试。


`data:voice/src/test` 共 21 个测试类（另有 1 个假事件辅助 `DashScopeTestEvents`）；
Realtime 原生路径：`data:agent` 的 `OpenAiRealtimeProtocolTest` /
`OpenAiRealtimeModelTest` 与 `QwenRealtimeProtocolTest` / `QwenRealtimeModelTest`
（MockWebServer WS:配置先行帧、缓冲上行冲出、事件映射、打断语义（截断 / 静默窗口）、
握手分类、单接收者与 close 解锁契约子集）、`:core:audio` 的 `Pcm16ResamplerTest` 契约、
`data:modelcatalog` 的 `SpeechModelProviderTest`（覆盖两个 Realtime 模板）：

| 文件 | 验证的契约 |
|---|---|
| `DashScopeRecognizerTest` | `run-task` 载荷、流式模型省略多余参数、携带对话上下文、失败不重放音频 |
| `DashScopeSynthesizerTest` | 合成参数与音频帧、`task-failed` 映射、无音频即失败 |
| `DashScopeConnectionProbeTest` | 握手成功 / 401 / 不可达三种归类 |
| `MinimaxRecognizerTest` | multipart WAV 契约、`language=auto` 省略 header、HTTP 失败归类 |
| `MinimaxSynthesizerTest` | hex 音频顺序、SSE 分帧兼容、`base_resp` 错误本地化、端点跟随基址 |
| `MinimaxBidiSynthesizerTest` | 会话式片段流与开窗参数、`cancel` 触发 `task_cancel` |
| `MiniMaxEndpointsTest` | 三端点同基址、尾斜杠、空基址回落、WS 路径替换 |
| `MinimaxVoiceProviderTest` | 远程音色 + 三级本地兜底 |
| `QwenAudioTtsVoicesTest` | 音色与模型强绑定，跨代 ID 不共享 |
| `SpeechRecognitionCancellationTest` | 取消会真正 cancel 底层 OkHttp Call |
| `SpeechRecognitionGatewayTest` | `encodePcm16Wav` 的 RIFF/fmt/data 与字节序 |
| `VoiceCapabilityRegistryTest` | 按 `(serviceId, capability)` 解析、未注册能力显式失败、Realtime 能力随原生适配器声明判定 |
| `DefaultSpeechRepositoriesTest` | 识别需已配 STT 模型、结果 trim、合成取首个 Key |
| `DefaultTtsVoiceRepositoryTest` | 按 serviceId 路由、缓存键含 modelId |
| `SimpleChatPromptPreferencesTest` | 预设增删改查、坏数据丢弃、官方预设可选不可删、说明字段往返 |
| `SpeechSettingsPreferencesTest` | 自动播报默认关闭且跨实例保留 |
| `VoiceAudioDeviceSelectionTest` | 按连接顺序而非平台顺序、手动不被覆盖、断开逐级回退 |
| `VoiceAudioRoutingControllerTest` | 手动选择保持、断开清偏好、被拒保留实际设备、`close()` 复原 |
| `VoiceSessionLifecycleTest` | 并发 `close()` 只释放一次且都等到释放完成 |
| `InMemoryRealtimeVoiceCallControllerTest` | 重开等旧资源、模式切换等 flush/close、`Persistence` 阻止切换、双入口幂等、Realtime 模式采用默认选择与音色、未配置时不新建会话 |
| `RealtimeVoiceCallNotificationContentTest` | 通知快照折叠优先级、高频语音更新不改快照、状态复位驱动服务退出 |

相关但不在 `data:voice` 的：`core:audio` 的 `VoiceActivitySegmenterTest` /
`PcmPlaybackReferenceTest` / `PcmLevelTest`，`data:agent` 的
`CascadedVoiceModelTest` / `AdkVoiceAudioBackendTest` / `VoiceAgentConversationTest` /
`LiveConnectionContractTest` / `VoiceSentenceBufferTest`。

提示词预设这一层的测试：

| 文件 | 验证的契约 |
|---|---|
| `domain:voice` 的 `BuiltInVoicePresetsTest` | 官方目录条目 id 唯一、字段非空、正文保持实时语音分节结构（Role … Escalation） |
| `feature:chat` 的 `SimpleChatPromptViewModelTest` | 新建预设落库带生成的 id、空白输入不入库、编辑保留原 id、官方预设可列出且只看不进编辑 |

默认 Realtime 选择这一层的测试：

| 文件 | 验证的契约 |
|---|---|
| `data:agent` 的 `VoiceLiveModelFactoryTest` | Realtime/级联按能力标记分派、上行契约随适配器声明、会话音色缺省按模型代际解析、自定义音色覆盖缺省 |
| `data:modelcatalog` 的 `ModelServiceConfigurationPersistenceTest` | 默认 Realtime 模型与音色覆盖跨重启存活、可清除；服务级音色字段已移除 |
| `feature:modelsettings` 的 `DefaultModelSettingsRealtimeTest` | 候选只列声明了原生适配器的服务、音色候选跟随所选模型、自定义音色绑定到模型、清除模型与清除音色各自落库 |
| `feature:modelsettings` 的 `DefaultModelSettingsViewModelCharacterizationTest` | 既有按能力分组与 TTS 音色回落行为不受 Realtime 默认值影响 |

`data:voice` 与 `core:audio` 都**只有 `test` 源集、没有 `androidTest`**。所以麦克风、
AEC、蓝牙路由、`AudioTrack` 只能靠真机验证，CI 抓不到。
