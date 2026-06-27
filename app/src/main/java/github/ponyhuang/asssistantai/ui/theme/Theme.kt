package github.ponyhuang.asssistantai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val JetchatDarkColorScheme = darkColorScheme(
    primary = ChatGptGreen,
    onPrimary = Color.White,
    primaryContainer = ChatGptGreenDark,
    onPrimaryContainer = Color.White,
    inversePrimary = ChatGptGreen,
    secondary = ChatGptDarkMutedText,
    onSecondary = ChatGptDarkBackground,
    secondaryContainer = ChatGptDarkSurfaceVariant,
    onSecondaryContainer = ChatGptDarkText,
    tertiary = ChatGptGreen,
    onTertiary = Color.White,
    tertiaryContainer = ChatGptDarkSurfaceVariant,
    onTertiaryContainer = ChatGptDarkText,
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
    outline = Color(0xFF555555),
)

val JetchatLightColorScheme = lightColorScheme(
    primary = ChatGptGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F5EB),
    onPrimaryContainer = Color(0xFF064E3B),
    inversePrimary = ChatGptGreen,
    secondary = ChatGptLightMutedText,
    onSecondary = Color.White,
    secondaryContainer = ChatGptLightSurfaceVariant,
    onSecondaryContainer = ChatGptLightText,
    tertiary = ChatGptGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F5EB),
    onTertiaryContainer = Color(0xFF064E3B),
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
)

@Composable
fun AsssistantaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JetchatDarkColorScheme else JetchatLightColorScheme

    // 用户气泡配色独立于 MaterialTheme colorScheme，确保在所有设备上
    // 都保留 ChatGPT 风格的绿色对话辨识度。
    val userBubbleColors = if (darkTheme) DarkUserBubbleColors else LightUserBubbleColors

    CompositionLocalProvider(LocalUserBubbleColors provides userBubbleColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
