package github.ponyhuang.asssistantai.feature.modelsettings.list

import androidx.annotation.StringRes
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting

data class ModelServiceListUiState(
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val query: String = "",
    val items: List<LLMModelSetting> = emptyList(),
    val isMutationBlocked: Boolean = false,
)

sealed interface ModelServiceListAction {
    data class QueryChanged(val value: String) : ModelServiceListAction
    data class EnabledChanged(
        val serviceId: String,
        val enabled: Boolean,
    ) : ModelServiceListAction
}

/** 一次性 UI 反馈（Toast 等），由 Route 经 effects 通道消费。 */
sealed interface ModelServiceListEffect {
    /** @param messageRes 文案的 string resource id。 */
    data class ShowToast(@StringRes val messageRes: Int) : ModelServiceListEffect
}
