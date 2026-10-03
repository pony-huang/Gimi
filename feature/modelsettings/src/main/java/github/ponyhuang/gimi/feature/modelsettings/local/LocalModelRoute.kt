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
fun LocalModelRoute(brandId: String? = null, onBack: () -> Unit, onOpenBrand: (String) -> Unit = {}, viewModel: LocalModelViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    PreferenceScaffold(title = stringResource(if (brandId == null) R.string.local_model_title else R.string.local_model_gemma4), onBack = onBack) { modifier ->
        LocalModelScreen(state, brandId, viewModel::onAction, onOpenBrand,
            onOpenSource = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"))) },
            modifier = modifier,
        )
    }
}
