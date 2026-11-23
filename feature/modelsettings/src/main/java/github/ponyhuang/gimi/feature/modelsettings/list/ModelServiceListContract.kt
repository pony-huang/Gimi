package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.annotation.StringRes
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance

/** 已配置实例及其目录加载状态；统一模型服务入口的唯一投影。 */
data class ModelServiceListUiState(
    val loadState: CatalogLoadState = CatalogLoadState.Loading,
    val items: List<ModelServiceInstance> = emptyList(),
    @param:StringRes val errorRes: Int? = null,
)
