package github.ponyhuang.gimi.feature.memory

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MemorySettingsRoute(
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
    MemorySettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
