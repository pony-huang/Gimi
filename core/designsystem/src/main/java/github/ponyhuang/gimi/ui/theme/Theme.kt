package github.ponyhuang.gimi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 深色方案（M3 黑白墨灰 · Dark）：
 * 徽墨黑画布（#0D0D0F）+ 5 级墨灰容器色阶，主操作色用宣纸白（#F4F4F6）与深墨字（#111113）成对。
 */
val GimiDarkColorScheme = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandBlueDarkContainer,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    inversePrimary = BrandPrimaryLight,
    secondary = InkSecondaryDark,
    onSecondary = InkOnSecondaryDark,
    secondaryContainer = InkSecondaryContainerDark,
    onSecondaryContainer = InkOnSecondaryContainerDark,
    tertiary = InkTertiaryDark,
    onTertiary = InkOnTertiaryDark,
    tertiaryContainer = InkTertiaryContainerDark,
    onTertiaryContainer = InkOnTertiaryContainerDark,
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
    outlineVariant = NeutralDarkOutlineVariant,
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
 * 浅色方案（M3 黑白墨灰 · Light）：
 * 宣纸白画布（#FAFAFA）+ 5 级墨灰容器色阶，主操作色用浓墨黑（#18181B）与纯白字（#FFFFFF）成对。
 */
val GimiLightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandBlueContainer,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
    inversePrimary = BrandPrimaryDark,
    secondary = InkSecondaryLight,
    onSecondary = InkOnSecondaryLight,
    secondaryContainer = InkSecondaryContainerLight,
    onSecondaryContainer = InkOnSecondaryContainerLight,
    tertiary = InkTertiaryLight,
    onTertiary = InkOnTertiaryLight,
    tertiaryContainer = InkTertiaryContainerLight,
    onTertiaryContainer = InkOnTertiaryContainerLight,
    error = Red40,
    onError = BrandOnPrimaryLight,
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
    outlineVariant = NeutralLightOutlineVariant,
    surfaceTint = NeutralLightSurface,
    surfaceDim = NeutralLightSurfaceDim,
    surfaceBright = NeutralLightSurfaceBright,
    surfaceContainerLowest = NeutralLightSurfaceContainerLowest,
    surfaceContainerLow = NeutralLightSurfaceContainerLow,
    surfaceContainer = NeutralLightSurfaceContainer,
    surfaceContainerHigh = NeutralLightSurfaceContainerHigh,
    surfaceContainerHighest = NeutralLightSurfaceContainerHighest,
)

/**
 * 应用内实际生效的深浅主题；用户可在设置里锁定，此时它不一定等于系统 `uiMode`。
 *
 * 需要按主题切换资源的组件必须读这里，而不是 `isSystemInDarkTheme()` 或 `-night`
 * 资源限定符：后者按系统 `uiMode` 解析，与应用内主题不一致时会选错图标版本。
 */
val LocalGimiDarkTheme = staticCompositionLocalOf { false }

@Composable
fun GimiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) GimiDarkColorScheme else GimiLightColorScheme

    // 用户气泡配色对齐 M3 surfaceContainerHighest 墨灰容器。
    val userBubbleColors = if (darkTheme) DarkUserBubbleColors else LightUserBubbleColors

    CompositionLocalProvider(
        LocalUserBubbleColors provides userBubbleColors,
        LocalGimiDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = GimiShapes,
            typography = Typography,
            content = content,
        )
    }
}
