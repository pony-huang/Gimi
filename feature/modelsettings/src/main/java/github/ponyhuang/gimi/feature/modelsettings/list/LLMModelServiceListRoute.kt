package github.ponyhuang.gimi.feature.modelsettings.list

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

// effect 携带动态 string res id，无法在组合期用 stringResource 解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun ModelServiceListRoute(
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LLMModelServiceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ModelServiceListEffect.ShowToast -> Toast.makeText(
                    context,
                    context.getString(effect.messageRes),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    PreferenceScaffold(
        title = stringResource(R.string.modelsettings_list_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        LLMModelServiceListScreen(
            state = state,
            onAction = viewModel::onAction,
            onNavigateToDetail = onNavigateToDetail,
            modifier = scaffoldModifier,
        )
    }
}