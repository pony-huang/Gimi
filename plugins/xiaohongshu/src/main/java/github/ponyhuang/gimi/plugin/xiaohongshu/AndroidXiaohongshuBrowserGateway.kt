package github.ponyhuang.gimi.plugin.xiaohongshu

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.IOException
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.net.URLConnection
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
                val view = getOrCreateWebView()
                var completed = false
                view.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        completed = false
                    }

                    override fun onPageFinished(view: WebView, loadedUrl: String?) {
                        if (!completed && continuation.isActive) {
                            completed = true
                            continuation.resume(Unit)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame && !completed && continuation.isActive) {
                            completed = true
                            continuation.resumeWithException(IOException(error.description.toString()))
                        }
                    }
                }
                view.loadUrl(url)
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

    override suspend fun selectFiles(selector: String, sources: List<String>): Boolean {
        val prepared = withContext(Dispatchers.IO) { sources.map(::prepareUploadUri) }
        return try {
            withTimeout(FILE_CHOOSER_TIMEOUT_MS.milliseconds) {
                suspendCancellableCoroutine { continuation ->
                    mainHandler.post {
                        val view = getOrCreateWebView()
                        view.webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView,
                                filePathCallback: ValueCallback<Array<Uri>>,
                                fileChooserParams: FileChooserParams,
                            ): Boolean {
                                filePathCallback.onReceiveValue(prepared.map { it.uri }.toTypedArray())
                                if (continuation.isActive) continuation.resume(true)
                                return true
                            }
                        }
                        val script = """
                            (() => {
                              const input = document.querySelector(${org.json.JSONObject.quote(selector)});
                              if (!input) return false;
                              input.click();
                              return true;
                            })()
                        """.trimIndent()
                        view.evaluateJavascript(script) { clicked ->
                            if (clicked != "true" && continuation.isActive) continuation.resume(false)
                        }
                    }
                }
            }
        } finally {
            // WebView renderer 已取得文件句柄后再清理临时 MediaStore 项，避免长期污染下载目录。
            mainHandler.postDelayed(
                { prepared.filter(PreparedUpload::temporary).forEach { context.contentResolver.delete(it.uri, null, null) } },
                UPLOAD_URI_LIFETIME_MS,
            )
        }
    }

    private fun prepareUploadUri(source: String): PreparedUpload {
        if (source.startsWith("content://")) return PreparedUpload(Uri.parse(source), temporary = false)
        val name = source.substringAfterLast('/').substringBefore('?').ifBlank { "gimi-${UUID.randomUUID()}" }
        val mime = URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Gimi")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values),
        ) { "无法创建上传临时文件" }
        try {
            val input = when {
                source.startsWith("http://") || source.startsWith("https://") -> URL(source).openStream()
                source.startsWith("file://") -> FileInputStream(File(requireNotNull(Uri.parse(source).path)))
                else -> FileInputStream(File(source))
            }
            input.use { sourceStream ->
                context.contentResolver.openOutputStream(uri, "w")!!.use(sourceStream::copyTo)
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return PreparedUpload(uri, temporary = true)
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
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
        }.also { webView = it }
    }

    companion object {
        private const val NAVIGATION_TIMEOUT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 250L
        private const val FILE_CHOOSER_TIMEOUT_MS = 15_000L
        private const val UPLOAD_URI_LIFETIME_MS = 2 * 60_000L
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

/**
 * WebView 文件选择器使用的 URI。
 *
 * @property uri 可被 WebView renderer 读取的 content URI。
 * @property temporary 是否由插件临时写入 MediaStore、稍后需清理。
 */
private data class PreparedUpload(val uri: Uri, val temporary: Boolean)

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
