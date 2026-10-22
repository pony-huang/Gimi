package github.ponyhuang.gimi.feature.modelsettings.list

import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting

/** 厂商目录及其添加状态；首页与厂商选择页采用不同投影。 */
data class ModelServiceListUiState(
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val query: String = "",
    val items: List<LLMModelSetting> = emptyList(),
    val isMutationBlocked: Boolean = false,
)

sealed interface ModelServiceListAction {
    /** 搜索目录，不修改服务配置。 */
    data class QueryChanged(val value: String) : ModelServiceListAction
}
