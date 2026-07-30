package github.ponyhuang.gimi.feature.modelsettings.defaults

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// effect 携带动态 string res id，无法在组合期用 stringResource 解析，豁免该 lint。
@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun DefaultModelSettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: DefaultModelSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    DefaultModelSettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
