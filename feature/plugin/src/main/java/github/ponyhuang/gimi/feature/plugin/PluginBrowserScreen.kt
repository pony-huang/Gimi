package github.ponyhuang.gimi.feature.plugin

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.URLEncoder
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
                if (browser.desktopMode) {
                    // 登录与后续自动化必须共享桌面版 DOM；仅改 viewport 仍可能被站点按移动 UA 分流。
                    settings.userAgentString = DESKTOP_USER_AGENT
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                // 由 onPageStarted 置位：作为「导航真正开始」的信号，供下方重试加载判断。
                var navigationStarted = false
                webViewClient = object : WebViewClient() {
                    private var completed = false
                    private var polling = false

                    /** 命中 redirectBase 即截获重定向；post 到主线程避免在回调栈内销毁自身。 */
                    private fun tryComplete(view: WebView, url: String?): Boolean {
                        if (!completed && url != null && url.startsWith(browser.redirectBase)) {
                            completed = true
                            view.post { onComplete(url) }
                            return true
                        }
                        return completed
                    }

                    /** 页面条件成立后刷新 Cookie 持久层，再用一次性内存回调交给插件。 */
                    private fun startCompletionPolling(view: WebView) {
                        val script = browser.completionScript ?: return
                        if (polling || completed) return
                        polling = true

                        fun poll() {
                            if (completed) return
                            view.evaluateJavascript("Boolean($script)") { raw ->
                                if (raw == "true") {
                                    completed = true
                                    val cookieUrl = browser.captureCookiesForUrl
                                    val cookies = cookieUrl?.let { url ->
                                        CookieManager.getInstance().getCookie(url)
                                    }.orEmpty()
                                    CookieManager.getInstance().flush()
                                    val callback = buildString {
                                        append("gimi-plugin-capture://complete")
                                        if (cookies.isNotEmpty()) {
                                            append("?cookies=")
                                            append(URLEncoder.encode(cookies, Charsets.UTF_8.name()))
                                        }
                                    }
                                    view.post { onComplete(callback) }
                                } else {
                                    view.postDelayed(::poll, COMPLETION_POLL_INTERVAL_MS)
                                }
                            }
                        }

                        poll()
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
                        navigationStarted = true
                        if (tryComplete(view, url)) {
                            view.stopLoading()
                        } else {
                            super.onPageStarted(view, url, favicon)
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        super.onPageFinished(view, url)
                        startCompletionPolling(view)
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: android.webkit.WebResourceError,
                    ) {
                        Log.w(
                            TAG,
                            "WebView error ${error.errorCode} on ${request.url}: ${error.description}",
                        )
                        super.onReceivedError(view, request, error)
                    }
                }
                // 加载授权 URL。必须在 WebView 原生侧就绪后再 loadUrl：部分 ROM 的 WebView
                // （如魅族 Flyme 上的 140 版）在 factory/post 里立即 loadUrl 时渲染进程还没
                // 起来，导航会被静默丢弃，表现为整页黑屏（spotify 授权页复现，onPageStarted
                // 永不回调、无任何网络请求）。以 onPageStarted 作为「导航真正开始」的信号，
                // 未开始就延迟重试，直到 WebView 就绪或超过次数上限。原 addOnLayoutChangeListener
                // 门控同样不可靠（部分设备不触发，URL 从未加载）。
                if (browser.authorizeUrl.isBlank()) {
                    Log.w(TAG, "Empty authorize URL for ${browser.actionId}")
                } else {
                    fun retryLoad(attempt: Int) {
                        if (navigationStarted || attempt > LOAD_MAX_ATTEMPTS) return
                        Log.w(TAG, "Loading plugin authorize URL (attempt $attempt): ${browser.authorizeUrl}")
                        loadUrl(browser.authorizeUrl)
                        postDelayed({ retryLoad(attempt + 1) }, LOAD_RETRY_DELAY_MS)
                    }
                    post { retryLoad(1) }
                }
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() },
    )
}

private const val TAG: String = "PluginBrowser"

/** 授权 URL 加载重试上限与间隔：WebView 未就绪时导航会被静默丢弃，需要重试到渲染进程就绪。 */
private const val LOAD_MAX_ATTEMPTS: Int = 6
private const val LOAD_RETRY_DELAY_MS: Long = 1_000L

private const val COMPLETION_POLL_INTERVAL_MS: Long = 500L
private const val DESKTOP_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
