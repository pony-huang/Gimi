package github.ponyhuang.asssistantai.feature.modelsettings.list

import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService

data class ModelServiceListUiState(
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val query: String = "",
    val items: List<ModelService> = emptyList(),
)

sealed interface ModelServiceListAction {
    data class QueryChanged(val value: String) : ModelServiceListAction
    data class EnabledChanged(
        val serviceId: String,
        val enabled: Boolean,
    ) : ModelServiceListAction
}
