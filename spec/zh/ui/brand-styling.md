简体中文 · [English](../../en/ui/brand-styling.md)

# Gimi 品牌造型：M3 黑白墨灰（Monochrome Ink-Gray）主题

读者：设计 Gimi UI、预览、插画、示意图及其他品牌视觉产物的代理与开发者。

> 本版是目标品牌与主题规范。核心色板在 `core/designsystem` 生效：`Color.kt` 提供黑白墨灰常量，`Shape.kt` 提供 `GimiShapes`，`Type.kt` 提供完整的 M3 `Typography`，`Theme.kt` 的两套 `ColorScheme` 按核心色板表生效，`UserBubbleTheme` 的用户气泡色按本文校准；当前可执行行为以 `GimiTheme` 的生效赋值为准。新增界面以本文表格为准，出现差异时显式更新主题实现，不得在 feature 内硬编码十六进制颜色来“对齐设计稿”。界面改动按[预览优先的 UI 设计评审](preview-first-design-review.md)维护浅色、深色和交互状态预览。

## 适用范围与视觉方向

采用 Material Design 3 **黑白墨灰（Monochrome Ink-Gray）** 视觉语言：以宣纸白与徽墨黑为底，构建 5 级墨灰 `surfaceContainer` 容器色阶，主操作色在浅色下使用浓墨黑（`#18181B`）、深色下使用宣纸白（`#F4F4F6`），与 Gimi 昼夜反转的扁平黑白品牌图标形成完全统一的视觉语言。对话内容始终是视觉中心；通过明度、M3 容器色阶、字重、间距、圆角和图标建立层级，不依赖额外的高饱和彩色装饰。

本方案仅定义 Gimi 自身的产品视觉语言，不构成与任何第三方品牌的联名、隶属或官方授权声明。保留 Gimi 名称、产品标志和既有组件几何，不以第三方服务名称或标识替代 Gimi 的产品身份。

设计原则：

1. **宣白与徽墨为底**：浅色主背景采用防眩光宣纸白（`#FAFAFA`），深色主背景采用防 OLED 拖影的徽墨黑（`#0D0D0F`）；卡片、面板和消息气泡按 M3 5 级墨灰容器色阶自然分层。
2. **黑白墨灰主强调**：主按钮、激活开关、模式滑块与选中对勾在浅色下用浓墨黑（`#18181B`）、深色下用宣纸白（`#F4F4F6`）；选中项容器使用淡墨灰（`#E4E4E7` 浅色）与焦墨灰（`#2C2C31` 深色）。
3. **内容优先**：长篇助手回复直接铺在开放阅读画布上；用户消息气泡使用 `surfaceContainerHighest`（浅色 `#E0E0E5` / 深色 `#2E2E33`）。
4. **语义独立**：保留标准 M3 错误语义色（浅色 `#BA1A1A` / 深色 `#FFB4AB`）及第三方提供商原始品牌色；状态同时通过文字、图标或形状表达。
5. **可读性优先**：交互描边（`outline`：浅色 `#71717A` / 深色 `#8A8A93`）对比度均大于 4.8:1，满足 M3 3:1 非文字对比度要求；装饰性分隔线使用 `outlineVariant`（浅色 `#D4D4D8` / 深色 `#2E2E33`）。

## 依据与权威来源

### 视觉参数

- Gimi 自有图标采用昼夜反转的扁平单色方案：白天／浅色模式使用 `#050505` 底和 `#FFFFFF` 标志；夜间／深色模式使用 `#FFFFFF` 底和 `#3C3C43` 标志。保持现有图形、比例、透明度与净空，不添加渐变或阴影。
- README 使用自适应透明底标志素材，通过 `prefers-color-scheme` 的 `<picture>` 元素选择：白天／浅色模式用 `doc/icon-light.png`，夜间／深色模式用 `doc/icon-dark.png`，`doc/icon.webp` 保留为原始白天版本源图。
- `BrandBlue = #4D6BFE` 仅保留给实时语音粒子球（`VoiceOrb`）播报态的微冷调点缀，不作为常规 UI 控件主色。

