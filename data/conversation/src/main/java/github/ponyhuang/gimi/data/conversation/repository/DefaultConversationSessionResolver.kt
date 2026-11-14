package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionSnapshot
import github.ponyhuang.gimi.domain.conversation.repository.NoAvailableAssistantModelException
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelectionCodec
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [ConversationSessionResolver] 的进程级实现，保证恢复和创建不会并发生成多个当前会话。 */
@Singleton
class DefaultConversationSessionResolver @Inject constructor(
    private val conversations: ConversationRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val mcpRepository: McpRepository,
    private val officialTools: OfficialToolFunctionCatalog,
) : ConversationSessionResolver {
    private val mutex = Mutex()

    override suspend fun resolveCurrentOrCreate(): ConversationSessionSnapshot = mutex.withLock {
        modelCatalog.awaitReady()
        val remembered = conversations.lastConversationId()
        if (!remembered.isNullOrBlank()) {
            resolveExisting(remembered)?.let { return@withLock it }
            conversations.discardConversationMetadata(remembered)
        }
        conversations.listConversations().firstNotNullOfOrNull { conversation ->
            resolveExisting(conversation.id)
        } ?: create()
    }

    override suspend fun createAndActivate(): ConversationSessionSnapshot = mutex.withLock {
        modelCatalog.awaitReady()
        create()
    }

    override suspend fun activate(sessionId: String): ConversationSessionSnapshot? = mutex.withLock {
        if (sessionId.isBlank()) return@withLock null
        modelCatalog.awaitReady()
        resolveExisting(sessionId)
    }

    private suspend fun resolveExisting(sessionId: String): ConversationSessionSnapshot? {
        if (conversations.loadMessages(sessionId) == null) return null
        val fallback = defaultSelection()
            ?: throw NoAvailableAssistantModelException()
        val storedPayload = conversations.activateConversation(
            sessionId,
            ModelSelectionCodec.encode(fallback),
        )
        val stored = ModelSelectionCodec.decode(storedPayload)
        val selection = stored?.takeIf(::isUsable) ?: fallback
        if (stored != selection) {
            conversations.setConversationModel(sessionId, ModelSelectionCodec.encode(selection))
        }
        val tools = resolveToolConfigurationLocked(sessionId, selection)
        return ConversationSessionSnapshot(sessionId, selection, tools)
    }

    private suspend fun create(): ConversationSessionSnapshot {
        val selection = defaultSelection()
            ?: throw NoAvailableAssistantModelException()
        val tools = defaultToolConfiguration(selection)
        val sessionId = conversations.createConversation(
            initialModel = ModelSelectionCodec.encode(selection),
            activate = true,
            initialToolConfiguration = tools,
        )
        check(sessionId.isNotBlank()) { "Unable to create the current conversation." }
        return ConversationSessionSnapshot(sessionId, selection, tools)
    }

    override suspend fun resolveToolConfiguration(
        sessionId: String,
        modelSelection: ModelSelection,
    ): ConversationToolConfiguration = mutex.withLock {
        resolveToolConfigurationLocked(sessionId, modelSelection)
    }

    private suspend fun resolveToolConfigurationLocked(
        sessionId: String,
        selection: ModelSelection,
    ): ConversationToolConfiguration {
        val stored = conversations.conversationToolConfiguration(sessionId)
        val availableMcpIds = mcpRepository.currentServers().mapTo(hashSetOf()) { it.id }
        val resolved = (stored ?: defaultToolConfiguration(selection))
            .sanitize(availableMcpIds)
            .initializeOfficialFunctions(supportedOfficialToolIds(selection))
        if (stored != resolved) {
            check(conversations.setConversationToolConfiguration(sessionId, resolved)) {
                "Unable to save the conversation tool configuration."
            }
        }
        return resolved
    }

    private fun defaultToolConfiguration(selection: ModelSelection): ConversationToolConfiguration =
        ConversationToolConfiguration(
            enabledMcpServerIds = mcpRepository.currentServers()
                .filter { it.isEnabled }
                .mapTo(linkedSetOf()) { it.id },
            enabledOfficialFunctionIds = supportedOfficialToolIds(selection)
                .associateWith { setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER) },
        )

    private fun defaultSelection(): ModelSelection? {
        val instances = modelCatalog.currentInstances()
        return modelCatalog.currentAssistantSelection()
            ?.takeIf { instances.isUsable(it) }
            ?: instances.asSequence()
                .filter { it.isEnabled }
                .mapNotNull { instance ->
                    instance.conversationConnection?.let { connection ->
                        connection.models.firstOrNull { ModelCapability.LLM in it.capabilities }
                            ?.let { ModelSelection(instance.id, connection.id, it.id) }
                    }
                }
                .firstOrNull()
    }

    /** 当前聊天模型与所有已开启工具实例共同可用的官方工具 ID。 */
    private fun supportedOfficialToolIds(selection: ModelSelection): Set<String> =
        modelCatalog.currentInstances()
            .firstOrNull { it.id == selection.instanceId }
            ?.let { instance ->
                officialTools.availableTools(
                    activeInstance = instance,
                    activeModelId = selection.modelId,
                ).mapTo(linkedSetOf()) { it.toolId }
            }
            .orEmpty()

    private fun isUsable(selection: ModelSelection): Boolean =
        modelCatalog.currentInstances().isUsable(selection)
}

/** 会话可用的选择要求：实例启用、对话凭据可解析、连接与模型存在且可对话。 */
private fun List<ModelServiceInstance>.isUsable(selection: ModelSelection): Boolean {
    val instance = firstOrNull { it.id == selection.instanceId } ?: return false
    if (!instance.isEnabled) return false
    val connection = instance.connection(selection.connectionId) ?: return false
    if (instance.effectiveApiKey(connection).isBlank()) return false
    val model = instance.findModel(selection) ?: return false
    return ModelCapability.LLM in model.capabilities
}
