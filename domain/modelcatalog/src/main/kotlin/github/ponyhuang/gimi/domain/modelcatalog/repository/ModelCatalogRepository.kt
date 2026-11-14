package github.ponyhuang.gimi.domain.modelcatalog.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.DeletionImpact
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import kotlinx.coroutines.flow.Flow

/**
 * 模型服务配置仓库：内置模板目录 + 用户实例 + 默认引用的唯一真源。
 *
 * 实例身份独立于厂商模板身份；保存、启停、删除在同一加密配置快照内原子提交，
 * 失败时保留已有状态。Room 只承载公共模板与候选目录，用户凭据不进 Room。
 */
interface ModelCatalogRepository {
    suspend fun awaitReady()

    fun observeLoadState(): Flow<CatalogLoadState>

    /** 内置模板目录；静态元数据 + 候选模型目录，添加服务目录与模型管理共用。 */
    fun observeTemplates(): Flow<List<ModelServiceTemplate>>

    /** 按 ID 取内置模板；未集成的模板返回 null。 */
    fun template(templateId: String): ModelServiceTemplate?

    fun observeInstances(): Flow<List<ModelServiceInstance>>

    fun observeInstance(instanceId: String): Flow<ModelServiceInstance?>

    fun currentInstances(): List<ModelServiceInstance>

    fun currentInstance(instanceId: String): ModelServiceInstance?

    fun observeAssistantSelection(): Flow<ModelSelection?>

    fun observeFastSelection(): Flow<ModelSelection?>

    fun observeSpeechSelection(): Flow<ModelSelection?>

    fun observeTtsSelection(): Flow<ModelSelection?>

    fun observeTtsVoice(): Flow<String>

    /** Realtime 模式的默认模型；null 表示未配置。 */
    fun observeRealtimeSelection(): Flow<ModelSelection?>

    /** Realtime 会话音色的自定义覆盖；null 表示使用模型缺省音色。 */
    fun observeRealtimeVoice(): Flow<RealtimeVoiceOverride?>

    fun currentAssistantSelection(): ModelSelection?

    fun currentFastSelection(): ModelSelection?

    fun currentSpeechSelection(): ModelSelection?

    fun currentTtsSelection(): ModelSelection?

    fun currentTtsVoice(): String

    /** Realtime 模式的默认模型；null 表示未配置。 */
    fun currentRealtimeSelection(): ModelSelection?

    /** Realtime 会话音色的自定义覆盖；null 表示使用模型缺省音色。 */
    fun currentRealtimeVoice(): RealtimeVoiceOverride?

    /** Process-local selection used by the active agent; this is not a persisted default. */
    fun setRuntimeSelection(selection: ModelSelection?)

    fun selectAssistantModel(selection: ModelSelection)

    fun selectFastModel(selection: ModelSelection)

    fun selectSpeechModel(selection: ModelSelection)

    fun selectTtsModel(selection: ModelSelection)

    fun selectTtsVoice(voiceId: String)

    /** 选择 Realtime 模式的默认模型；null 表示取消配置（Realtime 模式随之不可进入）。 */
    fun selectRealtimeModel(selection: ModelSelection?)

    /** 设置 Realtime 会话音色的自定义覆盖；null 表示回到模型缺省音色。 */
    fun selectRealtimeVoice(override: RealtimeVoiceOverride?)

    /**
     * 保存新实例：分配稳定 ID 并整体落库；返回保存后的完整实例，失败返回 null。
     *
     * 草稿不含启停状态，新实例默认启用与否由仓库按"配置完整"规则决定。
     */
    suspend fun saveNewInstance(draft: ModelServiceDraft): ModelServiceInstance?

    /**
     * 保存既有实例：一次提交连接、凭据、模型与工具偏好；失败时保持原状态。
     *
     * 启用状态不来自草稿——仓库读取存储中的最新值，防止编辑保存覆盖独立提交的启停。
     */
    suspend fun saveInstance(draft: ModelServiceDraft): Boolean

    /** 独立启停；停用保留配置与默认引用，仅标记不可用。 */
    suspend fun setInstanceEnabled(instanceId: String, enabled: Boolean): Boolean

    /** 删除实例，并在同一提交内清理受影响默认分配与绑定音色。 */
    suspend fun deleteInstance(instanceId: String): Boolean

    /** 删除影响查询：引用该实例的默认角色与音色绑定，确认对话框据此展示。 */
    suspend fun deletionImpact(instanceId: String): DeletionImpact

    /** 连接候选目录：模板目录与已选模型合并，供模型管理挑选。 */
    suspend fun modelCandidates(instanceId: String, connectionId: String): List<Model>

    /**
     * 按选择解析连接视图（实例、连接、模型、生效凭据与端点）。
     *
     * 服务停用、凭据为空或模型不存在时返回 null；模型能力校验由调用方按用途执行。
     */
    fun resolveConnection(selection: ModelSelection?): ResolvedServiceConnection?
}

/** 连接测试与候选获取的远端网关；协议实现位于数据层，输入为草稿构建的连接视图。 */
interface ModelServiceRemoteGateway {
    /** 按连接视图探测；连接协议决定探测形态。 */
    suspend fun validateConnection(connection: ResolvedServiceConnection): ServiceConnectionResult

    /** 按连接视图获取候选模型目录；不支持目录接口的连接抛异常。 */
    suspend fun fetchModels(connection: ResolvedServiceConnection): List<Model>
}
