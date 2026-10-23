package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting

/** 厂商目录及其添加状态；首页与厂商选择页采用不同投影。 */
data class ModelServiceListUiState(
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val query: String = "",
    val items: List<LLMModelSetting> = emptyList(),
    val isMutationBlocked: Boolean = false,
    val pendingServiceIds: Set<String> = emptySet(),
    @param:StringRes val errorRes: Int? = null,
)

sealed interface ModelServiceListAction {
    /** 立即启停已添加服务，保留连接和模型配置。 */
    data class EnabledChanged(val serviceId: String, val enabled: Boolean) : ModelServiceListAction
    /** 搜索目录，不修改服务配置。 */
    data class QueryChanged(val value: String) : ModelServiceListAction
}
