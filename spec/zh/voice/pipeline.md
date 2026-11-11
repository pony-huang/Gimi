# 实时语音端到端链路

[English](../../en/voice/pipeline.md) · 简体中文

> [实时语音系统](realtime-voice-system.md)的端到端深入篇：从麦克风到播放的
> 全链路——入口与权限、采集、回声消除、VAD 与切句、上行与识别、生成、合成与播放、
> 打断、四个典型分支。主文档保留系统总览、三种模式、供应商矩阵与改动检查清单；
> 本文件承载各环节的具体机制。

## 端到端链路

### 入口与权限

`ChatRoute` 的语音按钮 → `RealtimeVoiceHost`（320 ms 可见性动画）→
`RealtimeVoiceRoute` 申请 `RECORD_AUDIO` → `RealtimeVoiceViewModel.start()`。

- 权限已授予才启动会话；拒绝则提示并收起页面。
- 返回键 / 系统返回与左胶囊「回聊天」同义：结束通话并返回文字聊天
  （`RealtimeVoiceRoute` 注册 `BackHandler { onDismiss(); viewModel.end() }`）；
  页内「挂断」主按钮不同，它只结束会话、留在语音页回到待启动状态，不返回聊天列表。

### 采集

`core:audio/VoiceAudioRecorder`：

| 参数 | 值 |
|---|---|
| 采样率 | 16 000 Hz（`VoiceAudioFormat.INPUT_SAMPLE_RATE_HZ`） |
| 声道 / 位深 | 单声道 / PCM16 小端 |
| 音源 | `VOICE_COMMUNICATION`（通话用）/ `VOICE_RECOGNITION` |
| 读法 | `READ_BLOCKING`，每次读满 `minBufferSize`（块大小由设备决定，**不是 32 ms 帧**） |
| 时间戳 | `getTimestamp(TIMEBASE_MONOTONIC)` 推算首采样时刻 |
| 电平 | RMS → dBFS，在 −60 dBFS 至 0 之间映射到 0..1，平滑系数 0.65，每 50 ms 派发 |

「读块大小不可控」这一点需要特别说明：录音回调拿到的字节数随设备变化，**分帧不是在这里
做的**。`SileroVoiceActivityDetector.accept()` 自己按任意长度输入累积，凑满 1024 字节
（512 采样 / 32 ms）才前向一次并回调 `onFrame`。所以 `VoiceActivitySegmenter` 才能对
`pcm16.size == FRAME_BYTES` 做 `require`——帧的完整性由检测器保证，不是录音器。
检测器还把不足一帧的尾巴留在 `frameOffset` 里跨块接续，`reset()` 时清零。

时间戳这一项是有代价换来的：注释明确写了**不能用回调到达时间**，否则 AEC 的参考
信号会和实际播放错位，回声消除直接失效。

### 回声消除：硬件优先，软件兜底，二者互斥

`DefaultRealtimeVoiceRepository.start()` 的分支是关键：

```kotlin
if (recorder.echoCancellationEnabled) acceptCleanAudio(bytes)
else echoCanceller?.accept(bytes, firstTime, player::echoReference, ::acceptCleanAudio)
```

- **硬件路径**：`AcousticEchoCanceler` / `NoiseSuppressor` 绑定到本条 `AudioRecord`
  的 `audioSessionId`。这是设备可选能力，耳机或厂商实现不支持时通话仍然继续。
- **软件路径**：`SoftwareEchoCanceller` + `speexdsp`（`echo_jni.c`）。160 采样
  （10 ms @16 kHz）一帧，参考信号提前 20 ms，自适应滤波器尾长 4800 采样
  （≈300 ms）。
- **互斥而非叠加**。硬件可用时不再跑软件 AEC。注释没有给理由，工程上的合理解释是
  重复消除会损伤近端语音并白烧 CPU；改动这里之前先确认这两层不会同时启用。
- **参考信号**来自播放侧，由**麦克风时间**驱动：采集线程每处理一帧（16 kHz / 160 采样）
  就调 `player.echoReference(该帧首采样时间戳)`，拿回 240 采样（24 kHz）参考。
  实现上保留的是**按片段一条** `PcmPlaybackReference`（2 秒环形缓冲），`references`
  只留最近 2 条，然后按播放时钟逐条 `mixInto()` 到同一段输出上——这样上一句的尾音
  残响在下一句开始后仍能被消除。
