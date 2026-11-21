package github.ponyhuang.gimi.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 聊天气泡 —— 用户角色专用配色。
 *
 * 对齐 M3 黑白墨灰（Monochrome Ink-Gray）体系的 `surfaceContainerHighest` 角色：
 *
 * 1. **层次清晰**：用户气泡采用高阶墨灰容器（浅色 `#E0E0E5` / 深色 `#2E2E33`），
 *    与无气泡背景的助手长文本回复自然拉开视觉层级。
 * 2. **可读性**：搭配高对比深墨字/宣白字，长时间阅读不刺眼、不疲倦。
 *
 * 通过 [LocalUserBubbleColors] 在 [GimiTheme] 中按 dark/light 模式注入；
 * 调用方直接读取 [LocalUserBubbleColors.current]，无需中间 wrapper。
 *
 * @property container 气泡容器背景色。
 * @property onContainer 气泡内文字与图标前景色。
 */
data class UserBubbleColors(
    val container: Color,
    val onContainer: Color,
)

/** 浅色模式 —— 淡墨灰容器（`surfaceContainerHighest`），保留文字高可读性。 */
val LightUserBubbleColors = UserBubbleColors(
    container = NeutralLightSurfaceContainerHighest,
    onContainer = NeutralLightText,
)

/** 深色模式 —— 焦墨灰容器（`surfaceContainerHighest`），和徽墨黑聊天背景形成柔和层级。 */
val DarkUserBubbleColors = UserBubbleColors(
    container = NeutralDarkSurfaceContainerHighest,
    onContainer = NeutralDarkText,
)

/**
 * 当前主题下用户气泡的配色。
 *
 * 未通过 [GimiTheme] 包裹时回退到 [LightUserBubbleColors]，
 * 这样 Preview / 单测场景不会拿到 `Color.Unspecified` 导致渲染失败。
 */
val LocalUserBubbleColors = staticCompositionLocalOf { LightUserBubbleColors }
