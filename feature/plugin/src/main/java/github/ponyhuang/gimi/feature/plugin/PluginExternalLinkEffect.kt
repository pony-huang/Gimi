package github.ponyhuang.gimi.feature.plugin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** 各插件共用的外部网页动作处理器，URL 由插件通过既有参数信封提供。 */
internal const val EXTERNAL_LINK_HANDLER = "external_url"

/** Route 侧打开普通网页后结束交互；不把浏览器导航误当作授权已完成。 */
@Composable
internal fun PluginExternalLinkEffect(callback: PluginActionCallbackUiState?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(callback) {
        if (callback?.handlerId != EXTERNAL_LINK_HANDLER) return@LaunchedEffect
        val uri = callback.parameters["url"].orEmpty().toUri()
        try {
            if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.userInfo != null) {
                Toast.makeText(context, R.string.plugin_link_invalid, Toast.LENGTH_SHORT).show()
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(context, R.string.plugin_browser_unavailable, Toast.LENGTH_SHORT).show()
        } finally {
            onDismiss()
        }
    }
}
