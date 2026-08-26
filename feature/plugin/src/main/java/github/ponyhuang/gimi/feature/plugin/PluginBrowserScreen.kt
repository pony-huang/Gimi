package github.ponyhuang.gimi.feature.plugin

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 内置浏览器授权页：用 WebView 加载授权 URL，不跳出到系统浏览器。
 *
 * 当导航命中 [PluginBrowserUiState.redirectBase] 前缀（OAuth 回调）时，
 * 截获完整重定向 URL 交给 [onComplete]，并返回插件配置页。
 */
@Composable
internal fun PluginBrowserScreen(
    browser: PluginBrowserUiState,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowserWebView(
        browser = browser,
        onComplete = onComplete,
        modifier = modifier.fillMaxSize(),
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    browser: PluginBrowserUiState,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    private var completed = false

                    /** 命中 redirectBase 即截获重定向；post 到主线程避免在回调栈内销毁自身。 */
                    private fun tryComplete(view: WebView, url: String?): Boolean {
                        if (!completed && url != null && url.startsWith(browser.redirectBase)) {
                            completed = true
                            view.post { onComplete(url) }
                            return true
                        }
                        return completed
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        return tryComplete(view, url)
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                        tryComplete(view, url)

                    // JS 跳转（window.location 等）不一定触发 shouldOverrideUrlLoading，
                    // onPageStarted 对所有导航都回调，作兜底防止 WebView 真去连 localhost。
                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        if (tryComplete(view, url)) {
                            view.stopLoading()
                        } else {
                            super.onPageStarted(view, url, favicon)
                        }
                    }
                }
                var initialUrlLoaded = false
                addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                    if (!initialUrlLoaded && right > left && bottom > top) {
                        initialUrlLoaded = true
                        (view as WebView).loadUrl(browser.authorizeUrl)
                    }
                }
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() },
    )
}
