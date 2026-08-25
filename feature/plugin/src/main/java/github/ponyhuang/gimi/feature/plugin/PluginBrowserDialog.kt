package github.ponyhuang.gimi.feature.plugin

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/**
 * 内置浏览器授权弹窗：用 WebView 加载授权 URL，不跳出到系统浏览器。
 *
 * 当导航命中 [PluginBrowserUiState.redirectBase] 前缀（OAuth 回调）时，
 * 截获完整重定向 URL 交给 [onComplete]，并在应用内关闭弹窗。
 */
@Composable
internal fun PluginBrowserDialog(
    browser: PluginBrowserUiState,
    onComplete: (String) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.plugin_browser_title),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.plugin_browser_close),
                        )
                    }
                }
                BrowserWebView(
                    browser = browser,
                    onComplete = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 420.dp),
                )
            }
        }
    }
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
                webViewClient = object : WebViewClient() {
                    /** 命中 redirectBase 即截获重定向；post 到主线程避免在回调栈内销毁自身。 */
                    private fun tryComplete(view: WebView, url: String?): Boolean {
                        if (url != null && url.startsWith(browser.redirectBase)) {
                            view.post { onComplete(url) }
                            return true
                        }
                        return false
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        return if (tryComplete(view, url)) {
                            true
                        } else {
                            super.shouldOverrideUrlLoading(view, request)
                        }
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
                loadUrl(browser.authorizeUrl)
            }
        },
        modifier = modifier,
        onRelease = { it.destroy() },
    )
}
