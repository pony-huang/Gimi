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
        PreferenceScaffold(title = "本地模型", onBack = {}) { modifier ->
            LocalModelScreen(previewState(), {}, {}, modifier)
        }
    }
}

@Preview(name = "Local removal light", widthDp = 393, heightDp = 920, showBackground = true)
@Preview(name = "Local removal dark", widthDp = 393, heightDp = 920, uiMode = 0x20, showBackground = true)
@Composable
private fun LocalModelRemovalPreview() {
    AsssistantaiTheme {
        val state = previewState()
        PreferenceScaffold(title = "本地模型", onBack = {}) { modifier ->
            LocalModelScreen(state.copy(pendingRemoval = state.models.first()), {}, {}, modifier)
        }
    }
}

private fun previewState(): LocalModelUiState = LocalModelUiState(
    loading = false,
    expandedModelIds = setOf("e4b-cpu"),
    models = listOf(
        LocalModelState(LocalModelVariant("e2b-cpu", "gemma4", "Gemma 4 E2B · CPU", LocalModelBackend.CPU, 2588147712, modelPageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"), LocalModelDownloadStatus.Ready),
        LocalModelState(LocalModelVariant("e2b-gpu", "gemma4", "Gemma 4 E2B · GPU", LocalModelBackend.GPU, 2008432640, modelPageUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"), LocalModelDownloadStatus.Ready),
        LocalModelState(LocalModelVariant("e4b-cpu", "gemma4", "Gemma 4 E4B · CPU", LocalModelBackend.CPU, 3659530240, modelPageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm"), LocalModelDownloadStatus.Downloading, progress = 0.42f),
        LocalModelState(LocalModelVariant("e4b-gpu", "gemma4", "Gemma 4 E4B · GPU", LocalModelBackend.GPU, 2969059328, modelPageUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm")),
    ),
)

@Preview(name = "Local failures and verification", widthDp = 393, heightDp = 920, showBackground = true)
@Composable
private fun LocalModelDownloadStatesPreview() {
    AsssistantaiTheme {
        val state = previewState()
        LocalModelScreen(state.copy(models = state.models.mapIndexed { index, model ->
            when (index) {
                0 -> model.copy(status = LocalModelDownloadStatus.Failed,
                    failure = github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelFailure.Storage)
                1 -> model.copy(status = LocalModelDownloadStatus.Verifying)
                else -> model
            }
        }), {}, {})
    }
}
