package github.ponyhuang.gimi.feature.plugin

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * 配置动作回调页：当前通过 WebView 完成网页交互，并把产生的通用参数交回插件。
 *
 * 插件下发的参数不可信：
 * - [PARAM_AUTHORIZE_URL] 必须是 https 外部地址，否则拒绝建立 WebView；
 * - [PARAM_REDIRECT_BASE] 以 scheme/host/port/path 前缀做匹配基线，不做整串 startsWith；
 * - [PARAM_COMPLETION_SCRIPT] 以 JSON 字符串字面量传给 eval，不允许闭合宿主表达式。
 */
@Composable
internal fun PluginActionCallbackScreen(
    callback: PluginActionCallbackUiState,
    onCallback: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (callback.handlerId == EXTERNAL_LINK_HANDLER) {
        Text(stringResource(R.string.plugin_opening_link), modifier = modifier)
        return
    }
    if (callback.handlerId != HANDLER_WEB) {
        Text(
            text = stringResource(R.string.plugin_action_callback_unsupported, callback.handlerId),
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val authorizeUrl = callback.parameters[PARAM_AUTHORIZE_URL].orEmpty()
    val redirectBase = callback.parameters[PARAM_REDIRECT_BASE].orEmpty()
    if (!isValidWebCallbackParameters(authorizeUrl, redirectBase)) {
        Log.w(TAG, "Rejecting untrusted web callback parameters for ${callback.actionId}")
        Text(
            text = stringResource(R.string.plugin_action_callback_unsupported, HANDLER_WEB),
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    ActionCallbackWebView(
        callback = callback,
        onCallback = onCallback,
        modifier = modifier.fillMaxSize(),
    )
}

/** 授权地址必须 https 且带主机；redirectBase 至少要有合法 scheme 与主机，允许 OAuth 本地回环 http。 */
private fun isValidWebCallbackParameters(authorizeUrl: String, redirectBase: String): Boolean {
    val authorize = Uri.parse(authorizeUrl)
    if (authorize.scheme != "https" || authorize.host.isNullOrBlank()) return false
    val base = Uri.parse(redirectBase)
    val scheme = base.scheme ?: return false
    if (scheme != "https" && scheme != "http") return false
    if (base.host.isNullOrBlank()) return false
    // 仅 http 允许显式回环地址（OAuth loopback redirect），https 无此限制。
    if (scheme == "http" && base.host.orEmpty().lowercase() !in LOOPBACK_HOSTS) return false
    return true
}

/** scheme 缺省端口的补全比较（Uri.effectivePort 是静态工具，实例上不可用）。 */
private fun effectivePort(uri: Uri): Int = when {
    uri.port != -1 -> uri.port
    uri.scheme == "https" -> 443
    uri.scheme == "http" -> 80
    else -> -1
}

/**
 * redirect 命中判定：scheme/host/port 完全一致，且 path 为基线 path 的前缀
 * （基线有路径时要求整段或以 `/` 续接，避免 `/cb` 误匹配 `/callback`）。
 */
private fun matchesRedirectBase(url: String, redirectBase: String): Boolean {
    if (redirectBase.isEmpty()) return false
    val uri = Uri.parse(url)
    val base = Uri.parse(redirectBase)
    if (uri.scheme != base.scheme || uri.host == null) return false
    if (!uri.host.equals(base.host, ignoreCase = true)) return false
    if (effectivePort(uri) != effectivePort(base)) return false
    val basePath = base.path.orEmpty()
    if (basePath.isEmpty() || basePath == "/") return true
    val path = uri.path.orEmpty()
    return path.startsWith(basePath) &&
        (path.length == basePath.length || path[basePath.length] == '/')
}

/** 错误日志脱敏：只保留 scheme://host/path，不外泄 query 里的 code/state/token。 */
private fun redactUrlForLog(uri: Uri): String =
    "${uri.scheme}://${uri.host.orEmpty()}${uri.path.orEmpty()}"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ActionCallbackWebView(
    callback: PluginActionCallbackUiState,
    onCallback: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // AndroidView 的 factory 只执行一次、闭包捕获首组合的派生值；用 key 在 callback
    // 变化（同一导航条目复用重建参数）时强制重建 WebView，保证捕获的参数始终最新。
    key(callback) {
        val authorizeUrl = callback.parameters[PARAM_AUTHORIZE_URL].orEmpty()
        val redirectBase = callback.parameters[PARAM_REDIRECT_BASE].orEmpty()
        val completionScript = callback.parameters[PARAM_COMPLETION_SCRIPT]
        val captureCookiesForUrl = callback.parameters[PARAM_CAPTURE_COOKIES_FOR_URL]
        val desktopMode = callback.parameters[PARAM_DESKTOP_MODE].toBoolean()
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    if (desktopMode) {
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
                        private var pollCount = 0

                        /** 命中 redirectBase 即截获重定向；post 到主线程避免在回调栈内销毁自身。 */
                        private fun tryComplete(view: WebView, url: String?): Boolean {
                            if (!completed && url != null && matchesRedirectBase(url, redirectBase)) {
                                completed = true
                                view.post { onCallback(mapOf(CALLBACK_URL_KEY to url)) }
                                return true
                            }
                            return completed
                        }

                        /** 页面条件成立后刷新 Cookie 持久层，再用一次性内存回调交给插件。 */
                        private fun startCompletionPolling(view: WebView) {
                            val script = completionScript ?: return
                            if (polling || completed) return
                            polling = true

                            fun poll() {
                                if (completed) return
                                // 轮询有上限：条件永不成立（站点改版/用户中途退出）时停止，
                                // 不无限每 500ms evaluateJavascript 耗电。
                                if (pollCount++ >= MAX_COMPLETION_POLLS) {
                                    Log.w(TAG, "Completion condition not met after $pollCount polls; giving up")
                                    return
                                }
                                // 脚本作为 JSON 字符串字面量交给 eval：无法闭合宿主表达式模板。
                                view.evaluateJavascript(
                                    "Boolean(eval(${JSONObject.quote(script)}))",
                                ) { raw ->
                                    if (raw == "true") {
                                        completed = true
                                        val cookieUrl = captureCookiesForUrl
                                        val cookies = cookieUrl?.let { url ->
                                            CookieManager.getInstance().getCookie(url)
                                        }.orEmpty()
                                        CookieManager.getInstance().flush()
                                        view.post {
                                            onCallback(mapOf(CALLBACK_COOKIES_KEY to cookies))
                                        }
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
                                "WebView error ${error.errorCode} on ${redactUrlForLog(request.url)}: ${error.description}",
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
                    if (authorizeUrl.isBlank()) {
                        Log.w(TAG, "Empty authorize URL for ${callback.actionId}")
                    } else {
                        fun retryLoad(attempt: Int) {
                            if (navigationStarted || attempt > LOAD_MAX_ATTEMPTS) return
                            Log.w(
                                TAG,
                                "Loading plugin authorize URL (attempt $attempt): ${redactUrlForLog(Uri.parse(authorizeUrl))}",
                            )
                            loadUrl(authorizeUrl)
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
}

private const val TAG: String = "PluginActionCallback"
private const val HANDLER_WEB: String = "web"
private const val CALLBACK_URL_KEY: String = "url"
private const val CALLBACK_COOKIES_KEY: String = "cookies"
private const val PARAM_AUTHORIZE_URL: String = "authorize_url"
private const val PARAM_REDIRECT_BASE: String = "redirect_base"
private const val PARAM_COMPLETION_SCRIPT: String = "completion_script"
private const val PARAM_CAPTURE_COOKIES_FOR_URL: String = "capture_cookies_for_url"
private const val PARAM_DESKTOP_MODE: String = "desktop_mode"

/** 授权 URL 加载重试上限与间隔：WebView 未就绪时导航会被静默丢弃，需要重试到渲染进程就绪。 */
private const val LOAD_MAX_ATTEMPTS: Int = 6
private const val LOAD_RETRY_DELAY_MS: Long = 1_000L

/** 完成条件轮询上限：500ms × 240 = 2 分钟，超时放弃。 */
private const val COMPLETION_POLL_INTERVAL_MS: Long = 500L
private const val MAX_COMPLETION_POLLS: Int = 240

/** OAuth 本地回环重定向允许 http 的主机白名单。 */
private val LOOPBACK_HOSTS: Set<String> = setOf("localhost", "127.0.0.1", "[::1]")

private const val DESKTOP_USER_AGENT: String =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
