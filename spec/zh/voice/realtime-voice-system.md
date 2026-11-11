# 实时语音系统

[English](../../en/voice/realtime-voice-system.md) · 简体中文

> 本文是实时语音的系统级文档，覆盖所有权分层、三种模式、多供应商适配、已知缺口
> 与改动检查清单。各环节的具体机制放在[深入阅读](#深入阅读)的同目录
> 子文档（端到端链路、状态与生命周期、测试覆盖）。更早的
> `spec/en/realtime-voice-models.md`（只覆盖模型适配层）已过时并被移除，历史版本
> 见 git 记录。
>
> 阅读建议：不认识的名词查文末[术语表](#术语表)；**动手改代码前先读
> [已知缺口](#已知缺口)**——文档正文按「设计意图」描述，缺口一节记录的是实现与
> 契约不一致的地方。

## 一分钟总览

实时语音默认不是「服务端全双工实时对话」，级联模式是**本地 VAD 分轮 + 每轮重新建立
后端连接**的分轮交互。整条链路是：

```text
麦克风 16 kHz 单声道
   │  回声消除（硬件 AEC 优先，软件 AEC 兜底，两者互斥）
   v
VAD 逐 32 ms 帧给语音概率
   │  切句：起始 0.60 连续 5 帧 / 结束 0.35 连续 22 帧 / 前置 12 帧 / 单句 ≤30 s
   v
整段话语 PCM ─> 识别 ─> 最终文字（中间结果只用于界面显示）
   │
   ├─ Simple：级联 Voice 模型，32 对内存上下文，无工具
   └─ Chat  ：完整 chat agent，工具全自动批准，文字由 ADK Runner 写会话
   │
   v
逐句 TTS 合成（会话式优先，否则退化为逐句一次性）
   v
24 kHz 单声道 PCM16 ─> AudioTrack 播放
   ^
   └── 用户开口 ─> 立刻停播，并取消在途生成与合成
```

这个设计带来三个必须记住的后果：

- **打断成本低、语义清晰**。人声一起就把整条付费链路（LLM + TTS）取消掉，不需要
  等服务端判定 turn end。
- **没有跨轮的连接复用**。每轮都会重新握手并重发上下文，长对话的延迟与费用特征
  没有被优化过。
- **单句有硬上限**。切句器在 30 秒处强制断句，超长独白会被切成多轮而不是报错。

## 分层与所有权

| 模块 | 负责 | 不负责 |
|---|---|---|
| `domain:voice` | 会话契约、状态快照、失败分类、能力解析接口、会话式合成接口 | 任何 Android / ADK / Compose 类型 |
| `core:audio` | 录音、Silero VAD、软 AEC（speexdsp JNI）、播放参考缓冲、音频格式契约 | 业务语义 |
| `data:voice` | 会话装配、AEC/VAD 编排、PCM 播放、设备路由、各厂商协议、前台服务、进程级控制器 | 模型选择、Agent 执行 |
| `data:agent`/`voice` | 把 ASR + 文字模型 + TTS 组装成 ADK 官方 `Model`/`LiveConnection`；聊天模式接完整 Agent | 设备、AEC、播放 |
| `feature:chat`/`realtime` | 界面状态映射与用户意图转发 | 会话所有权、生命周期结束 |
| `:app` | 清单声明、权限、通知权限引导、导航回跳 | — |

关键所有权约定（`domain/voice/.../RealtimeVoiceRepository.kt`）：

- 会话由**进程级通话控制器**持有。离开页面、进入后台、销毁 Chat NavEntry 都不得
  调用 `close()`。
- 只有用户从语音页或状态栏显式「结束通话」才释放资源。
- 录音权限由界面入口取得，会话层只负责在拿不到麦克风时归类为 `Microphone` 失败。

依赖方向上有两处值得注意的接线：

1. `data:voice` 编译期只依赖 `VoiceAudioBackend` 接口，实现由 `data:agent` 的
   `VoiceAudioModule` 提供（`AdkVoiceAudioBackend`）。所以「`data:voice` 里没有
   模型推理」不是偶然，是分层强制的。
2. `RealtimeVoiceRepository` 本身**不是单例**（每次注入新实例），单例的是
   `RealtimeVoiceCallController`。控制器在应用级 `CoroutineScope` 上持有会话，
   这才是「离开页面不中断」的真正原因。

## 深入阅读

次要细节放在下面的同目录子文档（英文伴随在 `spec/en/voice/`）；主文档保留设计意图、
契约与改动检查清单：

- [实时语音端到端链路](pipeline.md)——从麦克风到播放：入口与权限、采集、
  回声消除、VAD 与切句、上行与识别、生成、合成与播放、打断、四个典型分支。
- [实时语音状态、设备与生命周期](runtime.md)——状态机与三层代次、音频设备
  路由、后台存活、可靠性规则。
- [实时语音测试覆盖](testing.md)——每条自动化测试守护什么，哪些只能真机验证。

## 三种模式

| 维度 | Simple | Chat | Realtime |
|---|---|---|---|
| 默认 | 每次进入通话都是它 | 需显式切换 | 需显式切换 |
| 引擎 | 级联 Voice 模型 | 完整 chat agent（ADK Runner） | 经 `backend.openDuplex()` 的原生 Realtime 模型（`OpenAiRealtimeModel` / `QwenRealtimeModel`，工厂按 serviceId 分流） |
| 上下文 | 32 对内存 `heardHistory`，进程内 | ADK Session 里的完整历史 | 服务端会话；**重连即新会话，上下文不重放** |
| 已听粒度 | 只有**完整播完**的句子进入上下文 | 由 Runner 落完整模型事件 | 无——转写只进字幕 |
| 工具 | 无 | 与文字聊天一致，确认全部自动批准 | Realtime：无。RealtimeAssistant：客户端执行的 function 工具（授权的本地/MCP/插件/应用函数/沙盒来源），确认全部自动批准（见[实时助手工具](#实时助手工具)） |
| 落库 | 无 | Runner 写 Session；本层只发变更通知 | 无 |
| 占用 | 无 | `AgentRuntimeGate` 租约（`REALTIME_VOICE`） | 无 |
| 前置条件 | — | 必须已有 `conversationId` + `modelSelection` | 必须已配置默认 Realtime 模型（见[默认 Realtime 选择](#默认-realtime-选择)）；未配置时入口引导到默认模型页 |

`RealtimeVoiceConfiguration` 用 `require` 把 Chat 与 Realtime 的前置条件写死：
**不能隐式新建聊天**。模型选择在通话期间保持不变。

### 实时助手工具

RealtimeAssistant 等于 Realtime 加一条**客户端执行的工具桥**
（`:data:agent` 里的 `RealtimeAssistantTools` / `RealtimeVoiceToolBridge`）。动手前需要知道的事实：

- **声明链路**。工具桥在 `VoiceAudioBackend.openDuplex()` 内按通话解析一次
  （跑在 `Dispatchers.IO`——MCP server 探测在这里发生，坏 server 最多给通话启动
  加上 8 秒探测预算）。声明经 ADK `GenerateContentConfig.tools` 缝进入厂商的
  `session.update`；两厂商线上形状不同（OpenAI GA 扁平
  `{type, name, description, parameters}`；Qwen 声明嵌在 `function` 键下）。
  重连不重新解析——桥跨重连复用；重连即新会话，新会话的 `session.update`
  会自动重新下发声明。
- **来源白名单**。工具桥复用 chat agent 同一批 `AgentContribution` 来源，
  在 `RealtimeAssistantTools` 里按白名单筛选：`local`（工具授权页开关 +
  确认类工具照常声明，含副屏 mobile_use）、`mcp`（无会话勾选 → 全局启用集合）、
  `plugin`、`appfunction`（其按通话创建的工具集是唯一归桥所有的资源，会话结束关闭）、
  `sandbox`。刻意排除：`base`（会话/工件/记忆与用户输入挂起工具依赖 ADK Runner）、
  `skill`（沙盒编排）、`official`（厂商远程声明/厂商侧执行，本地 run 会抛）、
  `model`（模型目录工具在通话中无意义）。新增贡献方必须有意识地加入白名单。
- **执行上下文**。没有 ADK Runner，也没有持久化会话。执行跑在最小
  `InvocationContext` 构造出的裸 `ToolContext` 上，其 `RunConfig` metadata 携带
  `ToolRunMetadata`（模型运行信息 + 按通话分配的 `mobile_use` 归属）；会话状态、
  工件与确认都没有存储侧。`FunctionTool` 的确认门以恒定传入
  `ToolConfirmation(confirmed = true)` 满足——语音通话全部自动批准，与语音 Chat
  模式同一策略。
- **轮次流程（两厂商一致）**。`output_item.added(function_call)` 建档；
  `function_call_arguments.delta` 累积；`.done` 定稿。`.done` 缺失时，在
  `response.done` 落地前冲出未定稿调用（覆盖 OpenAI「从 response.done 读参数」的
  路径）：正常完成、status 缺失以及未知的厂商终态（记日志）都按完成处理；只有
  明确表示未正常完成的状态（`cancelled` / `canceled` / `incomplete` / `failed`）
  和被取消/打断（含 `speech_started` barge-in）的响应才把未定稿调用**直接丢弃**
  ——截断参数不执行、也不写回。门面（`NativeRealtimeDuplexSession`）
  把执行轮放进子协程——不阻塞事件接收——用互斥串行多轮，逐个经
  `conversation.item.create` / `function_call_output` 写回结果，每轮发送一次
  `response.create`。函数调用参数永远不进语音播报（无 TTS）；断线取消执行中的轮次
  （重连即新会话）。
- **界面反馈**。`RealtimeVoiceEvent.ToolCallStarted` 映射为
  `RealtimeVoiceState.toolNotice`；字幕区显示「正在调用工具」胶囊。工具轮未产出
  （`response.done` 时 `toolNotice` 非空）时状态机保持 `Thinking`，让第二轮的
  转写增量继续拼接；通知在第二轮首个转写增量或音频块到达时清除，打断、重连或
  下一轮用户发言也会清除。

### 默认 Realtime 选择

Realtime 模型在**默认模型**页选择，不在通话里选。该页有一个 `Realtime` 分节，两行：
Realtime 模型与 Realtime 音色。

| 事实 | 规则 |
|---|---|
| 候选范围 | 只列出「已启用 + 已配密钥 + **声明了原生 Realtime 适配器**」的服务下的模型。带 Realtime 标记却没有适配器的模型会静默回落级联链路（而级联要求 STT/TTS 能力），所以整组隐藏而不是显示为不可选。 |
| 音色表 | 不建表。每个厂商适配器按模型给一个**缺省音色**（Qwen 3.1 用 `longanqian_v3.1`、3.0 用 `longanqian`、OpenAI 用 `cedar`），另声明是否接受自由文本覆盖。Qwen 接受声音复刻 `voice_id`；OpenAI 音色集固定，只给下拉。 |
| 音色归属 | 自定义音色记住所属模型。合法音色集按模型代际不同，因此换模型后该覆盖不再生效、回落模型缺省音色；**清除模型不丢音色值**，选回同一模型即恢复。 |
| 可用性 | 默认选择在切模式时解析。服务被删、密钥被清、模型消失或不再声明原生适配器，都按**未配置**处理：Realtime 模式不可用，入口引导到默认模型页并直接打开 Realtime 模型选择。 |
| 生效范围 | 选择在通话期间固定。通话中改默认只对后续通话生效。 |

通话侧的模式菜单会显示已配置的 Realtime 模型名，但**不提供**模型切换：默认模型页是
唯一入口。

适配器事实收在 `domain:voice` 的 `RealtimeVoiceSupport` 契约后面（服务标识、上行契约、
缺省音色、内置音色表、自定义音色策略），由 `data:agent` 的每个原生适配器各自贡献，
再由 `VoiceCapabilityRegistry` 汇总。因此 `VoiceLiveModelFactory` 是从适配器读上行采样率
与缺省音色，而不是按 serviceId 分支。

### 模式切换

`selectMode()` 的顺序不能改：

1. 前置拒绝（同模式 / 正在切换 / Chat 不可用 / `Persistence` 失败——最后一项实际
   很少触发，见[已知缺口](#已知缺口)）。
2. `generation++`、`switching = true`（这是**控制器自身的**代次，用于丢弃过期的启动
   结果，与播放器代次无关）。
3. `startup?.cancelAndJoin()` → `finishPendingTurn()`；**失败则只复位 `switching`，
   保留原会话**供重试。
4. 取消状态收集 → `close()` → 二次校验 `generation` 与 `session` 身份 → `create(mode)`。
5. 切回 Simple 时 `conversationId` 传 null。

第 3 步的「失败保留原会话」是有意的：文字没提交成功就切模式，等于静默丢失一轮对话。

## 供应商与能力矩阵

先澄清一对容易混的能力名：**STT 与 ASR 不是同一件事的两种叫法，而是两个能力位**。
STT 指专用的整段转写服务，ASR 指复用对话模型做识别。模型同时具备两者时优先走专用
STT（更准、链路更短）。

| 服务 ID | 识别 | 流式识别 | 合成 | 会话式合成 | 音色 | 连通性探测 |
|---|---|---|---|---|---|---|
| `qwen-audio` | 专用 STT | 有 | 有 | 无 | 级联：按模型查表；实时：按模型的缺省音色（3.1 用 `longanqian_v3.1`，3.0 用 `longanqian`）+ 自由文本覆盖 | 实时 WS 握手，15 s |
| `minimax-speech` | STT（multipart） | 无 | SSE | 双向 WS | 远程 + 本地兜底 | 未注册专用探测 |
| `mimo-speech` | STT + ASR | 无 | 有 | 无 | 静态表 | `GET /models`，15 s |
| `openai-speech` | 专用 STT | 无 | 有（二进制 PCM 流） | 无 | 级联：静态表；实时：内置音色表（`OpenAiRealtimeVoices`，默认 `cedar`），不接受自定义音色 | 实时 WS 握手，15 s |
| `deepseek` / `openai` / `kimi` / `glm` | STT + ASR | 无 | 有 | 无 | 空 | 由聊天服务探测承担 |

DashScope 级联路径不做会话式合成是有明确理由的：百炼按连接计费，长连接复用没有收益。

**实时与级联模型共处同厂商的一条配置。** 不再有独立的 Realtime 服务种类：实时模型挂在
同厂商语音服务的「实时语音对话」分组下，携带 `ModelCapability.Realtime` 标记（且不声明
STT/TTS/ASR，级联选择永远不会误拾取）。实时会话仍是常驻全双工——服务端 server_vad 管
判轮，模型原生听音频、原生说话。千问的 Realtime 走 OpenAI 同源的事件协议，常驻会话本身
就是产品形态。

**原生 Realtime 缝已经落地。** 所选模型携带 `Realtime` 能力标记时 `VoiceLiveModelFactory.create()`
按 serviceId 分流：`openai-speech` → `OpenAiRealtimeModel`、`qwen-audio` →
`QwenRealtimeModel`（都实现 ADK `Model.connect()`/`LiveConnection`），其余仍返回
`CascadedVoiceModel`。原生路径的关键事实：

- 厂商适配器在 `:data:agent`，与 `CascadedVoiceModel` 并列；传输内核 `VoiceWsKernel`
  与 `@SpeechWebSocketClient` 限定符已晋升 `:core:network`（跨两模块的两个真实消费方）。
  握手失败的业务分类在构造时注入（domain:voice 的 `voiceHandshakeFailure`）——内核只知道
  HTTP 状态码，不知道领域语义。
- 仓储不接触 ADK 类型：`VoiceAudioBackend.openDuplex()` 返回 `RealtimeVoiceDuplexSession`
  门面，连接生命周期（配置确认门控、有限退避重连、重连即新会话）都由门面持有，
  ADK 响应在门面内翻译成 `RealtimeVoiceEvent` 领域事件。
- 上行是 16 kHz 采集，按**厂商上行契约**（`VoiceLiveModelFactory.realtimeUplink()`）
  整形：OpenAI 线性重采样 24 kHz（`:core:audio` 的 `Pcm16Resampler`），Qwen 原生
  16 kHz 直喂。整形后的持续流进入门面 `NativeRealtimeDuplexSession`（厂商无关，
  原 `OpenAiRealtimeDuplexSession` 去 OpenAI 命名复用）。
  助手播放期间仓储用本地 Silero VAD 加 12 帧预滚环做上行门控（**混合门控**），
  残留回声不会伪造服务端打断。`openDuplex()` 解析期间（实时助手工具桥探测窗口）
  麦克风已开、会话未就绪，上行帧进入有界 FIFO：VAD 的帧缓冲按帧复用，入队必须
  拷贝；会话就绪后由单一协程按序发送，会话失败则清空积压，不把陈旧音频带进
  下一次通话。
- 打断语义按厂商分流：OpenAI 依赖服务端 `interrupt_response` 自动取消并在
  `speech_started` 时补发 `conversation.item.truncate`（以已发出时长近似截断点）；
  Qwen 完全以服务端自动打断为准（不发送任何截断/取消事件），模型层维护**静默窗口**——
  `speech_started` 后丢弃残余音频增量直到下一个 `response.created`。
- 字幕走 ADK 转写通道：用户侧 `inputTranscription`（OpenAI 服务端 `gpt-4o-transcribe`
  终稿；Qwen 有 `delta` 增量——已确定累计文本，编排层按替换语义渲染——与 `completed`
  终稿），助手侧 `outputTranscription` 增量（Qwen 的 `transcript.done` 在已有增量时
  忽略，防追加语义下字幕重复）；客户端自身不做任何 ASR/TTS。
- 实时模型以 `ModelCapability.Realtime` 标记挂在同厂商语音服务下（不再是独立服务种类）；
  标记不参与级联能力（STT/TTS/ASR）选择，实时入口（模型选择、会话创建）以该标记分派。

### 能力解析

`VoiceCapabilityRegistry` 的解析键是 **`(serviceId, capability)`**：

- 合成只按 `serviceId` 匹配；识别必须 `serviceId` 与 `capability` 同时命中。
- 流式识别未命中返回 null，由调用方退化为一次性识别——**这里是软失败，不是异常**。
- `LLM` 是**唯一特例**：恒为 true，因为该层不判断模型是否可用。除它之外，声明了但
  未注册的 `(serviceId, capability)` 组合会显式失败——别拿 `LLM` 的宽松行为推断
  STT/TTS/ASR 也会宽容。

### 失败分类

`RealtimeVoiceFailure` 是**可翻译**的分类，界面只显示映射后的文案，不显示底层异常：

| 分类 | 来源 |
|---|---|
| `Configuration` | `start()` 默认值、`isAvailable()` 断言失败 |
| `AudioProcessing` | Silero / AEC 构造阶段 |
| `Microphone` | 音频焦点未授予、`recorder.start()` 失败、录音回调报错 |
| `Recognition` | 识别流异常（且 revision 仍有效） |
| `Generation` | 生成流异常且没有更具体的分类 |
| `Synthesis` | 一次性 TTS 非取消异常 |
| `Playback` | 播放失败 |
| `Persistence` | Chat 模式进入时目标会话已不存在（`acquire()` 抛出）；`close()` 提交阶段抛异常（此时 phase 已是 `Closed`）。**`finishTurn()` 是空实现，所以「保存失败」这条主路径并不存在** |
| `ConversationBusy` | `AgentRuntimeGate` 抛 `AgentSessionBusyException` |

`RealtimeVoiceFailure` 之下还有一套**不映射到它的**低层分类
`VoiceFailureKind`（`NotConfigured` / `Authentication` / `Network` / `Service` …），
只用于设置页和连接测试。两套别混：界面拿不到 `VoiceFailureKind`。

## 已知缺口

以下是当前代码的真实状态，不是待办建议。改这块之前请先确认它们是否已被修掉。

1. **持久化契约名存实亡**。`confirmPlayback()` / `finishTurn()` /
   `retryPersistence()` 在 Simple 与 Agent 两个 session 里都是空实现，而
   `VoiceAudioBackend` 的接口注释仍在描述旧的「稳定片段 ID 去重」「失败保留事件 ID
   供重试」语义。真实影响：
   - 「保存失败」根本不会发生，因此 `Persistence` 只在两处可达：Chat 模式进入时目标
     会话已被删除，以及 `close()` 提交阶段抛异常（此时界面已在收尾）。
   - `retry()` 里的「重试保存」分支恒判成功；界面上的 `voice_save_failed` /
     `voice_retry_save` 是不可达路径。
   - `InMemoryRealtimeVoiceCallControllerTest` 里「`Persistence` 阻止模式切换」的
     断言仍然有效，但它验证的是**控制器对失败的反应**（注入假会话），不是真实后端
     行为。
   - 这是契约层面的债，不是线上 bug。真正的风险在于后来者照接口注释以为「播放确认
     与持久化重试已实现」，在这些空实现上继续叠功能。
2. **聊天模式的「已听粒度」没有落到存储**。ADK Runner 写的是完整模型事件，
   被打断的回答在重开聊天时会照原样显示。Simple 模式则相反，只有播完的句子进上下文。
   两种模式在这一点上行为不一致。
3. **`DefaultRealtimeVoiceRepository` 没有单元测试**。播放期回声决策本身已抽到
   `PlaybackEchoGuard` 并由 `PlaybackEchoGuardTest` 锁住，但仓储里的接线
   （`player.playing` / `tailRisk()` 判定、参考电平传递、上行 channel 竞态）仍没有测试缝；
   `VoiceAudioRecorder`、`SoftwareEchoCanceller`、JNI、`AudioTrack` 只能真机验证。
4. **原生 Live 供应商只有 OpenAI 与 Qwen 两家**。`EndpointStrategy.VendorOwned`
   已在 `qwen-audio` / `openai-speech` 落地（端点由厂商自有写死；千问按可选业务空间
   ID 改写专属域名）。原生缝（`VoiceLiveModelFactory` → ADK `Model`）
   已经真实存在，但实现者只有 `CascadedVoiceModel` 与两家 Realtime 适配器；
   文本模型实现 `Model` 依旧不会自动获得实时语音能力。
5. **MiniMax 没有注册专用连通性探测**，其设置页「连接测试」行为未确认。
6. **没有手动停止播报的控件**，打断只能靠开口。
7. **Simple 每轮重建连接并重发 32 轮上下文**，长通话的延迟与费用未做过优化。
8. **Realtime 模型与音色只有默认模型页这一个选择口**。两点值得先知道再动手：没有
   通话内兜底（没配默认就是不可用，而不是"从目录里挑第一个"），音色绑定的是它被设置时
   的那个模型而不是服务。通话进行中改默认不影响当前通话。
9. **工具轮与打断天然存在竞态**。用户在工具执行中开口时，执行轮仍会写回结果并发送
   `response.create`；服务端可能在用户说话中途开始（并播报）一条回复。仓储侧的本地
   VAD 会立即停播并清掉工具提示，最坏情况只是「回答来晚了」，不会卡状态。这一点只能
   真机验证。

## 改动这个系统时的检查清单

- **加供应商**，完整清单（漏任何一项都会在设置页或运行期缺东西）：
  1. 实现 `SpeechRecognizer` / `StreamingSpeechRecognizer` / `SpeechSynthesizer` /
     `StreamingSpeechSynthesizer`，用 `@IntoSet` 注册，声明 `serviceIds` 与
     `capabilities`。
  2. 在 `VoiceCapabilityRegistry` 里注册 `(serviceId, capability)`；要流式识别必须
     显式注册 `STREAMING_STT`，否则会被静默退化成一次性识别。
  3. **音色**：在 `DefaultTtsVoiceRepository` 注册服务表（缓存键含 `modelId`），并
     准备本地兜底音色表。
  4. **连通性探测**：实现并注册 `SpeechConnectionProbe`。不注册的话，设置页「连接
     测试」对该供应商的行为是未定义的（MiniMax 目前就是这种情况）。
  5. 端点与鉴权留在 data 层。厂商自有端点（千问、OpenAI 语音）写死官方地址，统一在
     `speechRequestBaseUrl` 解析（千问按可选业务空间 ID 改写专属域名）；只有代理型
     语音服务（MiniMax、MiMo）保留用户可配置基址。
  6. 别在 `VoiceLiveModelFactory` 之外另起一套**厂商选择**缝：级联供应商都在这里实现
     ADK `Model`，原生 Realtime 路径也是同一个工厂按模型的 `Realtime` 能力标记分流出的
     `OpenAiRealtimeModel` / `QwenRealtimeModel`（未知厂商回落级联）。
     面向仓储的 `RealtimeVoiceDuplexSession` 门面（`NativeRealtimeDuplexSession`，
     厂商无关）是全双工模式的应用侧契约，不是厂商缝。
  7. 补契约测试，并确认限时跑在真实调度器上而不是虚拟时间。
- **加原生 Realtime 供应商**（全双工，在级联清单之上）：
  1. 实现一个 ADK `Model`，`connect()` 打开一条常驻 wss 会话，音频上行必须等配置确认
     （`session.updated`）——确认前绝不开始流式上行。
  2. 在 `SpeechModelConfigs` 里给该厂商的语音服务加实时模型分组（模型携带
     `ModelCapability.Realtime`、不带级联能力标记），并注册 wss 握手探测。端点保持
     厂商自有：常量在 `SpeechEndpoints`，千问按可选业务空间 ID 构造专属域名。
  3. **声明适配器能力**：实现 `domain:voice` 的 `RealtimeVoiceSupport`（服务标识、上行
     契约、按模型的缺省音色、内置音色表、自定义音色策略），用 `@IntoSet` 贡献。没有这份
     声明，该服务的 Realtime 模型不会出现在默认选择的候选里——这正是要点：未声明的适配器
     会静默回落级联。
  4. 走 `VoiceAudioBackend.openDuplex()`——重连退避与「重连即新会话」由门面持有，
     仓储只消费 `RealtimeVoiceEvent` 并把 16 kHz 帧喂进 `sendAudio`。
  5. 遵守上行门控契约：非播放期持续全量；播放期本地 VAD 门控 + 12 帧预滚
     （`DefaultRealtimeVoiceRepository` 的 `UPLINK_GATE_THRESHOLD`）。
  6. 协议 JSON 用 Gson 手工构建（每厂商一个 Protocol：`OpenAiRealtimeProtocol` /
     `QwenRealtimeProtocol`）；上行采样率按厂商契约：OpenAI 保持 24 kHz pcm16
     （不原生收 16k），Qwen 原生 16 kHz 直喂——新厂商在自己的 `RealtimeVoiceSupport`
     里声明上行契约，不要写进 `VoiceLiveModelFactory`。
  7. 打断语义按厂商分流：服务端自动取消的（Qwen）不发送截断/取消事件，模型层用
     静默窗口丢弃残余音频；需要补截断的（OpenAI）才发 `conversation.item.truncate`。
- **改实时助手的工具面**——只改 `RealtimeAssistantTools` 的来源白名单。依赖
  ADK Runner 的能力（会话状态、工件、用户输入挂起、技能）必须留在白名单外；新增
  贡献方要有意识决策，不能被静默并入。声明的线上形状在 `OpenAiRealtimeProtocol` /
  `QwenRealtimeProtocol` 内按厂商维护；`RealtimeVoiceToolBridgeTest` 守住执行语义。
- **改 VAD 阈值**：`VoiceActivitySegmenterTest` 会挡；同时确认 30 秒上限仍然生效，
  否则长独白会一路占内存。
- **改播放格式**：输出必须保持 24 kHz 单声道 PCM16。换格式要么在适配器里转换，
  要么同时改播放器与 AEC 参考几何（`240 @24k ↔ 160 @16k`）。
- **改路由、AEC 收敛或播放期门控**：收敛窗口由 `onAecReset()`（路由切换/会话启动）
  标记、在**下一次播放开始**才起算；播放期与播完后 400 ms 声学尾音期都用「麦克风
  实测回声基线 + 播放参考电平」把回声帧的 VAD 概率压到 0，同一轮播报的后续句子不
  重开窗口。撤防开关必须是 `player.playing`（参考电平会因时钟锚点漂移虚假归零），
  且 `playing` 变 false 后的尾音期不能立刻撤防。上行门控须同时看 `player.playing`
  与 `echoGuard.tailRisk()`，不能看 `duplexPlayback` 是否为空，否则服务端 server_vad
  会走一条独立于本地切句器的自激路径。改动前先跑 `PlaybackEchoGuardTest`（含
  「窗口内开始播放」「播完后混响尾音」「参考电平掉零」三组最小对照）。
- **改会话状态**：任何新的异步结果都要用 revision 守卫，否则取消后迟到的结果会漏进
  新一轮。
- **改领域接口或 Hilt 绑定**：必须装配并安装完整 APK、保留用户数据重启进程再验证。
  只装配不安装、或在旧 APK 上打 HOT_FIX（项目内部的热修下发机制），都可能让运行时
  混入不兼容类型。
- **验证真机行为**：认证、播放、路由三项只能真机确认，接口一致性不代表平台支持。

## 术语表

| 术语 | 含义 |
|---|---|
| ADK（ADK Kotlin） | Google Agent Development Kit 的 Kotlin 版。实时语音的推理侧跑在它的 `Model` / `LiveConnection` 官方接口上 |
| `LiveConnection` | ADK 的一次实时连接，由 `Model.connect()` 返回；本项目每轮新建一个 |
| `CascadedVoiceModel` | 把「文字模型 + ASR + TTS」串起来、对外伪装成实时语音的适配器；级联路径的实时模型实现（原生路径另有 OpenAI / Qwen 两个 Realtime 适配器） |
| AEC | 声学回声消除（Acoustic Echo Cancellation） |
| VAD | 语音活动检测（Voice Activity Detection），本项目的「轮次边界」全靠它 |
| DSP | 此处指 AEC、VAD、切句这一组逐帧处理环节的统称 |
| PCM16 | 16 位有符号整数采样。本文音频一律小端、单声道 |
| Silero | 开源 VAD 模型，本项目用 v6.2 ONNX |
| speexdsp | 开源音频处理库，这里提供软件 AEC 的 JNI 实现 |
| ONNX | 模型交换格式，由 ONNX Runtime 加载执行 |
| SSE | Server-Sent Events，服务端逐事件推送的 HTTP 响应流 |
| 话轮代次 | 即 `state.revision`。一次问答为一代，异步结果必须匹配当前代次才写入状态 |
| 播放代次 | 即 `RealtimePcmPlayer.generation`。`stop()` 时递增，用于丢弃旧轮音频 |
| 租约 | `AgentRuntimeGate` 对某个聊天的运行期独占权，同一会话只能有一个写入者 |
| 前台服务 | Android `ForegroundService`；`microphone` 类型才允许后台持续录音 |
| 音频焦点 | Android 的音频互斥机制。这里只申请、**不监听**丢失 |
| 通信设备 | `AudioManager.availableCommunicationDevices` 返回的通话路由设备集 |
