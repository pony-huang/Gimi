package github.ponyhuang.asssistantai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 聊天气泡 —— 用户角色专用配色。
 *
 * 故意独立于 [androidx.compose.material3.MaterialTheme.colorScheme]，原因：
 *
 * 1. **稳定性**：动态色（Material You，Android 12+）下 `primary` 会随壁纸变化，
 *    而用户希望气泡"始终温暖"，不希望出现冷蓝色或暗紫色的"变色"气泡。
 * 2. **角色语义**：用户气泡代表"我自己"，用一组与品牌色不同的暖色能让对话
 *    一眼分辨说话方，无需看对齐方向。
 * 3. **可读性**：暖杏/焦糖的明度经过挑选 —— 不刺眼、不发灰，长时间对话不疲倦。
 *
 * 通过 [LocalUserBubbleColors] 在 [AsssistantaiTheme] 中按 dark/light 模式注入，
 * 调用方用 `UserBubbleTheme.colors.container` / `.onContainer` 取色。
 */
data class UserBubbleColors(
    val container: Color,
    val onContainer: Color,
)

/** 浅色模式 —— 低对比浅灰，保留文字可读性而不过分抢眼。 */
val LightUserBubbleColors = UserBubbleColors(
    container = Color(0xFFE9E9E9),
    onContainer = ChatGptLightText,
)

/** 深色模式 —— 深灰表面，和聊天背景形成柔和层级。 */
val DarkUserBubbleColors = UserBubbleColors(
    container = Color(0xFF303030),
    onContainer = ChatGptDarkText,
)

/**
 * 当前主题下用户气泡的配色。
 *
 * 未通过 [AsssistantaiTheme] 包裹时回退到 [LightUserBubbleColors]，
 * 这样 Preview / 单测场景不会拿到 `Color.Unspecified` 导致渲染失败。
 */
val LocalUserBubbleColors = staticCompositionLocalOf { LightUserBubbleColors }

/** 便捷取色入口。 */
object UserBubbleTheme {
    val colors: UserBubbleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalUserBubbleColors.current
}
