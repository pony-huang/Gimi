package github.ponyhuang.gimi.domain.modelcatalog.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import kotlinx.coroutines.flow.Flow

interface ModelCatalogRepository {
    suspend fun awaitReady()

    fun observeService(serviceId: String): Flow<LLMModelSetting?>

    fun observeServices(): Flow<List<LLMModelSetting>>

    fun observeLoadState(): Flow<CatalogLoadState>

    fun observeAssistantSelection(): Flow<ModelSelection?>

    fun observeFastSelection(): Flow<ModelSelection?>

    fun observeSpeechSelection(): Flow<ModelSelection?>

    fun observeTtsSelection(): Flow<ModelSelection?>

    fun observeTtsVoice(): Flow<String>

    fun currentService(serviceId: String): LLMModelSetting?

    fun currentServices(): List<LLMModelSetting>

    fun currentAssistantSelection(): ModelSelection?

    fun currentSpeechSelection(): ModelSelection?

    fun currentTtsSelection(): ModelSelection?

    fun currentTtsVoice(): String

    /** Process-local selection used by the active agent; this is not a persisted default. */
    fun setRuntimeSelection(selection: ModelSelection?)

    /** 仅读取候选目录，不改变已选择的模型或运行时配置。 */
    suspend fun modelCandidates(serviceId: String): List<ModelGroup>

    /** 一次提交连接、模型和工具偏好；失败时保持原配置。 */
    suspend fun saveService(service: LLMModelSetting): Boolean

    /** 暂停保留连接、模型和工具偏好。 */
    suspend fun setServiceEnabled(serviceId: String, enabled: Boolean): Boolean

    /** 清除用户配置和凭据，保留厂商目录。 */
    suspend fun deleteService(serviceId: String): Boolean

    fun selectAssistantModel(selection: ModelSelection)

    fun selectFastModel(selection: ModelSelection)

    fun selectSpeechModel(selection: ModelSelection)

    fun selectTtsModel(selection: ModelSelection)

    fun selectTtsVoice(voiceId: String)
}

interface ModelServiceRemoteGateway {
    suspend fun validateConnection(service: LLMModelSetting): Boolean

    suspend fun fetchModels(service: LLMModelSetting): List<Model>
}