- 参考信号的时间基准是 `AudioTimestamp` 的**硬件播放时钟**，不是 TTS 出包时间，也不是
  回调到达时间。这三者混用是回声消除失效最常见的原因。
- 播放是 24 kHz、AEC 参考是 16 kHz，帧几何靠 `240 @24k ↔ 160 @16k` 对齐。改任何一端
  的采样率都要同时改另一端。

**播放期回声门控**（`PlaybackEchoGuard`）。AEC 的状态会在两处归零：会话刚建立
（新建 `SoftwareEchoCanceller`，滤波器从零收敛）和**路由切换**
（`echoCanceller.reset()` + `detector.reset()` + `segmenter.reset()`）。重新收敛的
这段时间里，残留回声的 VAD 概率和真实人声没有区别。

只给这两处各开一个 800 ms 窗口并不足以修住自激循环：窗口按会话启动计时，而首个
TTS 包往往一秒后才到，窗口在播放开始前就烧完了；即便窗口完整覆盖了开头，超过
800 ms 的长播报后半程一旦 AEC 有残留（外放耦合、参考时钟抖动、硬件 AEC 声明可用
却不生效），回声帧照样以原始概率进入 `VoiceActivitySegmenter`，触发
`speechStarted()`，并在静音 704 ms 后把整段回声当作「用户这一轮说的话」交给 ASR，
于是助手把自己的播报再回答一遍，如此往复。两种模式共用
`acceptCleanAudio()` → `detector.accept()` 这个入口，所以全双工 Realtime 与
ASR+LLM+TTS 级联一起中招。

因此门控是**播放期常驻、且不依赖 AEC 是否真的消干净**的决策，作用在 detector 的
逐帧回调上。这里的「常驻」指**决策常驻**，不是「一律压制」：播放期每帧都要判定，
但明显强于回声的近端人声仍会放行，否则播报期间完全无法用语音打断，等于把全双工
降级成半双工。

| 条件 | 行为 |
|---|---|
| 真正静置（播放结束超过 400 ms 且未再开播） | 原概率直通，零延迟 |
| AEC 重置后的**首播**前 800 ms 收敛窗口 | 概率压到 0 |
| 播放中、或播完后 400 ms 声学尾音期内，但未连续 3 帧满足「VAD ≥ 0.8 + 麦克风电平 ≥ 参考电平×0.65 + 超过回声基线 0.12」 | 概率压到 0 |
| 上述条件连续成立 | 原概率直通并保持到本句结束（可打断） |

概率压到 0 的帧**仍然喂给切句器和上行预滚环**，只是不触发开口：pre-roll 音频、
VAD 隐藏状态和 barge-in 前导音都不丢。回声基线取播放期麦克风电平的「快降慢升」
包络，自适应设备增益，不拿数字参考电平去和声学衰减后的麦克风电平硬比。

两个极易写错的点：

- **撤防开关必须是 `player.playing`，不能是播放参考电平**。参考电平由
  `getTimestamp` / `playbackHeadPosition` 锚定时钟，锚点漂移时 `echoReference()`
  会虚假返回静音；把它当开关，等于在播报最响时全量放行。回声基线来自麦克风实测，
  不依赖播放时钟，才是可靠的比较基准；句间静音时基线也低，用户开口照样能过判定。
- **`playing` 变 false 不等于回声消失**。`RealtimePcmPlayer` 排空硬件队列后立刻
  把 `activeTrack` 置空，但房间混响、扬声器余振与 AEC 残留还在，这段 400 ms
  尾音期必须继续过门控，否则「播着播着突然录到声音」恰好落在撤防的那一帧。

收敛窗口由 `onAecReset()` 标记（会话建立、路由切换），在**下一次播放开始时**才
起算，而不是重置时刻；会话内后续逐句播放不重开窗口，因为 AEC 实例并未重建。
句间短间隙保留回声基线，级联模式一句一段播放时才不会反复重建基线并把响起的
回声误判成近端人声。`PlaybackEchoGuardTest` 把门控与真实切句器串起来，守住
「播报期间不产生新一轮用户输入」「播完后的混响尾音不触发新轮」「参考电平掉零
不撤防」「句间静音、明显更响的近端人声仍可打断」。

**上行门控的播放期判定**（Realtime 模式）必须同时看 `player.playing` 与
`echoGuard.tailRisk()`：`finishDuplexPlayback()` 是先把 `duplexPlayback` 置空、
再 `join()` 等播放器排空尾音，若用 channel 是否为空判断播放期，这段尾音会被
全量上行；而 `playing` 置 false 后的 400 ms 混响期若恢复全量上行，服务端
server_vad 同样会判成一次用户开口。这是独立于本地切句器的第二条自激路径。

