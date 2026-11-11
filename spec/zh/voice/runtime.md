# 实时语音状态、设备与生命周期

[English](../../en/voice/runtime.md) · 简体中文

> [实时语音系统](realtime-voice-system.md)的深入篇：状态机与三层代次、音频设备
> 路由、后台存活（前台服务与结束通话），以及可靠性规则——超时、刻意不重试、背压、
> 取消传播。

## 状态机

`RealtimeVoicePhase` 迁移：

| 起点 | 事件 | 终点 |
|---|---|---|
| `Idle` | `start()` 成功（录音已启动） | `Listening` |
| `Listening` | 切出一句话（开口瞬间已从 `Speaking` 切回） | `Transcribing` |
| `Transcribing` | 识别为空 | `Listening` |
| `Transcribing` | 识别出非空文本 | `Thinking`（话轮代次 +1） |
| `Thinking` | 首个音频块真正写入 `AudioTrack` | `Speaking` |
| `Speaking` | **该片段播完（无例外，每段都走）** | `Thinking` |
| `Thinking` | 段流已结束且最后一段也已播完 | `Listening` |
| 任意（含 `Speaking`） | 本地开口 / 后端 `Interrupted` / 播放代次失效 | `Listening` |
| 任意 | `fail(failure)` | `Failed`（会话存活、音频已停） |
| `Failed` | `retry()` | 重新 `start()` → `Listening` |
| 任意 | `close()` | `Closed`（终态） |

读这张表要抓住一条环：**`Thinking` 与 `Speaking` 按片段交替**，不是一次性
`Thinking → Speaking`。代码里 `playSegment()` 播完总会把 phase 写回 `Thinking`，
只有 `reply()` 在 `playback.join()` 返回（段流关闭）之后才写 `Listening`。所以
「最后一段播完」走的是 `Speaking → Thinking → Listening` 两跳，不是直接从
`Speaking` 跳到 `Listening`。

三个打断源在表里合并成一行，是因为它们的作用完全相同：停播 + 取消生成 + 清字幕 +
回到 `Listening`。区别只在触发位置（本地 VAD / 后端事件 / 播放器代次校验）。

### 为什么 `listening` 与 `phase` 分开

领域注释直接给了答案：「播报与推理的阶段；监听状态独立保存，因为播放时仍需收音以
支持打断」。代码事实也印证：`speechStarted()` 只校验 `closed` / `started` / `muted`，
**不校验 phase**——这是 `Speaking` 期间还能被打断的前提（当然，前提是未静音）。

所以不要用 `phase != Speaking` 去推断「是否在收音」，两者是正交的。

### 三层代次，不要混用

| 计数器 | 递增时机 | 丢弃什么 |
|---|---|---|
| `inputRevision`（会话私有 `Long`） | 开口、静音切换、失败、`finishPendingTurn`、`close` | 过期的**识别**结果 |
| `state.revision`（状态快照内，即「话轮代次」） | 每轮识别成功、进入 `Thinking` 时 `+1` | 过期的**生成 / 播放 / 字幕**结果 |
| `RealtimePcmPlayer.generation`（播放器内部，即「播放代次」） | `stop()` / 新 `play()` 时递增 | 旧轮**尚未写入**的音频 |

术语提醒：文档里的**「话轮代次」专指 `state.revision`**，与播放器的 `generation`
不是同一个东西；`inputRevision` 又只作用于识别侧。三层各管一段，混用会让守卫条件
写错。三层合起来保证：取消之后迟到的异步结果只会被静默丢弃，不会把旧回答的字幕或
音频漏进新一轮。

另外 `recognize()` 在开新一轮前会 `replyJob?.cancelAndJoin()`——必须等旧轮真正
取消并完成文字提交，下一轮才允许取得后端所有权。这不是性能取舍，是正确性要求。

## 音频设备与路由

