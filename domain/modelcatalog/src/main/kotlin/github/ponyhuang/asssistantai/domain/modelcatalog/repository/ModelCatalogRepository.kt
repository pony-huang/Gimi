package github.ponyhuang.asssistantai.domain.modelcatalog.repository

import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import kotlinx.coroutines.flow.Flow

interface ModelCatalogRepository {
    suspend fun awaitReady()

    fun observeService(serviceId: String): Flow<ModelService?>

    fun observeServices(): Flow<List<ModelService>>

    fun observeLoadState(): Flow<CatalogLoadState>

    fun observeAssistantSelection(): Flow<ModelSelection?>

    fun observeFastSelection(): Flow<ModelSelection?>

    fun observeSpeechSelection(): Flow<ModelSelection?>

    fun observeTtsSelection(): Flow<ModelSelection?>

    fun observeTtsVoice(): Flow<String>

    fun currentService(serviceId: String): ModelService?

    fun currentServices(): List<ModelService>

    fun currentAssistantSelection(): ModelSelection?

    fun currentSpeechSelection(): ModelSelection?

    fun currentTtsSelection(): ModelSelection?

    fun currentTtsVoice(): String

    /** Process-local selection used by the active agent; this is not a persisted default. */
    fun setRuntimeSelection(selection: ModelSelection?)

    fun updateApiKey(serviceId: String, value: String)

    fun updateEnabled(serviceId: String, enabled: Boolean): Boolean

    fun updateApiProtocol(serviceId: String, protocol: ApiProtocol)

    fun updateBaseUrl(serviceId: String, value: String)

    suspend fun addModel(serviceId: String, model: Model)

    suspend fun removeCatalogModel(serviceId: String, groupId: String, modelId: String)

    suspend fun replaceRemoteModels(serviceId: String, models: List<Model>)

    fun selectAssistantModel(selection: ModelSelection)

    fun selectFastModel(selection: ModelSelection)

    fun selectSpeechModel(selection: ModelSelection)

    fun selectTtsModel(selection: ModelSelection)

    fun selectTtsVoice(voiceId: String)
}

interface ModelServiceRemoteGateway {
    suspend fun validateConnection(service: ModelService): Boolean

    suspend fun fetchModels(service: ModelService): List<Model>
}
