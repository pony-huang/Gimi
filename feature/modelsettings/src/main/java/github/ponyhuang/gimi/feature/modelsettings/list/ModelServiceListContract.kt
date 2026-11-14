package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance

/** 已配置实例及其列表操作状态；统一模型服务入口的唯一投影。 */
data class ModelServiceListUiState(
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val query: String = "",
    val items: List<ModelServiceInstance> = emptyList(),
    @param:StringRes val errorRes: Int? = null,
)

sealed interface ModelServiceListAction {
    /** 搜索服务名称或厂商，不修改服务配置。 */
    data class QueryChanged(val value: String) : ModelServiceListAction
}
