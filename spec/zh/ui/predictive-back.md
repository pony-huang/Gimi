简体中文 · [English](../../en/ui/predictive-back.md)

# 预测式返回支持

核对日期：2026-11-11。读者：修改返回导航、手势或系统动画的代理与开发者。

本文记录本应用的预测式返回基线、发现的缺口，以及把这些缺口排除在本次变更之外的决策。这是一份行为/兼容性文档，不是 UI 设计草案：本文描述的工作不需要视觉设计批准，也不需要 Compose 预览草案。若将来真的要定制返回动画或返回交互，须遵循[预览优先的 UI 设计评审](preview-first-design-review.md)。

**状态：本次工作未改动任何运行时代码。** 下文全部基于已检查的源码、AndroidX 官方 sources jar 与平台文档。**没有任何一项经过真机验证**——见[验证](#验证)。

## 为什么平台指南大部分不适用

平台设置指南分成两套互不相干的导航栈：

- 本应用使用 **Navigation 3**（`androidx.navigation3:navigation3-ui:1.0.0`，
  `app/src/main/java/github/ponyhuang/gimi/navigation/AppNavigation.kt` 中的
  `NavDisplay`），不是 Navigation Compose。仓库里没有 `NavHost`、没有
  `navigation-compose` 依赖，也没有 `popEnterTransition` / `popExitTransition`。
- 指南中「`navigation-compose` 2.8.0 或更高」的要求，以及
  `NavHost(popExitTransition = ...)` 示例，只属于 Navigation Compose。官方共享元素
  导航文档反过来明确写道：**所有版本的 Navigation 3 都支持预测式返回**。
- Navigation 3 的对应能力是 `NavDisplay.predictivePopTransitionSpec`，已在
  `AppNavigation.kt:217` 设置。它目前与 `popTransitionSpec` 逐字相同，且只接收
  `swipeEdge`、不接收进度值；进度由
  `NavigationEventTransitionState.InProgress.latestEvent.progress` 经
  `SeekableTransition.seekTo(progress, previousScene)` 在库内部驱动。

因此本次工作不包含任何依赖升级、`NavHost` 迁移或新增 pop 过渡。

## 已核实的基线

| 平台要求 | 文档下限 | 本应用 | 状态 |
|---|---|---|---|
| AndroidX Activity | 1.6.0+ | `activity` / `activity-compose` **1.13.0** | 满足 |
| Navigation | `navigation-compose` 2.8.0+ | Navigation 3 `navigation3-ui` **1.0.0** | 不适用 |
| Material3 | 1.3.0+ | BOM **2026.02.01** → `material3` **1.4.0**（官方 BOM POM） | 满足 |
| `android:enableOnBackInvokedCallback` | 旧 target 需显式 opt-in | 未声明；`targetSdk = 37` | 无需改动（见下） |
| SDK 级别 | Android 15+ 默认开启系统动画 | `compileSdk` 37.1 / `targetSdk` 37 / `minSdk` 34 | 满足 |
| 根 Activity 不得拦截返回 | — | `MainActivity` 无 `onBackPressed`、`addCallback`、`OnBackInvokedCallback`、`BackHandler` 或 `finish()` | 满足 |

`app/src/main/AndroidManifest.xml` 的 `<application>` 与所有 `<activity>` 均未声明
`android:enableOnBackInvokedCallback`。官方文档说明 target Android 15+ 的应用不需要
该 flag，Android 16 行为变更又让 target API 36+ 的应用默认获得 back-to-home、
cross-task、cross-activity 动画。**该 Manifest 属性本身的字面默认值官方文档并未
明文给出**，此处记为一项假设；若将来下调 `targetSdk` 必须重新核实。由于
`minSdk = 34`，指南里 Android 13 的开发者选项测试步骤完全不适用。

## 已经生效的部分

- **back-to-home**：`NavDisplay` 1.0.0 注册
  `NavigationBackHandler(isBackEnabled = scene.previousEntries.isNotEmpty())`。
  在 `SinglePaneSceneStrategy` 下 `previousEntries = entries.dropLast(1)`，等价于
  `backStack.size > 1`。在根目的地该 handler 被禁用，平台输入层注销其
  `OnBackInvokedCallback`，返回手势交回系统并播放 back-to-home 动画。
  `goBack`（`AppNavigation.kt:109-111`）在根栈是 no-op，且此时根本不会被调用，
  因此不会吞掉手势。
- **应用内预测式 pop**：`predictivePopTransitionSpec` 已接线；平台
  `OnBackAnimationCallback`（`onBackStarted` / `onBackProgressed` /
  `onBackInvoked` / `onBackCancelled`）经 `activity` 1.13.0 的 dispatcher 桥接
  喂给 `navigationevent` 1.0.0。
- **返回 handler**：六处 `androidx.activity.compose.BackHandler` 走的是受支持的
  `OnBackPressedDispatcher` 路径，由 `activity` 1.13.0 桥接到
  `NavigationEventHandler` / `OnBackInvokedCallback`，并非「未迁移」的回调。
  它们都不感知进度，这只影响应用内动效质量，不影响系统动画是否可用。
- **Activity 图**：共 7 个 Activity（`:app` 2 个、`:data:agent` 5 个权限/透明中转页），
  均不拦截返回。没有 `taskAffinity`、`documentLaunchMode`，也没有
  `FLAG_ACTIVITY_NEW_DOCUMENT` / `MULTIPLE_TASK` 入口。

## 系统动画的适用范围

| 动画 | 本应用是否适用 | 处置 |
|---|---|---|
| back-to-home | 适用——根目的地 | 验收目标 |
| cross-activity | 原理上适用（7 个 Activity、无拦截） | 记为「无拦截、无需改动」；见已知缺口 4 |
| cross-task | **N/A**——无 multi-task / document 入口 | 明确排除 |
| 应用内预测式 pop | 适用——`NavDisplay` | 验收目标 |
| Android 13 开发者选项 | 不适用（`minSdk = 34`） | 不测试 |

设备矩阵：Android 14（打开开发者选项 **Predictive back animations**）、Android 15
（默认）、Android 16+（默认，且命中 `targetSdk 36+` 行为变更）。cross-task 在清单中
明确标 N/A，而不是无声跳过。

## 决策记录

2026-11-11 设计评审锁定的决策；评审中答「是」即采纳此处所示推荐答案。

| 编号 | 决策 |
|---|---|
| Q1 | 把任务从「按指南迁移」重定义为「盘点、补真实缺口、验证、落文档」；不动依赖版本、不引入 `NavHost`。 |
| Q2 | 验收只覆盖 back-to-home + 应用内 pop + 抽屉；cross-activity 记为无需改动，cross-task 标 N/A；设备只做 14 / 15 / 16。 |
| Q3 | 给 `ModalDrawerSheet` 传 `drawerState`——**经源码核实后被 Q10 推翻，未实施**。 |
| Q4 | 不改 `GimiSheetBase`；真机验证该配置下是否仍有动画，并记录结论。 |
| Q5 | 不迁移六处 `BackHandler`，只做审计，重点关注两处无条件启用的。 |
| Q6 | 悬浮窗 `Key.Back` 路径定性为独立的兼容性回归、不是动画缺口；纳入范围但先实测。 |
| Q7 | 不添加 Manifest flag；记录 `targetSdk 37` 的依据。 |
| Q8 | 交付双语 spec、`AGENTS.md` 索引行、人工验证清单；不新增自动化 UI 测试、不走预览草案与设计批准。 |
| Q9 | 实时语音返回语义：**以代码为准**。返回 = 结束通话并回文字聊天；只对齐两份 pipeline spec，不改运行时行为。 |
| Q10 | 若发现抽屉返回冲突或 BottomSheet 无动画，不就地修改返回交互；回退/记为已知限制，另开动效任务。 |
| Q11 | 仅在真机确认 `Key.Back` 路径确实失效时，才允许对悬浮窗做最小兼容修复。 |
| Q12 | 验证清单作为两份 spec 中双语同步的「验证」章节；不新建 openspec change；不手写 `CHANGELOG.md`（由 release-please 生成）。 |

## 已知缺口，以及为何本次不修

1. **抽屉没有预测式返回动画。**
   `feature/chat/.../ChatConversationDrawer.kt:175` 使用的是不带 `drawerState` 的
   `ModalDrawerSheet` 重载。Material3 1.4.0 明确注明该重载默认不处理返回、也没有
   Android 14+ 的预测式动画；带 `drawerState` 的重载通过
   `DrawerPredictiveBackHandler` 两者都具备。
   **为何不修**：该重载会在 `drawerContent` 内注册
   `PredictiveBackHandler(enabled = drawerState.isOpen)`，而它的组合顺序位于
   `ChatRoute.kt:146` 的 `BackHandler` **之后**。handler 按 LIFO 消费（最后组合的
   启用 handler 优先），因此在重叠状态「抽屉打开 + 最近会话多选 + 非删除态」下，
   按返回会**变成关抽屉，而不是退出多选**；多选态只能靠 `ChatRoute.kt:149` 的
   `LaunchedEffect(drawerState.currentValue == Closed)` 兜底间接触发。
   `ModalNavigationDrawer` 自身不注册任何 back handler，所以不存在第三方竞争。
   按 Q10，这属于行为变化：这一行修复被回退，handler 优先级的设计（重排或收窄
   某个 handler）另列为需走预览优先动效评审的独立任务。

2. **BottomSheet 动画未验证。**
   `core/designsystem/.../GimiSheetBase.kt` 设置了
   `sheetGesturesEnabled = false`、`skipPartiallyExpanded = true`，并通过自封装的
   `GimiSheetController` 控制关闭。`ModalBottomSheet` 默认带预测式返回动画，但该
   具体配置未经真机验证。按 Q4/Q10：不改代码，只记录设备实测结论。

3. **Android 16 上悬浮窗返回可能失效。**
   `feature/assistant/.../AssistantConversationPanel.kt:94-101` 在 `OVERLAY` 模式下
   通过 `onPreviewKeyEvent` 消费 `Key.Back`，注释自述悬浮窗宿主没有
   `OnBackPressedDispatcher`。Android 16 对 target API 36+ 的行为变更明确写出
   `onBackPressed` 不再调用、`KeyEvent.KEYCODE_BACK` 不再派发。这是**功能回归**
   （面板收不回来），不是动画缺口。Q11 允许最小兼容修复，但前提是完成真机验证；
   **本次没有做真机验证**，故未改任何代码。此项为 P0 清单项。

4. **Compose back handler 与 cross-activity 动画。**
   平台 Note 指出在 Activity 层拦截返回会禁用 cross-activity 动画。本应用没有
   Activity 直接拦截返回，但六处 Compose `BackHandler` 注册在宿主 Activity 的
   dispatcher 上。静态源码无法判定这是否算 Activity 级拦截，故列入真机验证（V6）。

5. **`predictivePopTransitionSpec` 与 `popTransitionSpec` 完全相同。**
   没有按边缘或进度做差异化处理。当前接受现状；定制它属于预览优先流程下的动效
   设计变更。

6. **语音 spec 只对齐了一部分。**
   `spec/en/voice/pipeline.md` 与 `spec/zh/voice/pipeline.md` 现已改为与代码一致
   （返回 = 结束通话并回文字聊天；页内「挂断」按钮只结束会话）。这两份 companion
   就是 Q9 的全部范围。`spec/en/voice/realtime-voice-system.md:80` /
   `spec/zh/voice/realtime-voice-system.md:63` 仍写「只有显式结束通话才释放资源」，
   `spec/en/voice/runtime.md:157-159` / `spec/zh/voice/runtime.md:129-130` 仍只列
   两个结束通话入口。这四处保留为已记录的后续文档任务；因 Q9 只授权修改 pipeline
   两份，未擅自改写。

7. **没有自动化测试覆盖。**
   `src/test` 与 `src/androidTest` 中没有任何返回导航断言，且 `AGENTS.md` 禁止新增
   自动化 UI 交互、布局或截图测试。验证只能靠人工与录屏。

## 验证

**待办——尚未进行任何真机运行。** 按[预览优先的 UI 设计评审](preview-first-design-review.md)，
动效只有在实际操作或查看录屏之后才能报告为已验证。下列每个动画项都需录屏，并记录
设备、系统版本、开发者选项是否开启。若某项失败，按最后一列所链决策处理，**不要
临场就地加修复**。

| 编号 | 设备 / 准备 | 步骤 | 预期 | 证据 / 失败时 |
|---|---|---|---|---|
| V1 | Android 14（API 34），开发者选项 → 打开 **Predictive back animations** | 在根聊天页缓慢侧滑返回后取消；再重复一次并提交 | 应用随手势缩放/位移向桌面；取消恢复原页面；提交回到桌面 | 录屏。失败时按 Q1/Q2 重开根 handler 审计 |
| V2 | Android 15（API 35），无需开发者选项 | 同 V1 | 同样的 back-to-home 动画，默认开启 | 录屏 |
| V3 | Android 16+（API 36+），`targetSdk 37` | 同 V1 | API 36+ 默认下同样的 back-to-home 动画 | 录屏 |
| V4 | V1–V3 | 从非根 `NavEntry`（如设置页）缓慢侧滑返回、取消，再提交 | `predictivePopTransitionSpec` 的滑入滑出跟随手势；取消页面不变；提交只弹出一个条目 | 录屏。Q2 验收目标 |
| V5 | V1–V3 | 打开聊天抽屉按返回；再打开抽屉、进入最近会话多选态按返回 | 第一种情况记录当前基线（无抽屉 back handler）；第二种情况**必须退出多选且抽屉保持打开** | 录屏。缺口 1 的 P0 回归护栏；将来若因 `drawerState` 改动而回归，按 Q10 回退 |
| V6 | V1–V3 | 从 `:data:agent` 的权限 / 透明 Activity 按返回 | 播放 cross-activity 动画并回到 `MainActivity` | 录屏；用于判定缺口 4。设备上无法到达该入口则标 N/A |
| V7 | V1–V3 | 任意 multi-task / document 入口 | 不存在此类入口 | 记录 **N/A — 无 cross-task 触发点**（Q2），不得无声跳过 |
| V8 | V1–V3 | 打开任一基于 `GimiSheetBase` 的 sheet，缓慢侧滑返回 | 判定 `sheetGesturesEnabled = false` 下预测式返回动画是否仍播放 | 录屏；记录缺口 2 的结论，不改代码（Q4/Q10） |
| V9 | 仅 Android 16+ | 进入 `OVERLAY` 助手模式，按返回 | 面板收起 | **P0。** 若收不起，Q11 允许做一处不改动交互模型的最小兼容修复；否则不改代码 |
| V10 | V1–V3 | 在实时语音页按返回；另一次点击页内「挂断」按钮 | 返回结束通话并回到文字聊天；挂断只结束会话、语音页留在待启动状态 | Q9 的行为检查，确认 spec 已对齐 |
| V11 | 仅 Android 14 | 在做 V1 前确认开发者选项确已开启 | Predictive back animations 开关为 ON | 截图；未开启则 V1 结果无效 |

Android 13（API 33）刻意缺席：`minSdk = 34`。

## 变更记录

- 2026-11-11 — 依据源码、AndroidX 1.0.0 / 1.4.0 / 1.13.0 官方 sources jar、
  Compose BOM POM 与平台文档完成基线盘点，锁定 Q1–Q12。
  `spec/en/voice/pipeline.md` 与 `spec/zh/voice/pipeline.md` 已对齐实时语音返回行为；
  `AGENTS.md` 增加索引行；新建本文及中文 companion。**未改动任何运行时源码。**