代价是会话首播（或切设备后的首播）0.8 s 内无法用开口打断，播放期与播完后
400 ms 内打断需连续 3 帧确认（约 96 ms 延迟），且外放时只有近端人声明显强于
回声基线才能打断（弱声打断变迟钝）。近端判定的阈值、400 ms 尾音时长都是经验值，
外放/耳机/不同增益与不同房间混响需真机校准。相比「助手被自己的声音打断并
反复回答」，这个取舍更划算；若产品上宁可放弃播放期打断，把门控改成无条件压 0
即可彻底杜绝自激，但会退化为半双工。

静音（muted）的语义要单独记，这里极易写反：

- 静音**不停止**录音流，也**不停止**回声处理（软件路径照常收敛、硬件路径照常
  查询播放参考）。
- 静音**会**停掉 VAD 喂入（`if (!muted) detector?.accept(...)`），于是 `speechStarted()`
  永远等不到——**静音期间无法用开口打断播报**。
- `speechStarted()` 内部还有一道 `state.value.muted` 早退，是第二重保险。
- 所以「播报时能打断」的前提是**未静音，且该帧通过了回声门控**。真正的设计意图是：
  非静音状态下麦克风全程不停，因此 `Speaking` 阶段仍能被打断，而不需要额外的
  「监听窗口」。

### VAD 与切句

Silero VAD **v6.2** ONNX（`assets/silero/silero_vad_v6_2.onnx`），输入 512 采样 +
64 上下文采样，hidden state 256，16 kHz。有一个非常容易踩的坑：
**每帧必须重建输入张量**，复用会让输入恒为零，表现为「永远检测不到说话」。

`VoiceActivitySegmenter` 的阈值：

| 常量 | 值 | 含义 |
|---|---|---|
| `FRAME_BYTES` | 1024（512 采样 / 32 ms） | 帧长，长度不符直接 `require` 失败 |
| `START_THRESHOLD` | 0.6 | 语音概率门槛 |
| `START_FRAMES` | 5（≈160 ms） | 连续命中才判定开口，压掉瞬态噪声 |
| `END_THRESHOLD` | 0.35 | 静音门槛（低于起始门槛，避免抖动） |
| `END_FRAMES` | 22（≈704 ms） | 连续静音才切句，容忍句中停顿 |
| `PRE_ROLL_FRAMES` | 12（≈384 ms） | 前置保留，起始人声不被切掉 |
| `MAX_UTTERANCE_BYTES` | 16 000×2×30 | 单句上限 30 秒 |

30 秒在两处各出现一次，但**行为完全不同**，不要混为一谈：

| 位置 | 行为 | 实际会触发吗 |
|---|---|---|
| `VoiceActivitySegmenter.MAX_UTTERANCE_BYTES` | **截断**：把已积累音频当作一句交出去 | 会，这是超长独白的正常路径 |
| `CascadedVoiceModel.MAX_INPUT_BYTES` | `require` 失败，抛 `IllegalArgumentException` | 不会，切句器已先截断 |

所以超长独白表现为「**被切成多轮**」，不报错。第二道只是防御性断言，防的是将来有人
绕过切句器把原始音频直接灌进来。

`accept()` 与 `reset()` 都必须是 `@Synchronized`：结束通话、静音、路由切换都会从
外部线程重置，不加锁就会把上一会话的语音片段拼进新会话。

### 上行与识别

识别请求体是**整段话语的完整 PCM**，不是持续流。三家供应商的传输方式完全不同：

| 供应商 | 传输 | 分片 | 音频编码 |
|---|---|---|---|
| DashScope（`qwen-audio`） | WebSocket | 32 KB/片（≈1 s） | 裸 PCM16（`format=pcm`） |
| MiniMax（`minimax-speech`） | HTTP multipart | 整段一次 | WAV（44 字节 RIFF 头） |
| MiMo（`mimo-speech`） | HTTP `/chat/completions`（专属适配器） | 识别整段一次；合成 SSE 流式 | 上行 data-URI Base64(WAV)；下行 base64 PCM16 分块 |
| OpenAI 兼容（`deepseek` / `openai` / `kimi` / `glm`） | HTTP `/chat/completions` | 整段一次 | Base64(WAV) |
| OpenAI 专用（`openai-speech`） | HTTP multipart | 整段一次 | WAV（44 字节 RIFF 头） |

