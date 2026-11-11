package github.ponyhuang.gimi.ui.theme

import androidx.compose.ui.graphics.Color

// ── 品牌常量 ────────────────────────────────────────────────
// BrandBlue 是规范里的品牌标准蓝，只用于焦点环、品牌图形与非文字强调；
// 普通字号的可访问主操作色另用 BrandPrimaryLight / BrandPrimaryDark。
val BrandBlue = Color(0xFF4D6BFE)
val BrandPrimaryLight = Color(0xFF3D5BDB)
val BrandPrimaryDark = Color(0xFFAEBBFF)

/**
 * 强描边：输入框与控件边界需要达到 3:1 非文字对比度时使用。
 *
 * M3 的 `ColorScheme` 没有对应的强描边角色，普通边界仍走 `outline`；
 * 该色值是规范核心色板表的一部分，供后续强化边界时取用。
 */
val BrandOutlineStrong = Color(0xFF737373)

// 主色容器：选中项、轻量强调与标签底色，按主题成对使用。
val BrandBlueContainer = Color(0xFFE3E8FF)
val BrandBlueDarkContainer = Color(0xFF27366D)

// ── 中性色板（浅色）─────────────────────────────────────────
val NeutralLightBackground = Color(0xFFFFFFFF)
val NeutralLightSurface = Color(0xFFF7F7F7)
val NeutralLightSurfaceVariant = Color(0xFFEDEDED)
val NeutralLightText = Color(0xFF1F1F1F)
val NeutralLightMutedText = Color(0xFF666666)
val NeutralLightOutline = Color(0xFFD4D4D4)

// ── 中性色板（深色）─────────────────────────────────────────
val NeutralDarkBackground = Color(0xFF000000)
val NeutralDarkSurface = Color(0xFF141414)
val NeutralDarkSurfaceVariant = Color(0xFF242424)
val NeutralDarkText = Color(0xFFF5F5F5)
val NeutralDarkMutedText = Color(0xFFA3A3A3)
val NeutralDarkOutline = Color(0xFF3D3D3D)

// ── 中性表面色阶 ────────────────────────────────────────────
// 固定这组灰阶，避免 Material 默认紫色渗入到底色、卡片与底部面板。
val NeutralLightSurfaceDim = Color(0xFFE4E4E4)
val NeutralLightSurfaceBright = Color(0xFFFFFFFF)
val NeutralLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val NeutralLightSurfaceContainerLow = Color(0xFFFAFAFA)
val NeutralLightSurfaceContainer = Color(0xFFF5F5F5)
val NeutralLightSurfaceContainerHigh = Color(0xFFF0F0F0)
val NeutralLightSurfaceContainerHighest = Color(0xFFE9E9E9)
val NeutralDarkSurfaceDim = Color(0xFF0A0A0A)
val NeutralDarkSurfaceBright = Color(0xFF383838)
val NeutralDarkSurfaceContainerLowest = Color(0xFF000000)
val NeutralDarkSurfaceContainerLow = Color(0xFF0F0F0F)
val NeutralDarkSurfaceContainer = Color(0xFF141414)
val NeutralDarkSurfaceContainerHigh = Color(0xFF1A1A1A)
val NeutralDarkSurfaceContainerHighest = Color(0xFF242424)

// ── Preference (settings) canvas & group card ────────────────
// One UI 风格设置页配色：浅色为浅灰画布 + 纯白分组卡片；深色画布纯黑，
// 卡片比画布略亮一档，保证分组层次在暗色下仍可辨。
val PreferenceCanvasLight = Color(0xFFF5F5F5)
val PreferenceCanvasDark = NeutralDarkBackground
val PreferenceGroupCardLight = Color(0xFFFFFFFF)
val PreferenceGroupCardDark = Color(0xFF141414)

// ── 装饰性插画色 ────────────────────────────────────────────
// 非语义角色：只用于插画自身的装饰配色，不参与品牌强调层级。
val IllustrationSunAmber = Color(0xFFFABD1B)

// ── Red (error) ──────────────────────────────────────────────
val Red10 = Color(0xFF410001)
val Red20 = Color(0xFF680003)
val Red30 = Color(0xFF930006)
val Red40 = Color(0xFFBA1B1B)
val Red80 = Color(0xFFFFB4A9)
val Red90 = Color(0xFFFFDAD4)