枚举来源是 `AudioManager.availableCommunicationDevices`（通话路由设备集，
不是全量输出设备）。类型映射到六类：扬声器 / 听筒 / 蓝牙（含 BLE、助听器）/
有线 / USB / 其他。

选择是纯函数（`VoiceAudioDeviceSelection`）：

```text
手动指定命中 → 连接顺序倒序里的耳机 → 扬声器 → 列表第一个
```

这里的「耳机」指蓝牙 / 有线 / USB 三类，**不含扬声器与听筒**。

「连接顺序」是控制器自己记的首次出现顺序，不是平台列表顺序——平台顺序不稳定，
会让「自动模式」在设备插拔后跳到非预期设备。

几个容易出错的时序：

- 路由变化会**重置 DSP**（`echoCanceller.reset()` / `detector.reset()` /
  `segmenter.reset()`），且重置投递到 IO 线程。在主线程持 `inputLock` 做这件事会
  与录音线程的逐帧 DSP 争抢，表现为卡顿。
- `devicesChanged()` 刷新两次：立即一次，**1000 ms 后再一次**。蓝牙通话端点可能晚于
  物理设备通知出现。
- 切换有 30 秒确认超时。届时仍未生效就清空偏好、标记 `selectionFailed`、
  `clearCommunicationDevice()`。
- 断开与切换失败**靠 `noticeRevision` 单调递增区分通知类型**，`selectionFailed`
  只是最近一次的标签。用 Long 而不是布尔，是为了让连续两次蓝牙断开都能各弹一次提示。
- `close()` 会把 `start()` 时快照的通话设备设回去，忽略迟到回调。

代码里没有 `startBluetoothSco()` / `setSpeakerphoneOn()`，蓝牙只靠
`setCommunicationDevice` + `MODE_IN_COMMUNICATION`。实际 A2DP/SCO 切换行为只能真机
验证。

## 后台与生命周期

### 为什么离开页面不中断

`RealtimeVoiceCallController` 是 `@Singleton`，自建
`SupervisorJob + Dispatchers.Main.immediate` 的 scope。会话、状态收集、清理链全挂在
应用级协程上，所以页面销毁、导航跳转、前后台切换都不会取消录音/生成/播放。

反过来，`RealtimeVoiceViewModel` 只在 `viewModelScope` 里做 UI 派生，**不持有
session**。

### 前台服务

- 类型 `microphone`（Android 14 起后台持续用麦克风必须如此）。
- `START_NOT_STICKY`：进程死亡后不恢复。没有跨进程可恢复的音频会话，空转的服务
  反而会白占麦克风。
- 通知渠道 `realtime_voice_call_v2`（`IMPORTANCE_DEFAULT`）。渠道重要性创建后不可
  变，所以从旧的 `realtime_voice_call` 升级必须换 ID，并在创建时删除旧渠道。
- 通知快照只折叠成 `VoiceCallNotificationContent`（阶段、静音、结束中、标题、
  是否后台）。麦克风电平、流式字幕**不触发**通知刷新。
- 唤醒锁 `PARTIAL_WAKE_LOCK`，10 分钟过期，每 5 分钟续期。续期失败**没有**特殊处理：
  不会结束通话，但息屏后可能被系统冻结。这是当前已知的薄弱点。

两个必须遵守的顺序约束：

1. **先提升前台再判断退出**。否则 `startForegroundService` 超时会抛
   `ForegroundServiceDidNotStartInTimeException`。
2. **每次 `onStartCommand` 都尝试提升前台**，不只在 `onCreate`。回到前台时
   `ensureForeground()` 对已运行的服务只会走到 `onStartCommand`；提升不可重入就
   无法从首次失败或系统降级中恢复。

### 结束通话

只有两个入口，都收敛到 `RealtimeVoiceCallController.end()`：

- 页内的「结束通话」（需先回到语音页：文字聊天的语音按钮或点通知）；
- 状态栏通知的「结束通话」。