### 源码位置

以下路径均相对仓库根目录：

- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Color.kt`：调色板常量。
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Shape.kt`：`GimiShapes` M3 圆角令牌定义。
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Theme.kt`：`GimiLightColorScheme`、`GimiDarkColorScheme` 与 `GimiTheme` 的生效角色映射。
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Type.kt`：完整的 M3 排版字阶配置。
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/UserBubbleTheme.kt`：用户消息专用墨灰角色。
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/components/` 及同级 `ui/preference/`：统一的 M3 开关、Outlined 输入框、胶囊搜索框、抽屉与分组卡片。

## 核心色板与主题角色

下表是实现与外部视觉产物的统一目标。应用内不得复制这些字面量到页面或组件，应消费语义角色。

| 语义角色／常量 | 浅色 | 深色 | 使用规则 |
|---|---|---|---|
| 主操作 `primary`（`BrandPrimaryLight` / `BrandPrimaryDark`） | `#18181B` | `#F4F4F6` | 填充按钮、激活开关轨道、模式滑块、选中对勾、聚焦描边 |
| 主操作上的内容 `onPrimary` | `#FFFFFF` | `#111113` | 必须与 `primary` 成对使用 |
| 主色容器 `primaryContainer` | `#E4E4E7` | `#2C2C31` | 抽屉/弹窗选中行、Segmented 激活段底色、主色轻量容器 |
| 主色容器上的内容 `onPrimaryContainer` | `#111113` | `#F4F4F6` | 与同主题 `primaryContainer` 成对使用 |
| 次级色 `secondary`（`InkSecondary*`） | `#52525B` | `#A1A1AA` | 次级操作与筛选控件 |
| 次级色上的内容 `onSecondary` | `#FFFFFF` | `#18181B` | 与 `secondary` 成对使用 |
| 次级容器 `secondaryContainer` | `#ECECEF` | `#252529` | `FilledTonalButton`、设置列表图标圆底、信息横幅、模型胶囊 |
| 次级容器上的内容 `onSecondaryContainer` | `#18181B` | `#E4E4E7` | 与 `secondaryContainer` 成对使用 |
| 第三色 `tertiary`（`InkTertiary*`） | `#3F4850` | `#B8C2CC` | 石墨青灰微冷调，用于辅助徽章与标签 |
| 第三色容器 `tertiaryContainer` | `#E2E7EC` | `#303840` | 辅助徽章容器 |
| 第三色容器上的内容 `onTertiaryContainer` | `#141C22` | `#E2E7EC` | 与 `tertiaryContainer` 成对使用 |
| 主背景与表面 `background` / `surface` | `#FAFAFA` | `#0D0D0F` | 默认页面主画布 |
| 表面变体 `surfaceVariant` | `#E4E4E7` | `#27272A` | 次级容器与滑轨底色 |
| 主要文字 `onSurface` / `onBackground` | `#111113` | `#F4F4F6` | 标题、正文、主要图标 |
| 次要文字 `onSurfaceVariant` | `#52525B` | `#A1A1AA` | 辅助说明、时间、次级图标 |
| 交互描边 `outline` | `#71717A` | `#8A8A93` | 满足 3:1 非文字对比度，用于输入框、描边按钮、分段按钮、未选中开关边框 |
| 装饰描边 `outlineVariant` | `#D4D4D8` | `#2E2E33` | 列表内缩分隔线与卡片细描边 |
| 错误 `error` / `onError` | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | 仅表达错误语义，并配错误图标或文字 |
| 错误容器 `errorContainer` / `onErrorContainer` | `#FFDAD6` / `#410002` | `#93000A` / `#FFDAD6` | 错误横幅与错误状态容器 |
| 设置页画布（`preferenceCanvasColor()`） | `#F4F4F5` | `#0D0D0F` | 设置页与抽屉画布（浅色 `surfaceContainerLow` / 深色 `surface`） |
| 设置分组卡片（`preferenceGroupCardColor()`） | `#FFFFFF` | `#161618` | 比设置画布高一档明度（浅色 `surfaceContainerLowest` / 深色 `surfaceContainerLow`） |
| 用户消息容器 | `#E0E0E5` | `#2E2E33` | 对齐 `surfaceContainerHighest`，通过 `LocalUserBubbleColors` 注入 |
| 用户消息文字 | `#111113` | `#F4F4F6` | 通过 `LocalUserBubbleColors` 配对 |

