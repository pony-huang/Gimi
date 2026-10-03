package github.ponyhuang.gimi.domain.modelcatalog.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelCatalogState
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelRuntimeConfig
import kotlinx.coroutines.flow.StateFlow

/** 管理已校验的本地模型、独立下载任务和持久化的多选启用状态。 */
interface LocalModelRepository {
    val state: StateFlow<LocalModelCatalogState>
    suspend fun awaitReady()
    suspend fun download(modelId: String)
    suspend fun cancelDownload(modelId: String)
    suspend fun setEnabled(modelId: String, enabled: Boolean)
    suspend fun remove(modelId: String)
    fun resolve(modelId: String): LocalModelRuntimeConfig?
}
