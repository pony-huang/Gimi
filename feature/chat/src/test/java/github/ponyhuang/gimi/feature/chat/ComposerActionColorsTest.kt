package github.ponyhuang.gimi.feature.chat

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerActionColorsTest {

    @Test
    fun actionContainerIsTransparent() {
        assertEquals(Color.Transparent, composerActionContainerColor())
    }

    @Test
    fun enabledIconUsesCurrentThemeOnSurface() {
        val lightScheme = lightColorScheme(onSurface = Color(0xFF202124))
        val darkScheme = darkColorScheme(onSurface = Color(0xFFE6E6E6))

        assertEquals(lightScheme.onSurface, composerActionIconColor(lightScheme, enabled = true))
        assertEquals(darkScheme.onSurface, composerActionIconColor(darkScheme, enabled = true))
    }

    @Test
    fun disabledIconRetainsThemeHueAtLowerOpacity() {
        val scheme = darkColorScheme(onSurface = Color(0xFFE6E6E6))

        assertEquals(
            scheme.onSurface.copy(alpha = 0.38f),
            composerActionIconColor(scheme, enabled = false),
        )
    }
}
