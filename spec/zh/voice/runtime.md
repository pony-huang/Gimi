# 实时语音状态、设备与生命周期

[English](../../en/voice/runtime.md) · 简体中文

> [实时语音系统](realtime-voice-system.md)的姊妹篇。本文只保留代码讲不清的东西：
> 状态与三层代次为何要拆开、路由与生命周期上的坑，以及可靠性契约。类名、迁移表、
> 通道类型和精确超时值一律刻意略去——这些请直接读代码。

Audience: 参与 Gimi 实时语音功能开发的编码智能体。

## 状态机

`RealtimeVoicePhase` 的迁移请直接看代码。有两点值得记牢：

- `Thinking` 与 `Speaking` 是**按片段交替**，不是一轮只走一次。每播完一段都会把
  phase 写回 `Thinking`，只有段流结束才写 `Listening`——走的是
  `Speaking → Thinking → Listening` 两跳，不会直接跳过去。
- 三种打断（本地开口 / 后端 `Interrupted` / 播放代次校验）归一成同一个动作：
  停播、取消生成、清字幕、回到 `Listening`，只是触发来源不同。

`listening` 与 `phase` **正交**且独立存储：播放时必须继续收音才能支持打断。
`speechStarted()` 只校验 `closed`/`started`/`muted`，从不看 `phase`。别拿
`phase != Speaking` 去推断麦克风是否在收音。

## 三层代次，不要混用

| 计数器（代码名） | 防的是 |
|---|---|
| `inputRevision`（会话私有 `Long`） | 过期的**识别**结果 |
| `state.revision`（「话轮代次」） | 过期的**生成 / 播放 / 字幕**结果 |
| `RealtimePcmPlayer.generation`（「播放代次」） | 尚未写入播放器的旧音频 |

本文说的「话轮代次」一律指 `state.revision`，不是播放器的 `generation`；
`inputRevision` 又只管识别侧。三层各守一段，混用就会写错守卫条件。三者合起来
让取消之后迟到的异步结果被静默丢弃，不会把上一轮的字幕或音频漏进新一轮。

`recognize()` 在开新一轮前会 `replyJob?.cancelAndJoin()`：必须等旧轮真正取消、
文字提交完毕，下一轮才允许接管后端。这是正确性要求，不是性能优化。

## 音频设备与路由

枚举来源是 `AudioManager.availableCommunicationDevices`（通话路由设备集，
不是全量输出）。选择是纯函数（`VoiceAudioDeviceSelection`）——规则请读代码。
其中「耳机」指蓝牙 / 有线 / USB，**不含**扬声器与听筒。

容易踩的坑：

- 「连接顺序」是控制器自己记的首次出现顺序，**不是**平台列表顺序（后者不稳定，
  会让「自动模式」在插拔设备后跳到非预期设备）。
- 路由变化会重置 DSP，且重置投递到 **IO 线程**；若在主线程持 `inputLock` 做这件事，
  会与逐帧收音的 DSP 争抢，表现为卡顿。
- `devicesChanged()` 刷新两次：立即一次，1000 ms 后再一次——蓝牙通话端点可能晚于
  物理设备通知出现。
- 切换带确认超时；超时未生效就清空偏好、标记 `selectionFailed`、调用
  `clearCommunicationDevice()`。
- 断开与切换失败靠**单调递增的 `noticeRevision`** 区分（用 Long，连续两次蓝牙
  断开才能各弹一次通知）；`selectionFailed` 只是最近一次的标签。
- `close()` 会把 `start()` 时快照的通话设备设回去，忽略迟到回调。
- 代码里没有 `startBluetoothSco()` / `setSpeakerphoneOn()`，蓝牙只靠
  `setCommunicationDevice` + `MODE_IN_COMMUNICATION`。真实 A2DP/SCO 行为只能真机验证。

## 后台与生命周期

通话之所以能在离开页面后继续，是因为 `RealtimeVoiceCallController` 是
`@Singleton`，会话与整套清理链都挂在它自己的应用级协程上；页面销毁、导航跳转、
前后台切换都不会取消它们。`ViewModel` 只做 UI 派生，**不持有会话**。

前台服务：

- 类型 `microphone`（Android 14 起后台持续用麦克风必须如此）。
- `START_NOT_STICKY`：没有跨进程可恢复的音频会话，重启的空转服务只会白占麦克风。
- 通知渠道 ID 创建后不可变——要提高重要性必须换**新 ID**，并在创建时删掉旧渠道。
- 提升前台有两个顺序约束：(1) 必须在**决定退出之前**完成，否则
  `startForegroundService` 超时；(2) **每次 `onStartCommand`** 都要尝试，而不只是
  `onCreate`，这样才能从首次失败或系统降级中恢复。
- 唤醒锁续期失败没有特殊处理：通话继续，但息屏后进程可能被冻结——已知的薄弱点。

结束通话：所有挂断路径都收敛到控制器的 `end()`，导航要等 `ending` 清除后再换绑。
`end()` 幂等；`ending` 一直保持到 `close()` 返回，清理又放在 `NonCancellable` 里，
所以任何操作都不能截断旧麦克风所有权的释放。`close()` 的**释放顺序本身就是契约**
（详见代码）：停播 → 停止并 join 录音 → 取消识别并 join 回答任务 → 释放 DSP →
`session.close()` → 恢复音频焦点、路由与 mode → `Closed`、取消 scope。录音必须先
`cancel()` + `stop()` 让阻塞的 `read()` 返回，再在 IO 线程仍停在 `read` 时
`release()`——那是设备相关的崩溃路径。音频焦点**不注册丢失监听**：别的应用短暂
抢焦点就挂断会造成意外中断，所以焦点变化只当建议性提示。

## 可靠性

超时分布在 DashScope/MiniMax 各握手、路由确认和连接探测上（位置与精确值见代码）：

| 位置 | 为什么要设限时 |
|---|---|
| 识别与合成 `task-started` | 有心跳时服务端「半死」不会表现为断连 |
| 双向 `task_started` / `task_flushed` / 收尾 | 限制建连、flush 与结束等待 |
| 路由确认 | 切换迟迟不生效就回退 |
| 连接探测 | 限制握手 / `GET /models` |

**每个超时都必须跑在真实调度器上**，不能用调用方的虚拟时间测试调度器——
它们都包在 `withContext(Dispatchers.IO)` 里，确保按真实时钟兜底。

不重试是刻意的：识别按**音频时长**计费，合成按**字符**计费，重放就是重复计费。
WS/HTTP 客户端都关掉了连接失败重试；所谓「重连」不过是每轮新建连接。唯一用户可见
的重试是 `Failed` 状态下的 `Session.retry()`，它会重建录音流和 DSP，而非简单恢复播放。

背压（记理由——通道类型见代码）：入帧用无界队列，因为丢弃会静默丢音频帧，比积压
更难排查；上行音频在超过阈值后加延迟，避免堆积；段间交付与级联 `emit`/`await` 串行化，
`emit` 返回才算消费方接收，防止合成抢跑或攒下一堆付费请求。

取消必须原样传播：绝不要把 `CancellationException` 映射成业务失败。三处做法——
先 catch 取消再 catch 通用异常（`cancellationAwareRunCatching`）；用
`CancellationException` 子类表达打断，并在发打断事件前先 `ensureActive()`（避免把真取消
误判为服务端打断）；所有 `close()` 收尾都放在 `NonCancellable` 里。
