package github.ponyhuang.gimi.feature.modelsettings.local

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

/** 本地模型页的导航与外部链接由 Route 承担。 */
@Composable
fun LocalModelRoute(onBack: () -> Unit, viewModel: LocalModelViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PreferenceScaffold(title = stringResource(R.string.local_model_title), onBack = onBack) { modifier ->
        LocalModelScreen(state, viewModel::onAction,
            onOpenLicense = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
            modifier = modifier,
        )
    }
}
