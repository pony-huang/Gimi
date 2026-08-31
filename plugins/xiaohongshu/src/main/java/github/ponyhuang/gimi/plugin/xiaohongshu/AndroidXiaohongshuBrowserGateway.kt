package github.ponyhuang.gimi.plugin.xiaohongshu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.IOException
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import kotlin.time.Duration.Companion.milliseconds

/** Android WebView 对 [XiaohongshuBrowserGateway] 的实现；所有 WebView 调用都回到主线程。 */
internal class AndroidXiaohongshuBrowserGateway(
    private val context: Context,
) : XiaohongshuBrowserGateway {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null

    override suspend fun navigate(url: String) = withTimeout(NAVIGATION_TIMEOUT_MS.milliseconds) {
        suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                navContinuation = continuation
                navCompleted = false
                getOrCreateWebView().loadUrl(upgradeToHttps(url))
            }
        }
    }

    override suspend fun waitUntil(script: String, timeoutMillis: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (evaluate("Boolean($script)") == "true") return true
            delay(POLL_INTERVAL_MS.milliseconds)
        }
        return false
    }

    override suspend fun evaluate(script: String): String? = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            getOrCreateWebView().evaluateJavascript(script) { raw ->
                if (continuation.isActive) continuation.resume(decodeWebViewResult(raw))
            }
        }
    }

    override suspend fun clearCookies() = suspendCancellableCoroutine { continuation ->
        mainHandler.post {
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getOrCreateWebView(): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView 只能在主线程创建" }
        return webView ?: WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            // 小红书网页版在移动 UA 下会返回不同 DOM；固定桌面 UA 与参考项目保持同一页面契约。
            settings.userAgentString = DESKTOP_USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = GimiWebViewClient()
            // WebView 不 attach 到任何窗口（隐藏浏览器），但必须手动给它尺寸：
            // 未布局的视图是 0x0，输入事件与视口度量都会异常。
            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1080)
            val height = metrics.heightPixels.coerceAtLeast(1920)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
            )
            layout(0, 0, width, height)
        }.also { webView = it }
    }

    // 用基类型 Continuation 持有导航等待者：withTimeout 取消后页面回调再 resume 会抛
    // IllegalStateException，统一用 runCatching 吞掉即可。
    private var navContinuation: kotlin.coroutines.Continuation<Unit>? = null
    private var navCompleted: Boolean = false

    /** 处理导航回调（https 升级/完成/错误）。 */
    private inner class GimiWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            val upgraded = url?.let(::upgradeToHttps)
            if (upgraded != url) {
                // 站点在个别流程会 http:// 重定向；强制升级 https 再加载，
                // 避免被宿主默认的明文拦截（net::ERR_CLEARTEXT_NOT_PERMITTED）。
                view.stopLoading()
                view.loadUrl(upgraded!!)
                return
            }
            navCompleted = false
        }

        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val target = request.url.toString()
            val upgraded = upgradeToHttps(target)
            if (upgraded != target) {
                view.loadUrl(upgraded)
                return true
            }
            return false
        }

        override fun onPageFinished(view: WebView, loadedUrl: String?) {
            val continuation = navContinuation
            if (!navCompleted && continuation != null) {
                navCompleted = true
                navContinuation = null
                runCatching { continuation.resume(Unit) }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (!request.isForMainFrame) return
            val target = request.url.toString()
            val upgraded = upgradeToHttps(target)
            if (upgraded != target) {
                // 站点 http:// 重定向没走 shouldOverrideUrlLoading/onPageStarted 拦截
                // （如 meta refresh 或资源级加载）已到达错误回调：升级后重载而非报错。
                view.loadUrl(upgraded)
                return
            }
            val continuation = navContinuation ?: return
            navContinuation = null
            navCompleted = true
            // 带出具体 URL，便于定位被拦的是哪个地址。
            runCatching { continuation.resumeWithException(IOException("${error.description} @ $target")) }
        }
    }

    companion object {
        private const val TAG = "XhsBrowserGateway"
        private const val NAVIGATION_TIMEOUT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 250L
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

/** 已知小红书域名；这些域在个别流程会走 http:// 重定向，需升级为 https。 */
internal val XHS_CLEARTEXT_HOSTS: Set<String> = setOf("xiaohongshu.com", "xhscdn.com", "xhslink.com")

/** 已知域名上的明文 URL 升级为 https（主站全 https）；其他域名原样返回，不做盲目升级。 */
internal fun upgradeToHttps(url: String): String {
    if (!url.startsWith("http://")) return url
    // 用 java.net.URI 而非 android.net.Uri：宿主进程可用，且本地单测（android.jar 桩）下也能解析。
    val host = runCatching { URI(url).host }.getOrNull() ?: return url
    if (XHS_CLEARTEXT_HOSTS.any { host == it || host.endsWith(".$it") }) {
        return url.replaceFirst("http://", "https://")
    }
    return url
}

/** 解开 WebView `evaluateJavascript` 对字符串结果额外添加的一层 JSON 引号。 */
internal fun decodeWebViewResult(raw: String?): String? {
    if (raw == null || raw == "null") return null
    val parsed = runCatching { JSONTokener(raw).nextValue() }.getOrNull()
    return when (parsed) {
        null, JSONObjectNull -> null
        is String -> parsed
        else -> parsed.toString()
    }
}

private val JSONObjectNull: Any = org.json.JSONObject.NULL
