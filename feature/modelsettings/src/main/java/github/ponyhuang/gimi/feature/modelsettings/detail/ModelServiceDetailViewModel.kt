package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceTemplate
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.modelcatalog.usecase.DeletionImpactUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.DeleteModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelCandidatesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveNewModelInstanceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SetModelInstanceEnabledUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.UpdateDefaultModelSettingsUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConfiguration
import github.ponyhuang.gimi.domain.modelcatalog.usecase.toConnection
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.feature.modelsettings.R
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 服务详情编辑器：连接、模型、厂商工具与启停共享一份草稿。
 *
 * 启停是独立提交的操作，保存草稿不携带启用状态；模型管理页的工作集在「确认选择」时
 * 写回草稿；连接测试结果绑定草稿版本，凭据或端点修改后自动失效。
 */
@HiltViewModel
class ModelServiceDetailViewModel @Inject constructor(
    private val officialToolSupport: OfficialToolSupport,
    private val modelCatalog: ModelCatalogRepository,
    private val loadInstance: LoadModelInstanceUseCase,
    private val observeInstance: ObserveModelInstanceUseCase,
    private val saveNewInstance: SaveNewModelInstanceUseCase,
    private val saveInstance: SaveModelInstanceUseCase,
    private val setInstanceEnabled: SetModelInstanceEnabledUseCase,
    private val deleteInstance: DeleteModelInstanceUseCase,
    private val deletionImpact: DeletionImpactUseCase,
    private val updateDefaults: UpdateDefaultModelSettingsUseCase,
    private val testConnection: TestModelServiceConnectionUseCase,
    private val fetchCatalog: RefreshModelCatalogUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val voiceCapabilities: VoiceCapabilityResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ModelServiceDetailUiState())
    val uiState = _uiState.asStateFlow()
    // replay=0：离开组合的页面重新进入时不得重放旧导航事件（曾导致导航栈反复叠层）。
    private val _effects = MutableSharedFlow<ModelServiceDetailEffect>(extraBufferCapacity = 8)
    val effects = _effects.asSharedFlow()

    private var instanceId: String? = null
    private var loadJob: Job? = null
    private var fetchJob: Job? = null
    private val testJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            runWhenAgentIdle.state.collect { runtime ->
                _uiState.update { it.copy(isMutationBlocked = runtime.isBusy) }
            }
        }
    }

    fun onAction(action: ModelServiceDetailAction) {
        val state = _uiState.value
        if (state.isClosing && action !is ModelServiceDetailAction.Load) return
        when (action) {
            is ModelServiceDetailAction.Load -> load(action.instanceId, action.templateId, action.origin)
            is ModelServiceDetailAction.NameChanged -> editDraft { it.copy(name = action.value) }
            is ModelServiceDetailAction.SharedApiKeyChanged -> editDraft { it.copy(sharedApiKey = action.value) }
            ModelServiceDetailAction.ToggleApiKeyVisibility ->
                _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
            ModelServiceDetailAction.ToggleConnectionKeyVisibility ->
                _uiState.update { it.copy(isConnectionKeyVisible = !it.isConnectionKeyVisible) }
            is ModelServiceDetailAction.EnabledChanged -> changeEnabled(action.enabled)
            is ModelServiceDetailAction.OfficialToolsEnabledChanged -> editDraft {
                it.copy(isOfficialToolsEnabled = action.value)
            }
            is ModelServiceDetailAction.OpenPage -> openPage(action.page)
            is ModelServiceDetailAction.ModelQueryChanged ->
                _uiState.update { it.copy(modelQuery = action.value) }
            is ModelServiceDetailAction.SelectCategory ->
                _uiState.update { it.copy(selectedCategory = action.capability) }
            is ModelServiceDetailAction.ToggleModel -> if (state.canEdit && state.candidates.any { it.key == action.key }) {
                _uiState.update {
                    it.copy(pendingModelKeys = if (action.key in it.pendingModelKeys) {
                        it.pendingModelKeys - action.key
                    } else {
                        it.pendingModelKeys + action.key
                    })
                }
            }
            ModelServiceDetailAction.ConfirmModels -> confirmModels()
            ModelServiceDetailAction.ShowAddDialog -> if (state.canEdit) {
                _uiState.update { it.copy(isAddDialogVisible = true, newModelId = "") }
            }
            ModelServiceDetailAction.DismissAddDialog ->
                _uiState.update { it.copy(isAddDialogVisible = false, newModelId = "") }
            is ModelServiceDetailAction.NewModelIdChanged ->
                _uiState.update { it.copy(newModelId = action.value) }
            is ModelServiceDetailAction.NewModelKindChanged ->
                _uiState.update { it.copy(newModelKind = action.value) }
            ModelServiceDetailAction.ConfirmAddModel -> addModel()
            ModelServiceDetailAction.FetchModels -> fetchModels()
            is ModelServiceDetailAction.ConnectionProtocolChanged ->
                changeConnection(action.connectionId) { connection, template ->
                    val templateConnection = template?.connection(connection.capability)
                    if (templateConnection != null && action.protocol !in templateConnection.protocols) {
                        connection
                    } else {
                        // 协议切换回落到该协议的模板默认端点；VendorManaged 端点在保存期回贴。
                        val endpoint = if (templateConnection?.endpointPolicy == EndpointPolicy.UserConfigured) {
                            templateConnection.defaultEndpoint(action.protocol)
                        } else {
                            connection.endpoint
                        }
                        connection.copy(protocol = action.protocol, endpoint = endpoint)
                    }
                }
            is ModelServiceDetailAction.ConnectionEndpointChanged ->
                changeConnection(action.connectionId) { connection, _ -> connection.copy(endpoint = action.endpoint) }
            is ModelServiceDetailAction.ConnectionCredentialSharedChanged ->
                changeConnection(action.connectionId) { connection, _ ->
                    connection.copy(
                        credential = if (action.shared) {
                            CredentialSource.Shared
                        } else {
                            CredentialSource.Independent("")
                        },
                    )
                }
            is ModelServiceDetailAction.ConnectionIndependentKeyChanged ->
                changeConnection(action.connectionId) { connection, _ ->
                    connection.copy(credential = CredentialSource.Independent(action.apiKey))
                }
            is ModelServiceDetailAction.ConnectionWorkspaceChanged ->
                changeConnection(action.connectionId) { connection, _ ->
                    connection.copy(workspaceId = action.workspaceId.ifBlank { null })
                }
            is ModelServiceDetailAction.TestConnection -> testConnection(action.connectionId)
            ModelServiceDetailAction.RequestDelete -> requestDelete()
            ModelServiceDetailAction.DismissDelete -> _uiState.update { it.copy(deletionImpact = null) }
            ModelServiceDetailAction.ConfirmDelete -> confirmDelete()
            ModelServiceDetailAction.Save -> startSave()
            is ModelServiceDetailAction.ToggleSuggestion -> _uiState.update {
                it.copy(
                    acceptedSuggestionRoles = if (action.accepted) {
                        it.acceptedSuggestionRoles + action.role
                    } else {
                        it.acceptedSuggestionRoles - action.role
                    },
                )
            }
            ModelServiceDetailAction.ConfirmSuggestions ->
                saveWithSuggestions(_uiState.value.acceptedSuggestionRoles)
            ModelServiceDetailAction.SkipSuggestions -> performSave()
            ModelServiceDetailAction.Back -> back()
            ModelServiceDetailAction.Discard -> viewModelScope.launch { close() }
            ModelServiceDetailAction.ContinueEditing ->
                _uiState.update { it.copy(isDiscardDialogVisible = false) }
        }
    }

    private fun load(instanceIdValue: String?, templateId: String?, origin: DefaultModelRole?) {
        if (instanceId == instanceIdValue && loadJob?.isActive == true) return
        loadJob?.cancel()
        cancelTransientOperations()
        instanceId = instanceIdValue
        _uiState.value = ModelServiceDetailUiState(isMutationBlocked = _uiState.value.isMutationBlocked)
        loadJob = viewModelScope.launch {
            try {
                if (instanceIdValue == null) {
                    loadNewInstance(templateId, origin)
                } else {
                    loadExistingInstance(instanceIdValue, origin)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, errorRes = R.string.modelsettings_list_load_failed) }
            }
        }
    }

    /** 新实例草稿：模板连接全铺开（用户增删模型），自定义服务只建一条对话连接。 */
    private suspend fun loadNewInstance(templateId: String?, origin: DefaultModelRole?) {
        val template = templateId?.let { modelCatalog.template(it) }
        val draft = ModelServiceDraft(
            instanceId = null,
            templateId = templateId,
            name = template?.name.orEmpty(),
            isOfficialToolsEnabled = template != null &&
                template.id in ModelServiceDraft.OFFICIAL_TOOL_TEMPLATE_IDS,
            sharedApiKey = "",
            connections = template?.connections?.map { it.toConnection() }
                ?: listOf(customConversationConnection()),
        )
        publishDraft(draft, template, isNew = true, origin = origin, isEnabled = true)
    }

    private suspend fun loadExistingInstance(id: String, origin: DefaultModelRole?) {
        val saved = loadInstance(id)
        if (saved == null) {
            _uiState.update { it.copy(isLoading = false) }
            _effects.tryEmit(ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.InstanceNotFound))
            close()
            return
        }
        val template = saved.templateId?.let { modelCatalog.template(it) }
        val draft = ModelServiceDraft(
            instanceId = saved.id,
            templateId = saved.templateId,
            name = saved.name,
            isOfficialToolsEnabled = saved.isOfficialToolsEnabled,
            sharedApiKey = saved.sharedApiKey,
            connections = saved.connections,
        )
        publishDraft(draft, template, isNew = false, origin = origin, isEnabled = saved.isEnabled)
        // 外部启停/保存回流不得覆盖未保存草稿；只同步启停显示状态。
        observeInstance(id).collect { instance ->
            _uiState.update { state ->
                when {
                    instance == null || state.isSaving -> state
                    else -> state.copy(isEnabled = instance.isEnabled)
                }
            }
        }
    }

    private fun publishDraft(
        draft: ModelServiceDraft,
        template: ModelServiceTemplate?,
        isNew: Boolean,
        origin: DefaultModelRole?,
        isEnabled: Boolean,
    ) {
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                isNewInstance = isNew,
                originRole = origin,
                template = template,
                draft = draft,
                baseline = draft,
                isEnabled = isEnabled,
            ).withDraftSections(template)
        }
    }

    private fun editDraft(transform: (ModelServiceDraft) -> ModelServiceDraft) {
        if (!_uiState.value.canEdit) return
        cancelTransientOperations()
        _uiState.update { state ->
            val next = state.draft?.let(transform)
            state.copy(draft = next, errorRes = null).withDraftSections(state.template)
        }
    }

    /** 修改单条连接；连接测试结果随草稿版本失效。 */
    private fun changeConnection(
        connectionId: String,
        transform: (ServiceConnection, ModelServiceTemplate?) -> ServiceConnection,
    ) {
        if (!_uiState.value.canEdit) return
        cancelTransientOperations()
        _uiState.update { state ->
            val template = state.template
            val next = state.draft?.copy(
                connections = state.draft.connections.map { connection ->
                    if (connection.id == connectionId) transform(connection, template) else connection
                },
            )
            state.copy(draft = next).withDraftSections(template)
        }
    }

    /** 由草稿重算子页投影：候选目录、连接编辑视图与工具矩阵。 */
    private fun ModelServiceDetailUiState.withDraftSections(
        template: ModelServiceTemplate?,
    ): ModelServiceDetailUiState {
        val currentDraft = draft ?: return this
        val connectionRows = currentDraft.connections.map { connection ->
            val templateConnection = template?.connection(connection.capability)
            ConnectionEditor(
                connection = connection,
                endpointPolicy = templateConnection?.endpointPolicy ?: EndpointPolicy.UserConfigured,
                endpointSchemes = templateConnection?.endpointSchemes ?: setOf("http", "https"),
                supportsWorkspace = templateConnection?.supportsWorkspace == true,
                // 自定义服务没有模板约束：对话连接开放 OpenAI 兼容与 Anthropic 两种协议。
                protocols = templateConnection?.protocols
                    ?: if (connection.capability == ServiceCapability.Conversation) {
                        listOf(ApiProtocol.OpenaiChatcompletions, ApiProtocol.Anthropic)
                    } else {
                        listOf(connection.protocol)
                    },
                test = connections.firstOrNull { it.connection.id == connection.id }?.test
                    ?: ConnectionTestState(),
            )
        }
        val conversationProtocol = currentDraft.conversationConnection?.protocol
        val templateId = currentDraft.templateId
        val toolRows = if (currentDraft.supportsOfficialTools && templateId != null) {
            // 工具页展示模板级能力矩阵；协议限制逐行标注，运行期再按模型家族收窄。
            officialToolSupport.all.filter { it.templateId == templateId }.map { spec ->
                OfficialToolRow(
                    spec = spec,
                    isSupportedByProtocol = conversationProtocol in spec.protocols,
                )
            }
        } else {
            emptyList()
        }
        // 候选目录：模板内置目录 + 草稿已选模型 + 会话级增量（刷新/手动），按连接归属。
        val candidateRows = currentDraft.connections.flatMap { connection ->
            val templateCatalog = template?.connection(connection.capability)?.catalog.orEmpty()
            (templateCatalog + connection.models).distinctBy { it.id }.map { model ->
                ModelCandidateRow(connection.id, connection.capability, model)
            }
        }
        val staleExtras = extraCandidates.filterNot { extra ->
            candidateRows.any { it.key == extra.key }
        }
        return copy(
            connectionTestVersion = connectionTestVersion + 1,
            connections = connectionRows.map { editor ->
                // 草稿变化使既有测试结果失效；结果对象保留供版本比对。
                editor.copy(test = editor.test.copy(draftVersion = -1L))
            },
            supportedTools = toolRows,
            candidates = candidateRows + staleExtras,
            extraCandidates = staleExtras,
        )
    }

    private fun cancelTransientOperations() {
        fetchJob?.cancel()
        fetchJob = null
        testJobs.values.forEach { it.cancel() }
        testJobs.clear()
        _uiState.update {
            it.copy(
                isRefreshing = false,
                connections = it.connections.map { editor ->
                    if (editor.test.isRunning) editor.copy(test = ConnectionTestState()) else editor
                },
            )
        }
    }

    private fun openPage(page: ServiceEditorPage) {
        if (_uiState.value.isSaving) return
        _uiState.update { state ->
            when (page) {
                ServiceEditorPage.Models -> state.copy(
                    page = page,
                    errorRes = null,
                    pendingModelKeys = state.candidates.mapTo(mutableSetOf()) { it.key },
                )
                else -> state.copy(page = page, errorRes = null)
            }
        }
    }

    /** 模型管理页确认：按连接把工作集写回草稿。 */
    private fun confirmModels() {
        val state = _uiState.value
        if (!state.canEdit) return
        val selected = state.candidates.filter { it.key in state.pendingModelKeys }
        editDraft { draft ->
            draft.copy(
                connections = draft.connections.map { connection ->
                    val rows = selected.filter { it.connectionId == connection.id }
                    when {
                        // 对话连接清空交由保存校验拦截；语音连接清空即该能力未配置。
                        rows.isEmpty() -> connection.copy(models = emptyList())
                        else -> connection.copy(models = rows.map { it.model })
                    }
                },
            )
        }
        _uiState.update { it.copy(page = ServiceEditorPage.Detail) }
    }

    private fun addModel() {
        val state = _uiState.value
        val id = state.newModelId.trim()
        val draft = state.draft ?: return
        if (!state.canEdit || id.isBlank()) return
        val capability = state.newModelKind.capability
        val targetConnection = draft.connection(capability) ?: return
        val existing = state.candidates.firstOrNull {
            it.model.id == id && it.connectionId == targetConnection.id
        }
        if (existing != null) {
            // 已在候选中的手动项直接纳入选择工作集。
            _uiState.update {
                it.copy(pendingModelKeys = it.pendingModelKeys + existing.key, isAddDialogVisible = false, newModelId = "")
            }
            return
        }
        val row = ModelCandidateRow(
            connectionId = targetConnection.id,
            capability = capability,
            model = Model(id, id, capabilities = state.newModelKind.capabilities),
        )
        _uiState.update {
            it.copy(
                candidates = it.candidates + row,
                extraCandidates = it.extraCandidates + row,
                pendingModelKeys = it.pendingModelKeys + row.key,
                isAddDialogVisible = false,
                newModelId = "",
            )
        }
    }

    /** 获取对话连接的远端候选；只补充候选视图，不改变已选模型。 */
    private fun fetchModels() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isRefreshing || !state.canEdit) return
        val connection = draft.conversationConnection ?: return
        val view = draftResolvedView(connection) ?: run {
            _uiState.update { it.copy(errorRes = R.string.modelsettings_invalid_connection) }
            return
        }
        fetchJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorRes = null) }
            try {
                fetchCatalog(view).fold(
                    onSuccess = { models ->
                        val valid = models.filter { it.id.isNotBlank() }.distinctBy { it.id }
                        _uiState.update { current ->
                            val rows = valid.map { model ->
                                ModelCandidateRow(connection.id, ServiceCapability.Conversation, model)
                            }
                            current.copy(
                                candidates = (current.candidates + rows).distinctBy { it.key },
                                extraCandidates = (current.extraCandidates + rows).distinctBy { it.key },
                                errorRes = if (valid.isEmpty()) R.string.modelsettings_no_remote_models else null,
                            )
                        }
                        _effects.tryEmit(
                            ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.ModelsFetched(valid.size)),
                        )
                    },
                    onFailure = {
                        _uiState.update { it.copy(errorRes = R.string.modelsettings_notice_models_sync_failed) }
                    },
                )
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** 构造草稿连接的测试视图；端点或凭据不完整时返回 null。 */
    private fun draftResolvedView(connection: ServiceConnection): ResolvedServiceConnection? {
        val draft = _uiState.value.draft ?: return null
        val apiKey = draft.effectiveApiKey(connection)
        if (apiKey.isBlank() || connection.endpoint.isBlank()) return null
        return ResolvedServiceConnection(
            instanceId = draft.instanceId.orEmpty(),
            templateId = draft.templateId,
            connectionId = connection.id,
            capability = connection.capability,
            protocol = connection.protocol,
            endpoint = connection.endpoint,
            apiKey = apiKey,
            workspaceId = connection.workspaceId,
            model = connection.models.firstOrNull() ?: PLACEHOLDER_MODEL,
        )
    }

    private fun testConnection(connectionId: String) {
        val state = _uiState.value
        val draft = state.draft ?: return
        val connection = draft.connection(connectionId) ?: return
        if (testJobs[connectionId]?.isActive == true) return
        val view = draftResolvedView(connection) ?: run {
            markTestResult(connectionId, ServiceConnectionResult.NetworkFailed)
            return
        }
        val job = viewModelScope.launch {
            markTestRunning(connectionId)
            val result = if (connection.capability == ServiceCapability.Conversation) {
                testConnection(view)
            } else {
                // 语音连接按模板握手探测；未实现探测的模板明确报告不支持。
                try {
                    val templateId = checkNotNull(view.templateId)
                    voiceCapabilities.connectionProbe(templateId).probe(view)
                } catch (error: VoiceCapabilityNotSupported) {
                    ServiceConnectionResult.Unsupported
                }
            }
            markTestResult(connectionId, result)
        }
        testJobs[connectionId] = job
    }

    private fun markTestRunning(connectionId: String) {
        _uiState.update { state ->
            state.copy(
                connections = state.connections.map { editor ->
                    if (editor.connection.id == connectionId) {
                        editor.copy(
                            test = ConnectionTestState(
                                draftVersion = state.connectionTestVersion,
                                isRunning = true,
                            ),
                        )
                    } else {
                        editor
                    }
                },
            )
        }
    }

    private fun markTestResult(connectionId: String, result: ServiceConnectionResult) {
        _uiState.update { state ->
            state.copy(
                connections = state.connections.map { editor ->
                    if (editor.connection.id == connectionId) {
                        editor.copy(
                            test = ConnectionTestState(
                                draftVersion = state.connectionTestVersion,
                                isRunning = false,
                                result = result,
                            ),
                        )
                    } else {
                        editor
                    }
                },
            )
        }
    }

    private fun changeEnabled(enabled: Boolean) {
        val state = _uiState.value
        val id = state.draft?.instanceId ?: return
        if (state.isMutationBlocked || state.isSaving || state.isEnabled == enabled) return
        viewModelScope.launch {
            try {
                when (val result = runWhenAgentIdle { setInstanceEnabled(id, enabled) }) {
                    AgentMutationResult.BlockedByActiveAgent ->
                        _effects.tryEmit(
                            ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.AgentMutationBlocked),
                        )
                    is AgentMutationResult.Applied -> if (!result.value) {
                        _uiState.update { it.copy(errorRes = R.string.modelsettings_write_failed) }
                    }
                    // 启停状态由仓库观察回流同步。
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.modelsettings_write_failed) }
            }
        }
    }

    private fun requestDelete() {
        val state = _uiState.value
        val id = state.draft?.instanceId ?: return
        if (!state.canDelete || state.isSaving) return
        viewModelScope.launch {
            val impact = deletionImpact(id)
            _uiState.update { it.copy(deletionImpact = impact) }
        }
    }

    private fun confirmDelete() {
        val state = _uiState.value
        val id = state.draft?.instanceId ?: return
        if (state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                when (val result = runWhenAgentIdle { deleteInstance(id) }) {
                    AgentMutationResult.BlockedByActiveAgent -> {
                        _uiState.update { it.copy(isSaving = false, deletionImpact = null) }
                        _effects.tryEmit(
                            ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.AgentMutationBlocked),
                        )
                    }
                    is AgentMutationResult.Applied -> if (result.value) {
                        _uiState.update { it.copy(deletionImpact = null) }
                        _effects.tryEmit(ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.Deleted))
                        _effects.tryEmit(ModelServiceDetailEffect.Deleted)
                    } else {
                        _uiState.update { it.copy(isSaving = false, errorRes = R.string.modelsettings_write_failed) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false, errorRes = R.string.modelsettings_write_failed) }
            }
        }
    }

    /** 保存入口：校验后按普通添加流程确认空用途建议（用途来源流程跳过）。 */
    private fun startSave() {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isSaving) return
        if (!draft.hasValidConfiguration(state.template)) {
            _uiState.update { it.copy(errorRes = R.string.modelsettings_invalid_connection) }
            return
        }
        val suggestions = if (state.originRole == null) emptyRoleSuggestions(state) else emptyList()
        if (suggestions.isEmpty()) {
            performSave()
        } else {
            _uiState.update {
                it.copy(
                    suggestions = suggestions,
                    acceptedSuggestionRoles = suggestions.mapTo(mutableSetOf()) { suggestion -> suggestion.role },
                    page = ServiceEditorPage.Suggestions,
                )
            }
        }
    }

    private fun saveWithSuggestions(acceptedRoles: Set<DefaultModelRole>) {
        val suggestions = _uiState.value.suggestions.orEmpty()
        performSave { savedInstanceId ->
            // 已确认的建议在服务保存成功后逐项写入默认分配。
            suggestions.filter { it.role in acceptedRoles }.forEach { suggestion ->
                val selection = suggestion.selection.copy(instanceId = savedInstanceId)
                when (suggestion.role) {
                    DefaultModelRole.Assistant -> updateDefaults.assistant(selection)
                    DefaultModelRole.Fast -> updateDefaults.fast(selection)
                    DefaultModelRole.SpeechRecognition -> updateDefaults.speech(selection)
                    DefaultModelRole.SpeechSynthesis -> updateDefaults.tts(selection)
                    DefaultModelRole.Realtime -> updateDefaults.realtimeModel(selection)
                }
            }
        }
    }

    private fun performSave(onSaved: suspend (String) -> Unit = {}) {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (state.isSaving) return
        if (!draft.hasValidConfiguration(state.template)) {
            _uiState.update { it.copy(errorRes = R.string.modelsettings_invalid_connection) }
            return
        }
        cancelTransientOperations()
        _uiState.update { it.copy(isSaving = true, errorRes = null, isDiscardDialogVisible = false) }
        viewModelScope.launch {
            try {
                val template = state.template
                when (val result = runWhenAgentIdle {
                    if (draft.instanceId == null) {
                        saveNewInstance(draft, template)?.id
                    } else {
                        saveInstance(draft, template)
                    }
                }) {
                    AgentMutationResult.BlockedByActiveAgent -> _effects.tryEmit(
                        ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.AgentMutationBlocked),
                    )
                    is AgentMutationResult.Applied -> when (val value = result.value) {
                        is String -> {
                            adoptSavedInstance(value)
                            _effects.tryEmit(ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.Saved))
                            onSaved(value)
                            // 新实例：连弹两层（详情 + 添加目录）回服务列表；用途来源流程由 Route 转进默认模型页。
                            _effects.tryEmit(
                                ModelServiceDetailEffect.Saved(
                                    instanceId = value,
                                    popToServiceList = _uiState.value.originRole == null,
                                ),
                            )
                        }
                        true -> {
                            // 编辑已有实例：原地停留，不向导航栈压新条目；与新实例同样就地
                            // 收编归一化后的已保存实例重建基线，否则 draft 仍领先 baseline，
                            // 保存成功后返回会误报「放弃未保存的修改」。
                            draft.instanceId?.let(::adoptSavedInstance)
                            _effects.tryEmit(ModelServiceDetailEffect.ShowToast(ModelServiceDetailNotice.Saved))
                        }
                        else -> _uiState.update { it.copy(errorRes = R.string.modelsettings_write_failed) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(errorRes = R.string.modelsettings_write_failed) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * 原地收编保存结果：VM 拿到实例 ID 并以归一化后的已保存实例重建草稿与基线。
     * 否则 VM 仍持有 null 实例 ID，再次保存会重复建实例；用途来源流程返回后
     * 详情仍在栈内，也必须呈现已保存状态。
     */
    private fun adoptSavedInstance(savedInstanceId: String) {
        instanceId = savedInstanceId
        val saved = modelCatalog.currentInstance(savedInstanceId) ?: return
        val template = saved.templateId?.let { modelCatalog.template(it) }
        val adopted = ModelServiceDraft(
            instanceId = saved.id,
            templateId = saved.templateId,
            name = saved.name,
            isOfficialToolsEnabled = saved.isOfficialToolsEnabled,
            sharedApiKey = saved.sharedApiKey,
            connections = saved.connections,
        )
        _uiState.update {
            it.copy(draft = adopted, baseline = adopted, isNewInstance = false).withDraftSections(template)
        }
    }

    /** 空用途建议：默认未配置且本实例已选对应能力模型的角色。 */
    private fun emptyRoleSuggestions(state: ModelServiceDetailUiState): List<DefaultModelSuggestion> {
        val draft = state.draft ?: return emptyList()
        val assistant = modelCatalog.currentAssistantSelection()
        val fast = modelCatalog.currentFastSelection()
        val speech = modelCatalog.currentSpeechSelection()
        val tts = modelCatalog.currentTtsSelection()
        val realtime = modelCatalog.currentRealtimeSelection()
        return buildList {
            draft.conversationConnection?.models
                ?.firstOrNull { ModelCapability.LLM in it.capabilities }
                ?.let { model ->
                    if (assistant == null) add(suggestion(DefaultModelRole.Assistant, draft, model))
                    if (fast == null) add(suggestion(DefaultModelRole.Fast, draft, model))
                }
            draft.connection(ServiceCapability.Recognition)?.models?.firstOrNull()
                ?.takeIf { speech == null }
                ?.let { add(suggestion(DefaultModelRole.SpeechRecognition, draft, it)) }
            draft.connection(ServiceCapability.Synthesis)?.models?.firstOrNull()
                ?.takeIf { tts == null }
                ?.let { add(suggestion(DefaultModelRole.SpeechSynthesis, draft, it)) }
            draft.connection(ServiceCapability.Realtime)?.models?.firstOrNull()
                ?.takeIf { ModelCapability.Realtime in it.capabilities && realtime == null }
                ?.let { add(suggestion(DefaultModelRole.Realtime, draft, it)) }
        }
    }

    private fun suggestion(
        role: DefaultModelRole,
        draft: ModelServiceDraft,
        model: Model,
    ): DefaultModelSuggestion {
        val connection = when (role) {
            DefaultModelRole.Assistant, DefaultModelRole.Fast -> draft.conversationConnection
            DefaultModelRole.SpeechRecognition -> draft.connection(ServiceCapability.Recognition)
            DefaultModelRole.SpeechSynthesis -> draft.connection(ServiceCapability.Synthesis)
            DefaultModelRole.Realtime -> draft.connection(ServiceCapability.Realtime)
        } ?: error("Suggestion role $role without a matching connection")
        return DefaultModelSuggestion(
            role = role,
            roleLabelRes = role.labelRes,
            selection = ModelSelection(
                instanceId = draft.instanceId.orEmpty(),
                connectionId = connection.id,
                modelId = model.id,
            ),
            modelLabel = model.name.ifBlank { model.id },
        )
    }

    private fun back() {
        val state = _uiState.value
        if (state.isSaving) return
        when {
            state.page == ServiceEditorPage.Suggestions ->
                _uiState.update { it.copy(page = ServiceEditorPage.Detail, suggestions = null) }
            state.page != ServiceEditorPage.Detail ->
                _uiState.update { it.copy(page = ServiceEditorPage.Detail, errorRes = null) }
            state.isDirty -> _uiState.update { it.copy(isDiscardDialogVisible = true) }
            else -> viewModelScope.launch { close() }
        }
    }

    private suspend fun close() {
        if (_uiState.value.isClosing) return
        _uiState.update { it.copy(isClosing = true) }
        _effects.tryEmit(ModelServiceDetailEffect.Close)
    }

    private companion object {
        /** 手动候选的占位模型：连接测试只校验端点与凭据，不校验模型可用性。 */
        val PLACEHOLDER_MODEL = Model("placeholder", "placeholder")

        /** 自定义服务唯一的对话连接草稿。 */
        fun customConversationConnection() = ServiceConnection(
            id = "conn-conversation-custom",
            capability = ServiceCapability.Conversation,
            protocol = ApiProtocol.OpenaiChatcompletions,
            endpoint = "",
        )
    }
}

/** 用途的展示标签资源。 */
val DefaultModelRole.labelRes: Int
    get() = when (this) {
        DefaultModelRole.Assistant -> R.string.modelsettings_role_assistant
        DefaultModelRole.Fast -> R.string.modelsettings_role_fast
        DefaultModelRole.SpeechRecognition -> R.string.modelsettings_role_recognition
        DefaultModelRole.SpeechSynthesis -> R.string.modelsettings_role_synthesis
        DefaultModelRole.Realtime -> R.string.modelsettings_role_realtime
    }