`end()` 是幂等的，且 `ending` 状态一直保持到 `close()` 返回——前台服务与唤醒锁
必须覆盖整个资源释放过程。清理协程放在 `NonCancellable` 里：退出页面或新会话启动
都不能截断旧麦克风所有权的释放。

会话层 `close()` 的释放顺序（顺序本身是契约）：

```text
停播(player.stop → generation++)
  → 停止并 join 录音(recorder.releaseAndJoin)
  → 取消识别 / join 回答任务
  → 持 inputLock 释放 DSP
  → session.close()（失败降级为 Persistence，但不阻止后续释放）
  → 恢复音频焦点与路由、复原 audio.mode
  → phase = Closed，取消 scope
```

录音必须先 `cancel()` + `stop()` 让阻塞的 `read()` 返回，再在 IO 线程仍停在 `read`
时 `release()`——那是设备相关的崩溃路径。

音频焦点**不注册丢失监听**：后台被其他应用短暂抢占就关会话会造成意外中断，所以
焦点变化只当建议性提示。

## 可靠性

### 超时

| 位置 | 超时 | 目的 |
|---|---|---|
| DashScope 等 `task-started` | 15 s | 心跳保活下服务端「半死」不会表现为断连 |
| DashScope 合成 `task-started` | 15 s | 同上 |
| MiniMax bidi `task_started` | 15 s | 超时关连接 |
| MiniMax bidi `task_flushed` | 10 s | 超时抛 `IOException` |
| MiniMax bidi 收尾 | 3 s | 等 `task_finished` |
| 路由确认 | 30 s | 未生效则回退 |
| 连接探测 | 15 s | 握手 / `GET /models` |

所有限时都刻意放在 `withContext(Dispatchers.IO)` 里：注释写明「限时在真实调度器上
执行（而非调用方的虚拟时间测试调度器）」，保证无论调用方处于什么上下文，超时都按
真实时钟兜底。

### 不重试是刻意的

识别和合成**一律不重试**，理由写在代码注释里且值得保留：

- 识别按**音频时长**计费，重放会产生重复费用；
- 合成按**字符**计费，重放同一段文本同样重复计费。

所以 WS 客户端整体 `retryOnConnectionFailure(false)`，HTTP 客户端也逐个关掉。唯一的
「自动重连」其实是每轮新建连接。

用户可见的重试只有 `RealtimeVoiceSession.retry()`，且**仅 `Failed` 状态可用**。它会
重建录音流、重开 DSP，而不是简单恢复播放。

### 背压

| 层 | 机制 | 理由 |
|---|---|---|
| WS 收帧 | `Channel(UNLIMITED)` | 有界+丢弃会静默丢音频帧，比积压更难排查 |
| WS 发音频 | 队列 > 1 MB 时 `delay(10)` | 防止上行堆积 |
| 简单模式音频段 | `Channel(RENDEZVOUS)` | 开始收集才授权生产，取消播放能唤醒被阻塞的连接 |
| 会话层段间 | `Channel(1)` | 段与段之间真正的串行播放 |
| 播放写入 | 非阻塞 + `delay(5)` | 取消后不再灌入旧音频 |
| Cascaded 交付 | `Channel(RENDEZVOUS)` + `await` | `emit` 返回才表示消费方接受，避免合成抢跑或攒付费请求 |

### 取消必须原样传播

`CancellationException` 一律不映射为业务失败。三处具体做法：

- `core:common/cancellationAwareRunCatching` 先 catch 取消再 catch 通用异常。
- `CascadedVoiceModel` 用继承 `CancellationException` 的 `TurnInterrupted` /
  `ConnectionClosed` 表达打断，并在 `catch` 里先 `ensureActive()` 再发
  `interrupted` 事件——避免把「真取消」误判成「服务端打断」。
- 所有 `close()` 收尾都在 `NonCancellable` 里，取消不能截断资源释放。
