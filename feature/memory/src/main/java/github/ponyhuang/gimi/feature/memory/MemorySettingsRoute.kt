package github.ponyhuang.gimi.feature.memory

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle

@Composable
fun MemorySettingsRoute(
    onBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemorySettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val noticeMessage = when (state.notice) {
        MemorySettingsNotice.Saved -> stringResource(R.string.memory_saved)
        MemorySettingsNotice.SaveFailed -> stringResource(R.string.memory_save_failed)
        null -> null
    }
    LaunchedEffect(state.notice, noticeMessage) {
        val message = noticeMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        viewModel.onAction(MemorySettingsAction.ConsumeNotice)
    }
    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.memory_screen_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        MemorySettingsScreen(
            state = state,
            onAction = viewModel::onAction,
            onNavigateToHistory = onNavigateToHistory,
            onOpenMem0Quickstart = { uriHandler.openUri(MEM0_QUICKSTART_URL) },
            modifier = scaffoldModifier,
        )
    }
}

private const val MEM0_QUICKSTART_URL = "https://docs.mem0.ai/platform/quickstart"
