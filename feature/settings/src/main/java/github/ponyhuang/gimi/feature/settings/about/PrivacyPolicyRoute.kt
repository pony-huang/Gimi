package github.ponyhuang.gimi.feature.settings.about

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

/** Gimi 仓库 wiki 上的隐私政策页面（英文在上、中文在下）。 */
private const val PRIVACY_POLICY_URL = "https://github.com/pony-huang/Gimi/wiki/Privacy-Policy"

/** 隐私政策页：内嵌 WebView 加载仓库 wiki 页面，本地不再内置政策文本。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PrivacyPolicyRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.privacy_policy_title),
        onBack = onBack,
        modifier = modifier,
    ) { scaffoldModifier ->
        AndroidView(
            modifier = scaffoldModifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    loadUrl(PRIVACY_POLICY_URL)
                }
            },
        )
    }
}