MiMo 与 OpenAI 兼容集合同走 chat-completions 传输，但保持专属协议实现
（`MiMoSpeechProtocol`）：音频是 data-URI（`data:audio/wav;base64,…`），语种走
MiMo 专属的 `asr_options`，合成文本必须放在 `assistant` 消息里，音色表
（`MiMoTtsVoices`）也自成一份。不要把它并回 `OpenAiCompatibleChatProtocol`——
两家的请求形状是真实不同的。

DashScope 的时序是 `run-task` → `task-started` → 二进制音频 → `finish-task` →
`result-generated` → `task-finished`。**`finish-task` 必须带空 `input`**，否则
返回 `InvalidParameter`。

流式与非流式的分流靠模型 ID（`endsWith("-streaming")`）。流式模型**不接受**
`intermediate_result_enabled` / `disfluency_removal_enabled`，多传会让整段白跑。

识别结果聚合按 `sentence_id` 覆盖写（同一句会先来中间结果再来最终结果）：
`Partial` 只用来更新界面显示，只有 `Final` 才是本轮输入。空识别结果回到
`Listening` 并且**不触发生成**——这是「咳嗽一声不会换来一段回答」的原因。

没有流式适配器的供应商由 `DefaultSpeechRecognitionRepository.transcribeStreaming()`
退化成一次性识别，只发一个 `Final`，调用方无需分支。

### 生成

**Simple 模式**（`data:agent/voice/AdkVoiceAudioBackend.SimpleSession`）：

1. 收尾时把「本轮用户文字 + 上一轮实际播完的回答文本」作为**一对**压入
   `heardHistory`，`size > 32` 就 `removeFirst()`。所以上限是 **32 对 = 32 轮
   一问一答**；被打断的轮同样占一个位置（它的「已听文本」可能为空或只有前半句）。
2. 每轮 `models.create(selection)` → `CascadedVoiceModel`，然后 `connect()`。
3. 指令 = 预设人设（每轮重读，中途切换预设下一轮即生效）+ 固定输出底线。
4. `sendHistory()` 时**把当前问题一并作为末尾 USER 塞进 history**。
5. 无工具，三重保证：`CascadedVoiceModel.connect()` 断言 `tools` 为空；指令里明写
   「不具备任何工具」；不传 `toolConfiguration`。

第 4 点是踩过的坑：官方接口在 history 以 USER 结尾时才启动回答。先发旧 history
再单独发当前问题，会在「上一句没听到任何回答」的场景下重放上一轮的未答问题。

**提示词预设**：给语音模型的指令永远是两层——**可替换的人设层** + **始终叠加的固定
输出底线**（朗读友好的句子规范、禁 Markdown、「用用户语言回复」的锁定）。

| 层 | 来源 | 可替换 |
|---|---|---|
| 人设 | 选中的预设；未选预设时用内置 `VOICE_INSTRUCTION` | 是——预设整体替换它 |
| 输出底线 | `AdkVoiceAudioBackend` 的 `VOICE_OUTPUT_RULES` | 否 |

- 预设有两个来源，共用一套 id 空间：**官方目录**（`domain:voice` 的
  `BuiltInVoicePresets`，随应用发布、只读）与**用户自建预设**（存应用私有偏好）。
  选中项先找官方、再找自建；两边都没有的 id 一律归一化为「不使用预设」。
- 官方提示词按完整实时语音系统指令撰写：分节结构（Role and Objective、
  Personality and Tone、Language、Reasoning、Message Channels、Preambles、
  Verbosity、Tools、Unclear Audio、Entity Capture、Long Context Behavior、
  Escalation），并包含让模型在用户真的身处危险时跳出人设的安全升级规则。
- 同一份预设同时驱动 **Simple 模式**和 **Realtime 全双工模式**
  （`openDuplex()`）；Simple 每轮重读，所以通话中途切换下一轮生效。

**Chat 模式**（`VoiceAgentConversation`）：每轮把识别文字交给与文字聊天共用的
`AgentChatRunner`，正文增量逐句送 TTS。

- **工具全自动批准**：需要确认的工具照常声明，运行期把 `callId` 逐个
  `respondToToolConfirmation(confirmed = true)`。语音场景没有弹窗确认的位置。
- **用户输入类工具**（`get_user_choice` / `adk_request_input`）不自动答复，而是
  记下 `pendingInput`、结束本轮，等用户下一句口头答复后原调用恢复。
