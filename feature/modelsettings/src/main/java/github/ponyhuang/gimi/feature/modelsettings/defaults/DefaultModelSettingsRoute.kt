package github.ponyhuang.gimi.feature.modelsettings.defaults

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

// effect 携带动态 string res id，无法在组合期用 stringResource 解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DefaultModelSettingsRoute(
    onBack: () -> Unit,
    /** 进入即打开 Realtime 模型选择；用于从通话页「去配置」跳转而来。 */
    openRealtimePicker: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val noBrowser = stringResource(R.string.modelsettings_no_browser)

    LaunchedEffect(openRealtimePicker) {
        if (openRealtimePicker) viewModel.showRealtimeModelDialog()
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DefaultModelSettingsEffect.ShowToast -> Toast.makeText(
                    context,
                    context.getString(effect.messageRes),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    PreferenceScaffold(

        style = PreferenceStyle.Compact,
        title = stringResource(R.string.modelsettings_defaults_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        DefaultModelSettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
            onOpenUrl = { url -> context.openUrl(url, noBrowser) },
        )
    }
}

/** 打开外部链接；系统没有可用浏览器时用 Toast 说明，避免点击后毫无反馈。 */
private fun Context.openUrl(url: String, noBrowser: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, noBrowser, Toast.LENGTH_SHORT).show()
    }
}
