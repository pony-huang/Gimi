# 底部抽屉

[English](../../en/ui/bottom-sheet.md) · 简体中文

> `:core:designsystem` 里的公共底部抽屉组件：高度模型、手势规则、插槽骨架，
> 以及存量抽屉的迁移清单。

## 为什么要有公共组件

应用里一共有八个底部抽屉，每个都自己长了一套高度规则：写死的九成屏高、在两个文件里
各抄一份的 380dp 列表上限、92% 的页面上限、560dp 上限、三分之二屏高，还有三个完全
没有上限的。最后那三个会长成全屏；而半展开态还会把抽屉的操作条留在可视区之外——
这个坑我们已经踩过一次，排查了一整天。

`GimiSheetBase` 用一套规则取代了这些散落的高度约束：抽屉是模态面板，高度是显式的，
内容永远撑不高它，唯一的改高度入口是顶部横杆。

## 组件

| 组件 | 职责 |
|---|---|
| `GimiSheetBase` | 模态外壳、拖动横杆、连续高度、插槽骨架、inset |
| `GimiSheet` | 派生：标题行 + 主体 + 可选底部操作区 |
| `GimiSearchSheet` | 派生：可选标题行 + 搜索行 + 主体 + 可选底部操作区 |
| `GimiSheetOptionRow` | 抽屉内的互斥选项行（从旧的 `GimiBottomSheet` 挪出来的） |

抽屉内的开关行统一用 `PreferenceSwitchRow`（`core/designsystem/.../ui/preference`），
与设置页共用同一组件与几何，整行承担开关交互。

三个组件都在 `core/designsystem/.../ui/components`。`GimiSheetBase` 负责布局，
两个派生组件只决定标题、搜索、底部各放什么。

## 高度模型

`SheetHeight` 要么是窗口高度的比例，要么是绝对值：

```kotlin
GimiSheet(
    onDismissRequest = onDismiss,
    title = "播放音色",
    initialHeight = SheetHeight.Fraction(0.5f),
    maxHeight = SheetHeight.Fraction(0.9f),
) { /* 主体 */ }
```

- **抽屉贴着内容长。** `initialHeight` 是主体的高度上限，不是强行拉出来的高度：只有三个
  选项时抽屉就只长到那三个选项的高度；列表很长时抽屉长到上限，列表在内部滚动。
  短列表下面一大片空白正是这里要消掉的。
- **上限是硬约束。** `GimiSheetBase` 会把上限压到窗口的九成，调用方开不出全屏抽屉。
  这条约束在 `resolveSheetHeights` 里，由 `GimiSheetHeightTest` 锁住。
- **先给外壳留位置。** 横杆、标题行与底部操作区的高度先测出来并从上限里扣掉，
  主体才排版——否则主体会把底部操作区挤成 0（实测高度随之归零，形成死循环）。
  首帧还没测到时用 `ReservedChromeHeight` 临时收住主体，测到后立刻按实测值计算；
  预留值不会一直扣在上限上。
- **高度连续、没有档位。** 横杆可以停在零与上限之间的任意位置，松手就留在那里；
  把主体拖到零即关闭；下次打开仍然从初始高度开始。
- **抽屉可以按页面自己指定高度上限**（`height = …`）。外部指定的值优先于初始高度，
  并且下一次外部变更会清掉用户手动拖出来的结果。

## 手势规则

- **只有拖动横杆能改高度。** 横杆是抽屉顶部 32dp 高的一条带子，手指落在哪都能拖。
- **内容滚动永远不带动抽屉。** `GimiSheetBase` 关掉了 Material3 自带的拖拽与嵌套滚动
  （`sheetGesturesEnabled = false`），抽屉里的列表滚的是列表，不会出现"一滚动抽屉就
  跟着变形"。
- **键盘弹出也不带动抽屉。** IME 属于 `safeDrawing`，内容会被顶到键盘上方，底部
  的输入行仍然可见。
- 关闭方式：点遮罩、按返回键，或者把横杆一路往下拖到主体归零。

## 插槽骨架

骨架与会话抽屉一致，看起来是同一套东西：

```text
┌─────────────────────────────┐
│           ═══════           │  拖动横杆（32dp 触控带）
│ ✕        标题          ⋯    │  标题行：可选左侧图标、居中标题、右侧操作位
│ 🔍  搜索             取消   │  搜索行（仅搜索型抽屉）
├─────────────────────────────┤
│                             │
│  主体——自己滚动             │  heightIn(max)，贴内容高度
│                             │
├─────────────────────────────┤
│  底部操作区（输入、操作）    │  固定在底部
└─────────────────────────────┘
```

标题行与会话抽屉一样居中：标题相对整行宽度居中，左侧是可选图标（返回），右侧是操作位，
可以用 `GimiSheetTextAction` 或 `GimiSheetIconAction`；关闭一律放右侧，于是抽屉读起来是
"返回在左、关闭或操作在右"。

行的水平留白取 `GimiListDefaults.HorizontalPadding`（16dp），与抽屉内列表卡片的边距一致，
标题行与下方主体落在同一条竖直栅格线上。标题两侧按较宽一侧槽位的实测宽度做对称安全区：
长标题既不会压到按钮，只有一侧有槽位时标题也不会因此偏移。

inset 由组件负责：抽屉吃掉 `safeDrawing`，调用方不要再自己加
`navigationBarsPadding()` 或 `imePadding()`。

## 存量抽屉

| 抽屉 | 位置 | 初始／最大高度 |
|---|---|---|
| 播放音色 | `feature/modelsettings` `VoicePickerSheet` | 九成屏高上限，横杆可连续缩小，拖到底关闭 |
| Realtime 音色 | `feature/modelsettings` | 90% |
| 添加到对话 | `feature/chat` `ChatAddToChatSheet` | 首页 45%，列表页 90% |
| 权限模式 | `feature/chat` `ChatPermissionModeSheet` | 28% |
| 聊天模型选择 | `feature/chat` `ChatModelPickerSheet` | 60%／90% |
| Realtime 预设／详情 | `feature/chat` `RealtimeVoiceScreen` | 40–50%／90% |
| A2UI 详情抽屉 | `feature/chat` `ChatA2uiContainers` | 65%／90% |

`feature/assistant` 的助手面板与折叠胶囊**没有**迁移：它们是 `Popup` 宿主而不是模态抽屉，
自带拖拽把手，语义不同。

## 下次改动前先看

- 抽屉永远不能到全屏高度。要改 `GimiSheetDefaults.MaxHeightFraction` 得说得出理由，
  并让 `GimiSheetHeightTest` 继续绿。
- 不要在 `GimiSheetDragHandle` 之外给横杆区域加 `Modifier.clickable`；拖拽手势在那里。
- 需要滚动的抽屉，把可滚动内容放在主体槽里。主体用 `heightIn(max)` 给出上限：
  短列表要用 `Column + verticalScroll` 贴内容长，`LazyColumn` 在有界高度下会把主体撑满；
  内容超过上限时主体自己滚动，调用方不用再写一层高度上限。
- `GimiSheetController.hide { }` 用来在退场动画结束后再做事——例如在抽屉消失后再提交选择。

## 相关

- [品牌视觉](brand-styling.md) —— 公共画布色与圆角处理。
- [预览优先的 UI 设计评审](preview-first-design-review.md) —— 抽屉改动如何先做预览验证。
