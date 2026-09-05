package github.ponyhuang.gimi.feature.plugin

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import github.ponyhuang.gimi.domain.plugin.model.PluginConfigFieldDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PluginConfigScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun actionCallbackInteractionReplacesConfigurationPage() {
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
                        callback = PluginActionCallbackUiState(
                            actionId = "login",
                            handlerId = "web",
                            parameters = mapOf(
                                "authorize_url" to "about:blank",
                                "redirect_base" to "http://127.0.0.1/callback",
                            ),
                        ),
                    ),
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.plugin_action_callback_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.plugin_config_save)).assertDoesNotExist()
    }

    @Test
    fun actionCallbackInteractionLoadsAndConfiguresWebView() {
        val dataUrl = "data:text/html,<html><body>Authorization</body></html>"
        composeRule.setContent {
            MaterialTheme {
                PluginActionCallbackScreen(
                    callback = PluginActionCallbackUiState(
                        actionId = "login",
                        handlerId = "web",
                        parameters = mapOf(
                            "authorize_url" to dataUrl,
                            "redirect_base" to "http://127.0.0.1/callback",
                        ),
                    ),
                    onCallback = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val webView = composeRule.activity.window.decorView.findWebView()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(dataUrl, webView.url)
            assertTrue(webView.settings.domStorageEnabled)
            assertTrue(webView.settings.useWideViewPort)
            assertTrue(webView.settings.loadWithOverviewMode)
            assertTrue(CookieManager.getInstance().acceptThirdPartyCookies(webView))
        }
    }

    @Test
    fun unsupportedActionCallbackHandlerShowsExplanation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            MaterialTheme {
                PluginActionCallbackScreen(
                    callback = PluginActionCallbackUiState(
                        actionId = "pair",
                        handlerId = "device-code",
                    ),
                    onCallback = {},
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.plugin_action_callback_unsupported, "device-code"),
        ).assertIsDisplayed()
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
