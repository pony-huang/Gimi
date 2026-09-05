package github.ponyhuang.gimi.data.conversation.repository

import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionSnapshot
import github.ponyhuang.gimi.domain.mcp.repository.McpRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelectionCodec
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
            ?: error("No available assistant model.")
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
            ?: error("No available assistant model.")
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
            .initializeOfficialFunctions(
                selection.serviceId,
                supportedOfficialToolIds(selection),
            )
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
            enabledOfficialFunctionIdsByService = mapOf(
                selection.serviceId to supportedOfficialToolIds(selection)
                    .associateWith { setOf(ConversationToolConfiguration.ALL_FUNCTIONS_MARKER) },
            ),
        )

    private fun defaultSelection(): ModelSelection? {
        val services = modelCatalog.currentServices()
        return modelCatalog.currentAssistantSelection()
            ?.takeIf { services.isUsable(it) }
            ?: services.asSequence()
                .filter { it.isEnabled && it.apiKey.isNotBlank() }
                .flatMap { service ->
                    service.groups.asSequence().flatMap { group ->
                        group.models.asSequence()
                            .filter { !it.isStt && !it.isTts }
                            .map { ModelSelection(service.id, group.id, it.id) }
                    }
                }
                .firstOrNull()
    }

    private fun supportedOfficialToolIds(selection: ModelSelection): Set<String> =
        modelCatalog.currentServices()
            .firstOrNull { it.id == selection.serviceId }
            ?.supportedOfficialTools
            ?.toSet()
            .orEmpty()

    private fun isUsable(selection: ModelSelection): Boolean =
        modelCatalog.currentServices().isUsable(selection)
}

private fun List<LLMModelSetting>.isUsable(selection: ModelSelection): Boolean {
    val service = firstOrNull { it.id == selection.serviceId } ?: return false
    if (!service.isEnabled || service.apiKey.isBlank()) return false
    val group = service.groups.firstOrNull { it.id == selection.groupId } ?: return false
    val model = group.models.firstOrNull { it.id == selection.modelId } ?: return false
    return !model.isStt && !model.isTts
}
