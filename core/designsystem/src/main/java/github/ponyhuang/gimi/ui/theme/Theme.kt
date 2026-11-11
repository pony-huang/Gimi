package github.ponyhuang.gimi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * 深色方案：纯黑画布 + 中性灰分层，品牌蓝只做强调。
 *
 * `primary` 用可访问的浅蓝（#AEBBFF），与 `onPrimary`（#10205C）成对使用；
 * [BrandBlue] 只留给焦点环与品牌图形，不承担普通字号的主操作色。
 */
val GimiDarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color(0xFF10205C),
    primaryContainer = BrandBlueDarkContainer,
    onPrimaryContainer = Color(0xFFE3E8FF),
    inversePrimary = BrandPrimaryLight,
    secondary = NeutralDarkMutedText,
    onSecondary = NeutralDarkBackground,
    secondaryContainer = NeutralDarkSurfaceVariant,
    onSecondaryContainer = NeutralDarkText,
    tertiary = BrandPrimaryDark,
    onTertiary = Color(0xFF10205C),
    tertiaryContainer = BrandBlueDarkContainer,
    onTertiaryContainer = Color(0xFFE3E8FF),
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = NeutralDarkBackground,
    onBackground = NeutralDarkText,
    surface = NeutralDarkSurface,
    onSurface = NeutralDarkText,
    inverseSurface = NeutralLightSurface,
    inverseOnSurface = NeutralLightText,
    surfaceVariant = NeutralDarkSurfaceVariant,
    onSurfaceVariant = NeutralDarkMutedText,
    outline = NeutralDarkOutline,
    // M3 默认的 outlineVariant 取自基线紫色色板，这里换成中性灰，避免紫色渗入分隔线。
    outlineVariant = Color(0xFF383838),
    // M3 默认 surfaceTint 等于 primary，抬升层会泛蓝；改用中性表面色保持无彩色层级。
    surfaceTint = NeutralDarkSurface,
    surfaceDim = NeutralDarkSurfaceDim,
    surfaceBright = NeutralDarkSurfaceBright,
    surfaceContainerLowest = NeutralDarkSurfaceContainerLowest,
    surfaceContainerLow = NeutralDarkSurfaceContainerLow,
    surfaceContainer = NeutralDarkSurfaceContainer,
    surfaceContainerHigh = NeutralDarkSurfaceContainerHigh,
    surfaceContainerHighest = NeutralDarkSurfaceContainerHighest,
)

/**
 * 浅色方案：纯白画布 + 中性灰分层，主操作色用可访问的深蓝（#3D5BDB）。
 */
val GimiLightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = Color(0xFF10205C),
    inversePrimary = BrandPrimaryDark,
    secondary = NeutralLightMutedText,
    onSecondary = Color.White,
    secondaryContainer = NeutralLightSurfaceVariant,
    onSecondaryContainer = NeutralLightText,
    tertiary = BrandPrimaryLight,
    onTertiary = Color.White,
    tertiaryContainer = BrandBlueContainer,
    onTertiaryContainer = Color(0xFF10205C),
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = NeutralLightBackground,
    onBackground = NeutralLightText,
    surface = NeutralLightSurface,
    onSurface = NeutralLightText,
    inverseSurface = NeutralDarkSurface,
    inverseOnSurface = NeutralDarkText,
    surfaceVariant = NeutralLightSurfaceVariant,
    onSurfaceVariant = NeutralLightMutedText,
    outline = NeutralLightOutline,
    outlineVariant = Color(0xFFE5E5E6),
    surfaceTint = NeutralLightSurface,
    surfaceDim = NeutralLightSurfaceDim,
    surfaceBright = NeutralLightSurfaceBright,
    surfaceContainerLowest = NeutralLightSurfaceContainerLowest,
    surfaceContainerLow = NeutralLightSurfaceContainerLow,
    surfaceContainer = NeutralLightSurfaceContainer,
    surfaceContainerHigh = NeutralLightSurfaceContainerHigh,
    surfaceContainerHighest = NeutralLightSurfaceContainerHighest,
)

@Composable
fun GimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GimiDarkColorScheme else GimiLightColorScheme

    // 用户气泡配色独立于品牌主题，确保主题色变化不会降低消息角色辨识度。
    val userBubbleColors = if (darkTheme) DarkUserBubbleColors else LightUserBubbleColors

    CompositionLocalProvider(LocalUserBubbleColors provides userBubbleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
