package github.ponyhuang.gimi.feature.modelsettings.local

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelBackend
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelDownloadStatus
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelState
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelVariant
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Preview(name = "Local variants light", widthDp = 393, heightDp = 920, showBackground = true)
@Preview(name = "Local variants dark", widthDp = 393, heightDp = 920, uiMode = 0x20, showBackground = true)
@Composable
private fun LocalModelVariantsPreview() {
    AsssistantaiTheme {
        PreferenceScaffold(title = "Gemma 4", onBack = {}) { modifier ->
            LocalModelScreen(previewState(), "gemma4", {}, {}, {}, modifier)
        }
    }
}

@Preview(name = "Local removal light", widthDp = 393, heightDp = 920, showBackground = true)
@Preview(name = "Local removal dark", widthDp = 393, heightDp = 920, uiMode = 0x20, showBackground = true)
@Composable
private fun LocalModelRemovalPreview() {
    AsssistantaiTheme {
        val state = previewState()
        PreferenceScaffold(title = "Gemma 4", onBack = {}) { modifier ->
            LocalModelScreen(state.copy(pendingRemoval = state.models.first()), "gemma4", {}, {}, {}, modifier)
        }
    }
}

private fun previewState(): LocalModelUiState = LocalModelUiState(
    loading = false,
    models = listOf(
        LocalModelState(LocalModelVariant("e2b-cpu", "gemma4", "Gemma 4 E2B · CPU", LocalModelBackend.CPU, 2588147712), LocalModelDownloadStatus.Ready, enabled = true),
        LocalModelState(LocalModelVariant("e2b-gpu", "gemma4", "Gemma 4 E2B · GPU", LocalModelBackend.GPU, 2008432640), LocalModelDownloadStatus.Ready, enabled = true),
        LocalModelState(LocalModelVariant("e4b-cpu", "gemma4", "Gemma 4 E4B · CPU", LocalModelBackend.CPU, 3659530240), LocalModelDownloadStatus.Downloading, progress = 0.42f),
        LocalModelState(LocalModelVariant("e4b-gpu", "gemma4", "Gemma 4 E4B · GPU", LocalModelBackend.GPU, 2969059328)),
    ),
)
