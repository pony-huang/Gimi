package github.ponyhuang.gimi.ui.theme

import androidx.compose.ui.graphics.Color

// ── DeepSeek-inspired brand + neutral palette ────────────────
// The chat experience deliberately uses a stable neutral palette instead of
// Material You colours, so conversation surfaces remain recognisable across
// devices and wallpapers.
val DeepSeekBlue = Color(0xFF4D6BFE)
val DeepSeekBlueContainer = Color(0xFFE3E8FF)
val DeepSeekBlueDarkContainer = Color(0xFF27366D)
val ChatGptDarkBackground = Color(0xFF131315)
val ChatGptDarkSurface = Color(0xFF1B1B1E)
val ChatGptDarkSurfaceVariant = Color(0xFF26262A)
val ChatGptDarkText = Color(0xFFE6E6E6)
val ChatGptDarkMutedText = Color(0xFFA3A3A8)
val ChatGptLightBackground = Color(0xFFFFFFFF)
val ChatGptLightSurface = Color(0xFFF7F7F8)
val ChatGptLightSurfaceVariant = Color(0xFFEDEDED)
val ChatGptLightText = Color(0xFF1F1F1F)
val ChatGptLightMutedText = Color(0xFF6E6E6E)

// Neutral surface container ramp. Without these, M3 falls back to the purple-tinted
// baseline defaults and bottom sheets/cards drift away from the neutral theme.
val ChatGptLightSurfaceDim = Color(0xFFE4E4E5)
val ChatGptLightSurfaceBright = Color(0xFFFFFFFF)
val ChatGptLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val ChatGptLightSurfaceContainerLow = Color(0xFFFAFAFA)
val ChatGptLightSurfaceContainer = Color(0xFFF3F3F4)
val ChatGptLightSurfaceContainerHigh = Color(0xFFEDEDEE)
val ChatGptLightSurfaceContainerHighest = Color(0xFFE5E5E6)
val ChatGptDarkSurfaceDim = Color(0xFF0E0E10)
val ChatGptDarkSurfaceBright = Color(0xFF38383D)
val ChatGptDarkSurfaceContainerLowest = Color(0xFF0F0F12)
val ChatGptDarkSurfaceContainerLow = Color(0xFF17171A)
val ChatGptDarkSurfaceContainer = Color(0xFF1F1F23)
val ChatGptDarkSurfaceContainerHigh = Color(0xFF27272C)
val ChatGptDarkSurfaceContainerHighest = Color(0xFF303036)

// ── Preference (settings) canvas & group card ────────────────
// One UI 风格设置页配色：浅色为浅灰画布 + 纯白分组卡片；深色画布沿用全局底色，
// 卡片比画布略亮一档，保证分组层次在暗色下仍可辨。
val PreferenceCanvasLight = Color(0xFFF2F3F5)
val PreferenceCanvasDark = ChatGptDarkBackground
val PreferenceGroupCardLight = Color(0xFFFFFFFF)
val PreferenceGroupCardDark = Color(0xFF1E1E23)

// ── Legacy brand colours ─────────────────────────────────────
val Blue10 = Color(0xFF000F5E)
val Blue20 = Color(0xFF001E92)
val Blue30 = Color(0xFF002ECC)
val Blue40 = Color(0xFF1546F6)
val Blue80 = Color(0xFFB8C3FF)
val Blue90 = Color(0xFFDDE1FF)

// ── DarkBlue (secondary) ─────────────────────────────────────
val DarkBlue10 = Color(0xFF00036B)
val DarkBlue20 = Color(0xFF000BA6)
val DarkBlue30 = Color(0xFF1026D3)
val DarkBlue40 = Color(0xFF3648EA)
val DarkBlue80 = Color(0xFFBBC2FF)
val DarkBlue90 = Color(0xFFDEE0FF)

// ── Yellow (tertiary) ────────────────────────────────────────
val Yellow10 = Color(0xFF261900)
val Yellow20 = Color(0xFF402D00)
val Yellow30 = Color(0xFF5C4200)
val Yellow40 = Color(0xFF7A5900)
val Yellow80 = Color(0xFFFABD1B)
val Yellow90 = Color(0xFFFFDE9C)

// ── Red (error) ──────────────────────────────────────────────
val Red10 = Color(0xFF410001)
val Red20 = Color(0xFF680003)
val Red30 = Color(0xFF930006)
val Red40 = Color(0xFFBA1B1B)
val Red80 = Color(0xFFFFB4A9)
val Red90 = Color(0xFFFFDAD4)

// ── Grey (background / surface) ──────────────────────────────
val Grey10 = Color(0xFF191C1D)
val Grey20 = Color(0xFF2D3132)
val Grey80 = Color(0xFFC4C7C7)
val Grey90 = Color(0xFFE0E3E3)
val Grey95 = Color(0xFFEFF1F1)
val Grey99 = Color(0xFFFBFDFD)

// ── BlueGrey (surfaceVariant / outline) ──────────────────────
val BlueGrey30 = Color(0xFF45464F)
val BlueGrey50 = Color(0xFF767680)
val BlueGrey60 = Color(0xFF90909A)
val BlueGrey80 = Color(0xFFC6C5D0)
val BlueGrey90 = Color(0xFFE2E1EC)

// ── Warm Amber (user bubble) ─────────────────────────────────
// 低饱和度的暖琥珀 / 奶油杏，专用于用户聊天气泡，降低长时间阅读的视觉疲劳。
// 故意独立于 Brand primary —— 即便系统开启 Material You 动态色，气泡仍保持温暖。
val WarmAmber10 = Color(0xFF3D2200) // 最深底色（保留）
val WarmAmber20 = Color(0xFF5A3A1C) // Light 主题下的 onContainer：深咖
val WarmAmber30 = Color(0xFF7C4F1D) // 中等暗（保留）
val WarmAmber40 = Color(0xFFB5825A) // Dark 主题下的 container：柔和焦糖
val WarmAmber80 = Color(0xFFD9A06A) // 较亮琥珀（保留）
val WarmAmber90 = Color(0xFFFFE2C0) // Light 主题下的 container：暖奶油杏
val WarmAmber95 = Color(0xFFFFEDD2) // 浅暖米（保留）