### M3 5 级中性表面色阶

| 角色 | 浅色 | 深色 |
|---|---|---|
| `surfaceDim` | `#DADADD` | `#0D0D0F` |
| `surfaceBright` | `#FFFFFF` | `#34343A` |
| `surfaceContainerLowest` | `#FFFFFF` | `#08080A` |
| `surfaceContainerLow` | `#F4F4F5` | `#161618` |
| `surfaceContainer` | `#EFEFF1` | `#1C1C1F` |
| `surfaceContainerHigh` | `#E8E8EC` | `#242428` |
| `surfaceContainerHighest` | `#E0E0E5` | `#2E2E33` |

## 字体、形状与组件规范

- **排版（Typography）**：`Type.kt` 统一定义 `headlineSmall`（24sp SemiBold）、`titleLarge`（20sp SemiBold）、`titleMedium`（16sp Medium）、`titleSmall`（14sp SemiBold）、`bodyLarge`（16sp Normal）、`bodyMedium`（14sp Normal）、`bodySmall`（12sp Normal）、`labelLarge`（14sp SemiBold）、`labelMedium`（12sp Medium）与 `labelSmall`（11sp Medium）。
- **圆角（Shapes）**：`Shape.kt` 定义 `GimiShapes`（`extraSmall = 4.dp`、`small = 8.dp`、`medium = 12.dp`、`large = 16.dp`、`extraLarge = 28.dp`）。
  - `PreferenceGroupCard`、`GimiPickerItemRow`、`PluginRowCard` 与用户消息气泡统一使用 `MaterialTheme.shapes.large`（16.dp）。
  - `PreferenceTextField` 统一采用 M3 `OutlinedTextField` 与 `MaterialTheme.shapes.medium`（12.dp），未聚焦描边取 `outline`，聚焦描边取 `primary`。
  - `GimiSearchField` 采用 `CircleShape` 与 `surfaceContainerHigh` 填充底色。
  - `GimiSheetBase` 与 `ChatComposer` 采用 `28.dp`（`extraLarge`）圆角。
- **开关（`GimiSwitch`）**：在 48dp 触控盒内绘制 44×26dp M3 轨道；未选中态为 `surfaceContainerHighest` 底 + `1.5.dp outline` 描边 + `14.dp outline` 拇指；选中态为 `primary` 底 + `18.dp onPrimary` 拇指（带尺寸缩放过渡）。
- **主题判定**：组件内禁止通过 `MaterialTheme.colorScheme.background.luminance() < 0.5f` 手工分支；需要深浅主题判断时统一读取 `LocalGimiDarkTheme.current`。

## 标志与第三方品牌边界

- 保留 Gimi 名称和现有标志图形（`doc/icon-light.png` / `doc/icon-dark.png` 及各密度启动图标资产）。
- 第三方服务标识直接放在透明容器上：黑色单色标识（OpenAI / Anthropic / XiaomiMiMo）在 `res/drawable/` 中提供显式命名的 `*_night` 反白版，由 `LLMModelServiceIcon` 读取 `LocalGimiDarkTheme` 选择；Kimi 用品牌彩色版，彩色标识两种主题共用品牌原色资源。插件商店图标遵循同一代码选择约定。应用内厂商/插件图标禁止使用 `res/drawable-night/` 限定符，深色变体一律由代码按应用主题选择。
