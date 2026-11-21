package github.ponyhuang.gimi.ui.theme

import androidx.compose.ui.graphics.Color

// ── M3 黑白墨灰（Monochrome Ink-Gray）核心色板 ──────────────────
// 主操作色（Primary）：浅色用浓墨黑（#18181B），深色用宣纸白（#F4F4F6）；
// 主色容器（PrimaryContainer）：浅色用淡墨灰（#E4E4E7），深色用焦墨灰（#2C2C31）。
val BrandPrimaryLight = Color(0xFF18181B)
val BrandOnPrimaryLight = Color(0xFFFFFFFF)
val BrandBlueContainer = Color(0xFFE4E4E7)
val BrandOnPrimaryContainerLight = Color(0xFF111113)

val BrandPrimaryDark = Color(0xFFF4F4F6)
val BrandOnPrimaryDark = Color(0xFF111113)
val BrandBlueDarkContainer = Color(0xFF2C2C31)
val BrandOnPrimaryContainerDark = Color(0xFFF4F4F6)

// 语音粒子球等特殊动效保留的微冷调点缀常量，不用于常规 UI 控件主色。
val BrandBlue = Color(0xFF4D6BFE)

// 次级色（Secondary · 砚灰）：用于 Tonal 按钮、设置列表图标底与次级筛选胶囊。
val InkSecondaryLight = Color(0xFF52525B)
val InkOnSecondaryLight = Color(0xFFFFFFFF)
val InkSecondaryContainerLight = Color(0xFFECECEF)
val InkOnSecondaryContainerLight = Color(0xFF18181B)

val InkSecondaryDark = Color(0xFFA1A1AA)
val InkOnSecondaryDark = Color(0xFF18181B)
val InkSecondaryContainerDark = Color(0xFF252529)
val InkOnSecondaryContainerDark = Color(0xFFE4E4E7)

// 第三色（Tertiary · 石墨青灰）：用于辅助徽章与互补容器，保持水墨克制感。
val InkTertiaryLight = Color(0xFF3F4850)
val InkOnTertiaryLight = Color(0xFFFFFFFF)
val InkTertiaryContainerLight = Color(0xFFE2E7EC)
val InkOnTertiaryContainerLight = Color(0xFF141C22)

val InkTertiaryDark = Color(0xFFB8C2CC)
val InkOnTertiaryDark = Color(0xFF1F272E)
val InkTertiaryContainerDark = Color(0xFF303840)
val InkOnTertiaryContainerDark = Color(0xFFE2E7EC)

/**
 * 强描边：输入框与控件边界需要达到 3:1 非文字对比度时使用。
 */
val BrandOutlineStrong = Color(0xFF71717A)

// ── 中性色板（浅色 · 宣纸白与深墨字）─────────────────────────────
val NeutralLightBackground = Color(0xFFFAFAFA)
val NeutralLightSurface = Color(0xFFFAFAFA)
val NeutralLightSurfaceVariant = Color(0xFFE4E4E7)
val NeutralLightText = Color(0xFF111113)
val NeutralLightMutedText = Color(0xFF52525B)
val NeutralLightOutline = Color(0xFF71717A)
val NeutralLightOutlineVariant = Color(0xFFD4D4D8)

// ── 中性色板（深色 · 徽墨黑与宣白字）─────────────────────────────
val NeutralDarkBackground = Color(0xFF0D0D0F)
val NeutralDarkSurface = Color(0xFF0D0D0F)
val NeutralDarkSurfaceVariant = Color(0xFF27272A)
val NeutralDarkText = Color(0xFFF4F4F6)
val NeutralDarkMutedText = Color(0xFFA1A1AA)
val NeutralDarkOutline = Color(0xFF8A8A93)
val NeutralDarkOutlineVariant = Color(0xFF2E2E33)

// ── M3 5 级中性表面色阶（Surface Container Hierarchy）───────────
val NeutralLightSurfaceDim = Color(0xFFDADADD)
val NeutralLightSurfaceBright = Color(0xFFFFFFFF)
val NeutralLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val NeutralLightSurfaceContainerLow = Color(0xFFF4F4F5)
val NeutralLightSurfaceContainer = Color(0xFFEFEFF1)
val NeutralLightSurfaceContainerHigh = Color(0xFFE8E8EC)
val NeutralLightSurfaceContainerHighest = Color(0xFFE0E0E5)

val NeutralDarkSurfaceDim = Color(0xFF0D0D0F)
val NeutralDarkSurfaceBright = Color(0xFF34343A)
val NeutralDarkSurfaceContainerLowest = Color(0xFF08080A)
val NeutralDarkSurfaceContainerLow = Color(0xFF161618)
val NeutralDarkSurfaceContainer = Color(0xFF1C1C1F)
val NeutralDarkSurfaceContainerHigh = Color(0xFF242428)
val NeutralDarkSurfaceContainerHighest = Color(0xFF2E2E33)

// ── Preference (settings) canvas & group card ────────────────
// 对齐 M3 SurfaceContainer 层级：浅色为 surfaceContainerLow 画布 + surfaceContainerLowest 纯白卡片；
// 深色为 surface 画布 + surfaceContainerLow 卡片。
val PreferenceCanvasLight = NeutralLightSurfaceContainerLow
val PreferenceCanvasDark = NeutralDarkBackground
val PreferenceGroupCardLight = NeutralLightSurfaceContainerLowest
val PreferenceGroupCardDark = NeutralDarkSurfaceContainerLow

// ── 装饰性插画色 ────────────────────────────────────────────
// 非语义角色：只用于插画自身的装饰配色，不参与品牌强调层级。
val IllustrationSunAmber = Color(0xFFFABD1B)

// ── Red (error) ──────────────────────────────────────────────
val Red10 = Color(0xFF410002)
val Red20 = Color(0xFF690005)
val Red30 = Color(0xFF93000A)
val Red40 = Color(0xFFBA1A1A)
val Red80 = Color(0xFFFFB4AB)
val Red90 = Color(0xFFFFDAD6)
