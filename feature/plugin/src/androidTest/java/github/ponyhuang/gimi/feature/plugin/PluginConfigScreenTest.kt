package github.ponyhuang.gimi.feature.plugin

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PluginConfigScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun browserAuthorizationReplacesConfigurationPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialTheme {
                PluginConfigScreen(
                    state = PluginConfigUiState(
                        fields = listOf(
                            PluginConfigFieldUiState(
                                key = "clientId",
                                label = "Client ID",
                                kind = PluginConfigFieldDescriptor.Kind.TEXT,
                            ),
                        ),
                        browser = PluginBrowserUiState(
                            actionId = "login",
                            authorizeUrl = "about:blank",
                            redirectBase = "http://127.0.0.1/callback",
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.plugin_browser_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.plugin_config_save)).assertDoesNotExist()
    }

    @Test
    fun browserAuthorizationLoadsAfterWebViewHasSize() {
        val hasSize = mutableStateOf(false)
        val dataUrl = "data:text/html,<html><body>Authorization</body></html>"
        composeRule.setContent {
            MaterialTheme {
                PluginBrowserScreen(
                    browser = PluginBrowserUiState(
                        actionId = "login",
                        authorizeUrl = dataUrl,
                        redirectBase = "http://127.0.0.1/callback",
                    ),
                    onComplete = {},
                    modifier = if (hasSize.value) Modifier.fillMaxSize() else Modifier.size(0.dp),
                )
            }
        }

        val webView = composeRule.activity.window.decorView.findWebView()
        composeRule.runOnIdle { assertNull(webView.url) }

        composeRule.runOnIdle { hasSize.value = true }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(dataUrl, webView.url)
            assertTrue(webView.settings.domStorageEnabled)
            assertTrue(webView.settings.useWideViewPort)
            assertTrue(webView.settings.loadWithOverviewMode)
            assertTrue(CookieManager.getInstance().acceptThirdPartyCookies(webView))
        }
    }

    private fun View.findWebView(): WebView {
        if (this is WebView) return this
        if (this is ViewGroup) {
            repeat(childCount) { index ->
                runCatching { return getChildAt(index).findWebView() }
            }
        }
        error("WebView not found")
    }

}
