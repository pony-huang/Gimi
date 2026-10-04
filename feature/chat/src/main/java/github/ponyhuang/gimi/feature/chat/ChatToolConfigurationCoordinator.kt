package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ReasoningEffort
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolAvailability
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolFunctionCatalog
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 聊天会话的工具配置协调：目录展示、函数加载、默认 marker 展开与配置持久化。
 *
 * 不另建 UI 状态或会话缓存；共享 ViewModel 的唯一状态源与作用域，通过回调读取
 * 会话运行时并发布配置。目录可跨会话复用，配置写入仍绑定发起操作时的 runtime。
 * Agent 运行、导航及 lease 的生命周期继续由 ViewModel 管理。
 */
internal class ChatToolConfigurationCoordinator(
    private val uiState: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val sessionResolver: ConversationSessionResolver,
    private val modelServices: ModelCatalogRepository,
    private val officialFunctionCatalog: OfficialToolFunctionCatalog,
    private val runtimeFor: (String) -> ChatSessionRuntime,
    private val publishRuntime: (ChatSessionRuntime) -> Unit,
) {
    suspend fun initializeForSelection(
        sessionId: String,
        runtime: ChatSessionRuntime,
        selection: ModelSelection,
    ) {
        val current = runtime.toolConfiguration
            ?: sessionResolver.resolveToolConfiguration(sessionId, selection)
        val initialized = current.initializeOfficialFunctions(
            supportedOfficialToolIds(selection),
        )
        if (initialized != current) {
            if (repository.setConversationToolConfiguration(sessionId, initialized)) {
                runtime.toolConfiguration = initialized
            } else {
                uiState.update {
                    it.copy(hasToolConfigurationError = true)
                }
            }
        } else {
            runtime.toolConfiguration = current
        }
    }

    fun setReasoningEffort(effort: ReasoningEffort) {
        updateToolConfiguration { configuration ->
            configuration.copy(reasoningEffort = effort)
        }
    }

    fun setMcpServerEnabled(serverId: String, enabled: Boolean) {
        updateToolConfiguration { configuration ->
            configuration.copy(
                enabledMcpServerIds = if (enabled) {
                    configuration.enabledMcpServerIds + serverId
                } else {
                    configuration.enabledMcpServerIds - serverId
                },
            )
        }
    }

    /**
     * Toggle a single function of an official tool. The caller passes the
     * current catalog of ids so the marker can be expanded before the write.
     */
    fun setOfficialFunctionEnabled(
        toolId: String,
        functionId: String,
        enabled: Boolean,
        supportedFunctionIds: Set<String>,
    ) {
        if (uiState.value.currentModelSelection == null) return
        updateToolConfiguration { configuration ->
            configuration.setOfficialFunctionEnabled(
                toolId = toolId,
                functionId = functionId,
                supportedFunctionIds = supportedFunctionIds,
                enabled = enabled,
            )
        }
    }

    /**
     * Trigger an async load of the function list for [toolId]. Already loaded
     * tools only re-run their marker expansion (no network call). On success,
     * the configuration's marker entry for the tool is replaced with the real
     * function ids so persistence stays concrete.
     */
    fun loadFunctions(toolId: String) {
        val descriptors = uiState.value.officialToolDescriptors
        val target = descriptors.firstOrNull { it.id == toolId } ?: return
        if (target.isLoadingFunctions) return
        if (target.functions.isNotEmpty() && target.loadError == null) {
            expandMarkerAfterLoad(target)
            return
        }
        fetchAndCacheOfficialToolFunctions(toolId)
    }

    /**
     * Walk every descriptor and, for tools whose configuration still uses the
     * [ConversationToolConfiguration.ALL_FUNCTIONS_MARKER] sentinel, fetch the
     * function list in the background so the marker is expanded to concrete
     * ids without forcing the user to open each sub-page first.
     */
    fun expandPendingMarkers() {
        val configuration = uiState.value.toolConfiguration ?: return
        if (uiState.value.currentModelSelection == null) return
        val descriptors = uiState.value.officialToolDescriptors
        descriptors.forEach { descriptor ->
            val raw = configuration.enabledOfficialFunctionIds(descriptor.id)
            val needsExpansion = ConversationToolConfiguration.ALL_FUNCTIONS_MARKER in raw
            val alreadyLoaded = descriptor.functions.isNotEmpty()
            if (!needsExpansion) return@forEach
            if (alreadyLoaded) {
                expandMarkerAfterLoad(descriptor)
                return@forEach
            }
            if (descriptor.isLoadingFunctions || descriptor.loadError != null) return@forEach
            fetchAndCacheOfficialToolFunctions(descriptor.id)
        }
    }

    private fun fetchAndCacheOfficialToolFunctions(toolId: String) {
        uiState.update { state ->
            state.copy(
                officialToolDescriptors = state.officialToolDescriptors.map { existing ->
                    if (existing.id == toolId) {
                        existing.copy(isLoadingFunctions = true, loadError = null)
                    } else {
                        existing
                    }
                },
            )
        }
        scope.launch {
            val outcome = cancellationAwareRunCatching { officialFunctionCatalog.listFunctions(toolId) }
            val functions = outcome.getOrDefault(emptyList())
            val loadError = outcome.exceptionOrNull()?.message
            uiState.update { state ->
                state.copy(
                    officialToolDescriptors = state.officialToolDescriptors.map { existing ->
                        if (existing.id == toolId) {
                            existing.copy(
                                functions = functions,
                                isLoadingFunctions = false,
                                loadError = loadError,
                            )
                        } else {
                            existing
                        }
                    },
                )
            }
            if (functions.isNotEmpty()) {
                expandMarkerAfterLoad(
                    OfficialToolDescriptor(id = toolId, functions = functions),
                )
            }
        }
    }

    private fun expandMarkerAfterLoad(tool: OfficialToolDescriptor) {
        if (uiState.value.currentModelSelection == null) return
        val configuration = uiState.value.toolConfiguration ?: return
        val ids = configuration.enabledOfficialFunctionIds(tool.id)
        if (ConversationToolConfiguration.ALL_FUNCTIONS_MARKER !in ids) return
        if (tool.functions.isEmpty()) return
        updateToolConfiguration { configuration ->
            configuration.expandOfficialFunctionsMarker(
                tool.id,
                tool.functions.mapTo(hashSetOf()) { it.id },
            )
        }
    }

    fun buildDescriptors(
        selection: ModelSelection?,
        existing: List<OfficialToolDescriptor>,
    ): List<OfficialToolDescriptor> {
        val available = availableOfficialTools(selection)
        if (available.isEmpty()) return emptyList()
        val existingById = existing.associateBy { it.id }
        val services = modelServices.currentServices().associateBy(LLMModelSetting::id)
        return available.map { tool ->
            existingById[tool.toolId]
                ?.takeIf { it.sourceServiceId == tool.serviceId }
                ?: OfficialToolDescriptor(
                    id = tool.toolId,
                    sourceServiceId = tool.serviceId,
                    sourceServiceName = services[tool.serviceId]?.name.orEmpty(),
                )
        }
    }

    fun clearError() {
        uiState.update { it.copy(hasToolConfigurationError = false) }
    }

    private fun updateToolConfiguration(
        transform: (ConversationToolConfiguration) -> ConversationToolConfiguration,
    ) {
        val sessionId = uiState.value.sessionId
        if (sessionId.isBlank()) return
        val runtime = runtimeFor(sessionId)
        if (runtime.isActive) return
        val current = runtime.toolConfiguration ?: return
        val updated = transform(current)
        if (updated == current) return
        scope.launch {
            if (repository.setConversationToolConfiguration(sessionId, updated)) {
                runtime.toolConfiguration = updated
                uiState.update { it.copy(hasToolConfigurationError = false) }
                publishRuntime(runtime)
            } else {
                uiState.update {
                    it.copy(hasToolConfigurationError = true)
                }
            }
        }
    }

    /**
     * 当前选择可用的官方工具，包含当前服务原生工具与其它服务独立 API 工具。
     */
    private fun supportedOfficialToolIds(selection: ModelSelection?): Set<String> {
        return availableOfficialTools(selection).mapTo(linkedSetOf()) { it.toolId }
    }

    private fun availableOfficialTools(
        selection: ModelSelection?,
    ): List<OfficialToolAvailability> {
        val current = selection ?: return emptyList()
        val service = modelServices.currentServices()
            .firstOrNull { it.id == current.serviceId }
            ?: return emptyList()
        return officialFunctionCatalog.availableTools(
            activeService = service,
            activeModelId = current.modelId,
        )
    }
}
