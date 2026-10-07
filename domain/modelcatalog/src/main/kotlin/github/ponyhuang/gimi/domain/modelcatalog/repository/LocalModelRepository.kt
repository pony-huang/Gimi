package github.ponyhuang.gimi.domain.modelcatalog.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelCatalogState
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelRuntimeConfig
import kotlinx.coroutines.flow.StateFlow

/** 管理已校验的本地模型与独立下载任务；Ready 版本自动可用于聊天。 */
interface LocalModelRepository {
    val state: StateFlow<LocalModelCatalogState>
    suspend fun awaitReady()
    suspend fun download(modelId: String)
    suspend fun cancelDownload(modelId: String)
    suspend fun remove(modelId: String)
    fun resolve(modelId: String): LocalModelRuntimeConfig?
}
