package github.ponyhuang.asssistantai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val DeepSeekDarkColorScheme = darkColorScheme(
    primary = DeepSeekBlue,
    onPrimary = Color.White,
    primaryContainer = DeepSeekBlueDarkContainer,
    onPrimaryContainer = Color(0xFFE3E8FF),
    inversePrimary = Color(0xFFAEBBFF),
    secondary = ChatGptDarkMutedText,
    onSecondary = ChatGptDarkBackground,
    secondaryContainer = ChatGptDarkSurfaceVariant,
    onSecondaryContainer = ChatGptDarkText,
    tertiary = DeepSeekBlue,
    onTertiary = Color.White,
    tertiaryContainer = DeepSeekBlueDarkContainer,
    onTertiaryContainer = Color(0xFFE3E8FF),
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = ChatGptDarkBackground,
    onBackground = ChatGptDarkText,
    surface = ChatGptDarkSurface,
    onSurface = ChatGptDarkText,
    inverseSurface = ChatGptLightSurface,
    inverseOnSurface = ChatGptLightText,
    surfaceVariant = ChatGptDarkSurfaceVariant,
    onSurfaceVariant = ChatGptDarkMutedText,
    outline = Color(0xFF3C3C41),
    surfaceDim = ChatGptDarkSurfaceDim,
    surfaceBright = ChatGptDarkSurfaceBright,
    surfaceContainerLowest = ChatGptDarkSurfaceContainerLowest,
    surfaceContainerLow = ChatGptDarkSurfaceContainerLow,
    surfaceContainer = ChatGptDarkSurfaceContainer,
    surfaceContainerHigh = ChatGptDarkSurfaceContainerHigh,
    surfaceContainerHighest = ChatGptDarkSurfaceContainerHighest,
)

val DeepSeekLightColorScheme = lightColorScheme(
    primary = DeepSeekBlue,
    onPrimary = Color.White,
    primaryContainer = DeepSeekBlueContainer,
    onPrimaryContainer = Color(0xFF10205C),
    inversePrimary = Color(0xFFAEBBFF),
    secondary = ChatGptLightMutedText,
    onSecondary = Color.White,
    secondaryContainer = ChatGptLightSurfaceVariant,
    onSecondaryContainer = ChatGptLightText,
    tertiary = DeepSeekBlue,
    onTertiary = Color.White,
    tertiaryContainer = DeepSeekBlueContainer,
    onTertiaryContainer = Color(0xFF10205C),
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = ChatGptLightBackground,
    onBackground = ChatGptLightText,
    surface = ChatGptLightSurface,
    onSurface = ChatGptLightText,
    inverseSurface = ChatGptDarkSurface,
    inverseOnSurface = ChatGptDarkText,
    surfaceVariant = ChatGptLightSurfaceVariant,
    onSurfaceVariant = ChatGptLightMutedText,
    outline = Color(0xFFD4D4D4),
    surfaceDim = ChatGptLightSurfaceDim,
    surfaceBright = ChatGptLightSurfaceBright,
    surfaceContainerLowest = ChatGptLightSurfaceContainerLowest,
    surfaceContainerLow = ChatGptLightSurfaceContainerLow,
    surfaceContainer = ChatGptLightSurfaceContainer,
    surfaceContainerHigh = ChatGptLightSurfaceContainerHigh,
    surfaceContainerHighest = ChatGptLightSurfaceContainerHighest,
)

@Composable
fun AsssistantaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DeepSeekDarkColorScheme else DeepSeekLightColorScheme

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
