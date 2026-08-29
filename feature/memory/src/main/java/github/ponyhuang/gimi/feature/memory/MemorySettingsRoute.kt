package github.ponyhuang.gimi.feature.memory

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

@Composable
fun MemorySettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemorySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                MemorySettingsEffect.Saved -> R.string.memory_saved
                MemorySettingsEffect.SaveFailed -> R.string.memory_save_failed
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    PreferenceScaffold(
        title = stringResource(R.string.memory_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        MemorySettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            modifier = scaffoldModifier,
        )
    }
}