- **上下文与持久化不由本层搬运**：事件由 ADK Runner 直接落在 Session 上，本层只在
  每轮结束和 `release()` 时通知会话内容变更，让聊天界面立刻刷新。

### 合成与播放

合成优先用**会话式**（`openSession()` 非 null，当前只有 MiniMax 双向），否则退化为
逐句一次性合成。

- 会话式路径的关键在收尾：被打断时必须 `open.cancel()` 再 `close()`，否则**服务端
  会继续合成并计费**。MiniMax 的 `cancel` 会下发协议级 `task_cancel`，这与「客户端
  单方面断开、服务端继续算钱」有本质区别。
- 切句器 `VoiceSentenceBuffer`：单句上限 100 字，标点集 `。！？!?；;` + 换行，以及
  `.` 后跟空白；正确处理高/低代理对。
- 播放 `RealtimePcmPlayer`：24 kHz 单声道 PCM16，`USAGE_VOICE_COMMUNICATION` +
  `CONTENT_TYPE_SPEECH`，`MODE_STREAM`，`WRITE_NON_BLOCKING`。

三个细节决定了播放的正确性：

| 做法 | 为什么必须这样 |
|---|---|
| 非阻塞写入，`count == 0` 时 `delay(5)` | 停播不依赖原生 `write` 返回，取消后不会继续灌入旧音频 |
| `trailingByte` 跨块保留 | 网络分块不保证落在采样边界上；结尾 `check(trailingByte == null)` 才敢断言音频完整 |
| `check(activeTrack == null)` 硬约束 | 播放必须串行，否则两段音频会互相穿插 |

排空的判定是轮询 `playbackHeadPosition >= writtenBytes / 2`（10 ms 一次），
**播放完成而非服务端 turn 完成才更新「已听文本」**。注意单位：`playbackHeadPosition`
是**帧**，`writtenBytes / 2` 是**采样数**，两者相等仅在单声道成立——改成多声道必须
同步修这个比较。

播放循环刻意跑在 `Dispatchers.Default` 而不是主线程：主线程上的状态更新和路由回调
会与 AudioTrack 写入争抢，直接后果是实时录音掉帧。

### 打断

三个独立触发源，效果一致（停播 + 取消生成 + 清字幕）：

1. **本地 VAD 检测到开口**（`speechStarted()`）——最主要的路径。人声一起就
   `replyJob?.cancel()`，不等下一次识别结果，避免为一段马上要被丢弃的回答付费。
2. **后端回传 `VoiceAudioEvent.Interrupted`**（服务端判定被打断）。
3. **播放代次失效**——`RealtimePcmPlayer.checkGeneration()` 抛出
   `CancellationException("Voice playback interrupted")`。

目前**没有**「手动停止播报」的界面控件；打断只能靠开口说话。

### 四个典型分支

把上面的链路串成时序，比逐段读更容易建立整体感。

**① 正常一轮**

```text
开口(5 帧≥0.6) → speechStarted(): inputRevision++ / 停播 / 取消旧回答 / phase=Listening
停口(22 帧<0.35) → onUtterance(整段 PCM) → recognize()
                 → phase=Transcribing；Partial 只刷界面，Final 才是输入
识别非空         → 等旧 replyJob cancelAndJoin
                 → phase=Thinking / revision+1 / responseText 清空
生成+合成        → 文本增量追加 responseText；音频段经 Channel(1) 串行播放
首个字节落 AudioTrack → phase=Speaking / speakingText=该句
该句播完         → spokenText += 该句 / phase=Thinking
段流关闭         → playback.join() → phase=Listening → finishTurn()
```

**② 空识别轮**：`Final` 为空白（咳嗽、环境噪声）→ 直接回 `Listening`，
**不生成、不占上下文、不消耗模型额度**。这是「误触发不换来一段回答」的保证。

**③ 被打断轮**：`Speaking` 中用户开口 → `speechStarted()` 抢先执行，停播 +
取消 `replyJob` + 清 `speakingText`，回 `Listening`。此时已播完的句子仍在
`spokenText` 里，会作为下一轮的「已听上下文」；未播完的部分在 Simple 模式里直接丢弃。

**④ 失败与重试**：任意环节 `fail()` → 停录音、复原路由、`phase=Failed`（会话与
界面都还在）。`retry()` 会重建录音流和整套 DSP，再走一次 `start()`，而不是只恢复播放。
