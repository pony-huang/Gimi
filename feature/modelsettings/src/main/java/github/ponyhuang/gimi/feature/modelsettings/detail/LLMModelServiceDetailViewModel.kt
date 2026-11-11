package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability

import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnectionResult
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol

import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting

import github.ponyhuang.gimi.domain.modelcatalog.model.Model

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceKind

import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport

import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelCandidatesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityNotSupported
import github.ponyhuang.gimi.domain.voice.repository.VoiceCapabilityResolver
import github.ponyhuang.gimi.feature.modelsettings.R
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ModelServiceDetailViewModel @Inject constructor(
    private val officialToolSupport: OfficialToolSupport,
    private val loadModelService: LoadModelServiceUseCase,
    private val observeModelService: ObserveModelServiceUseCase,
    private val loadCandidates: LoadModelCandidatesUseCase,
    private val saveService: SaveModelServiceUseCase,
    private val testConnection: TestModelServiceConnectionUseCase,
    private val refreshCatalog: RefreshModelCatalogUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
    private val voiceCapabilities: VoiceCapabilityResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LLMModelSettingDetailUiState())
    val uiState = _uiState.asStateFlow()
    private val _effects = Channel<LLMModelSettingDetailEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private var serviceId: String? = null
    private var loadJob: Job? = null
    private var connectionJob: Job? = null
    private var refreshJob: Job? = null
    private var connectionRevision = 0L
    private var catalogModels: Map<String, Model> = emptyMap()

    init {
        viewModelScope.launch {
            runWhenAgentIdle.state.collect { runtime ->
                _uiState.update { it.copy(isMutationBlocked = runtime.isBusy) }
            }
        }
    }

    fun onAction(action: LLMModelSettingDetailAction) {
        val state = _uiState.value
        if (state.isClosing && action !is LLMModelSettingDetailAction.Load) return
        when (action) {
            is LLMModelSettingDetailAction.Load -> load(action.serviceId)
            is LLMModelSettingDetailAction.ApiKeyChanged -> editConnection { it.copy(apiKey = action.value) }
            is LLMModelSettingDetailAction.WorkspaceIdChanged -> editConnection {
                it.copy(workspaceId = action.value.ifBlank { null })
            }
            is LLMModelSettingDetailAction.ApiBaseUrlChanged -> editConnection {
                if (it.apiProtocol == ApiProtocol.Anthropic) it.copy(anthropicBaseUrl = action.value)
                else it.copy(apiBaseUrl = action.value)
            }
            is LLMModelSettingDetailAction.ApiProtocolChanged -> {
                if (action.value in state.service?.supportedProtocols.orEmpty()) {
                    editConnection { it.copy(apiProtocol = action.value) }
                    _uiState.update { it.copy(isProtocolMenuExpanded = false) }
                }
            }
            is LLMModelSettingDetailAction.OfficialToolsEnabledChanged -> edit {
                it.copy(isOfficialToolsEnabled = action.value)
            }
            is LLMModelSettingDetailAction.OpenPage -> openPage(action.page)
            is LLMModelSettingDetailAction.ToggleModel -> if (state.canEdit && state.candidates.any { it.key == action.key }) _uiState.update {
                it.copy(pendingModelKeys = if (action.key in it.pendingModelKeys) it.pendingModelKeys - action.key
                    else it.pendingModelKeys + action.key)
            }
            LLMModelSettingDetailAction.SelectAllModels -> if (state.canEdit) _uiState.update {
                it.copy(pendingModelKeys = it.candidates.mapTo(mutableSetOf()) { row -> row.key })
            }
            LLMModelSettingDetailAction.ClearModels -> if (state.canEdit) _uiState.update { it.copy(pendingModelKeys = emptySet()) }
            LLMModelSettingDetailAction.ToggleConnection -> _uiState.update {
                it.copy(isConnectionExpanded = !it.isConnectionExpanded, isProtocolMenuExpanded = false)
            }
            is LLMModelSettingDetailAction.NewLLMModelIdChanged -> _uiState.update { it.copy(newModelId = action.value) }
            is LLMModelSettingDetailAction.NewLLMModelKindChanged -> if (action.value in state.allowedModelKinds) {
                _uiState.update { it.copy(newModelKind = action.value) }
            }
            LLMModelSettingDetailAction.ToggleApiKeyVisibility -> _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
            LLMModelSettingDetailAction.ToggleProtocolMenu -> if (state.canEdit) _uiState.update { it.copy(isProtocolMenuExpanded = !it.isProtocolMenuExpanded) }
            LLMModelSettingDetailAction.DismissProtocolMenu -> _uiState.update { it.copy(isProtocolMenuExpanded = false) }
            LLMModelSettingDetailAction.ShowAddDialog -> if (state.canEdit) _uiState.update { it.copy(isAddDialogVisible = true) }
            LLMModelSettingDetailAction.DismissAddDialog -> _uiState.update { it.copy(isAddDialogVisible = false, newModelId = "") }
            LLMModelSettingDetailAction.ConfirmAddLLMModel -> addModel()
            LLMModelSettingDetailAction.TestConnection -> testCurrentConnection()
            LLMModelSettingDetailAction.RefreshModels -> refreshModels()
            LLMModelSettingDetailAction.ConfirmModels -> confirmModels()
            LLMModelSettingDetailAction.Save -> save()
            LLMModelSettingDetailAction.Back -> back()
            LLMModelSettingDetailAction.Discard -> viewModelScope.launch { close() }
            LLMModelSettingDetailAction.ContinueEditing -> _uiState.update { it.copy(isDiscardDialogVisible = false) }
        }
    }

    private fun load(id: String) {
        if (serviceId == id && loadJob?.isActive == true) return
        loadJob?.cancel()
        cancelConnectionOperations()
        serviceId = id
        _uiState.value = LLMModelSettingDetailUiState(isMutationBlocked = _uiState.value.isMutationBlocked)
        loadJob = viewModelScope.launch {
            try {
                val initial = loadModelService(id)
                if (initial == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    _effects.send(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.SettingNotFoundLLM))
                    close()
                    return@launch
                }
                val groups = loadCandidates(id)
                catalogModels = (groups + initial.groups).flatMap { it.models }.associateBy { it.id }
                val category = initial.kind
                val draft = initial.editorDraft(category)
                _uiState.update {
                    it.copy(isLoading = false, service = draft, baseline = draft, supportedTools = toolsFor(draft),
                        newModelKind = if (category == ModelServiceKind.Speech) NewModelKind.Stt else NewModelKind.Chat,
                        endpointStrategy = voiceCapabilities.endpointStrategy(draft.id),
                        candidates = (groups + initial.groups).forService(category).toRows().distinctBy { row -> row.key },
                        candidateGroupNames = (groups + initial.groups).associate { group -> group.id to group.name })
                }
                observeModelService(id).collect { service ->
                    // 外部刷新不得覆盖未保存草稿；保存中也不让仓库回流打断表单。
                    _uiState.update { state ->
                        if (service == null || state.isDirty || state.isSaving) state
                        else service.editorDraft(category).let { next ->
                            catalogModels = catalogModels + service.groups.flatMap { it.models }.associateBy { it.id }
                            state.copy(service = next, baseline = next, supportedTools = toolsFor(next),
                                endpointStrategy = voiceCapabilities.endpointStrategy(next.id),
                                candidates = (state.candidates + service.groups.forService(category).toRows()).distinctBy { it.key },
                                candidateGroupNames = state.candidateGroupNames + service.groups.associate { it.id to it.name })
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isLoading = false, errorRes = R.string.modelsettings_list_load_failed) }
            }
        }
    }

    private fun toolsFor(service: LLMModelSetting) = officialToolSupport.all.filter {
        it.serviceId == service.id && service.apiProtocol in it.protocols
    }

    private fun edit(transform: (LLMModelSetting) -> LLMModelSetting) {
        if (!_uiState.value.canEdit) return
        _uiState.update { state ->
            val next = state.service?.let(transform)
            state.copy(service = next, supportedTools = next?.let(::toolsFor).orEmpty(), errorRes = null)
        }
    }

    private fun editConnection(transform: (LLMModelSetting) -> LLMModelSetting) {
        if (!_uiState.value.canEdit) return
        cancelConnectionOperations()
        edit(transform)
    }

    private fun cancelConnectionOperations() {
        connectionRevision++
        connectionJob?.cancel()
        refreshJob?.cancel()
        _uiState.update { it.copy(connectionResult = null, isTestingKey = false, isRefreshing = false) }
    }

    private fun openPage(page: ServiceEditorPage) {
        if (_uiState.value.isSaving) return
        _uiState.update { state ->
            state.copy(page = page, errorRes = null,
                pendingModelKeys = if (page == ServiceEditorPage.Models) state.rows.mapTo(mutableSetOf()) { it.key }
                    else state.pendingModelKeys)
        }
    }

    private fun confirmModels() {
        val state = _uiState.value
        if (!state.canEdit) return
        val selected = state.candidates.filter { it.key in state.pendingModelKeys }
        val names = state.candidateGroupNames
        edit { service -> service.copy(groups = selected.groupBy { it.groupId }.map { (id, rows) ->
            ModelGroup(id, names[id] ?: service.name, rows.map { it.model })
        }) }
        _uiState.update { it.copy(page = ServiceEditorPage.Detail) }
    }

    private fun addModel() {
        val state = _uiState.value
        val id = state.newModelId.trim()
        if (!state.canEdit || id.isBlank() || state.newModelKind !in state.allowedModelKinds) return
        val service = state.service ?: return
        val existing = state.candidates.firstOrNull { it.model.id == id }
        val row = existing ?: LLMModelSettingDetailRow.LLMModelItem(
            "${service.id}-custom",
            Model(id, id, capabilities = state.newModelKind.capabilities),
        )
        _uiState.update {
            it.copy(candidates = if (existing == null) it.candidates + row else it.candidates,
                pendingModelKeys = it.pendingModelKeys + row.key,
                candidateGroupNames = it.candidateGroupNames + (row.groupId to (it.candidateGroupNames[row.groupId] ?: service.name)),
                isAddDialogVisible = false, newModelId = "", newModelKind = state.allowedModelKinds.first())
        }
    }

    private fun testCurrentConnection() {
        val state = _uiState.value
        val service = state.service ?: return
        if (state.isTestingKey || !state.canEdit) return
        val revision = connectionRevision
        connectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isTestingKey = true, connectionResult = null) }
            try {
                // 语音服务没有模型目录接口，连接测试走该服务自己的协议握手：握手即校验密钥，且不计费。
                // 服务未实现握手探测时明确报告不支持，而不是借用其它厂商的协议去连接。
                // 只捕获"未实现探测"这一种预期失败；网络/密钥失败由 probe 自己返回结果值，
                // 取消（离开页面）必须原样传播，否则会被误报成 Unsupported。
                val result = if (service.kind != ModelServiceKind.Chat) {
                    try {
                        voiceCapabilities.connectionProbe(service.id).probe(service)
                    } catch (error: VoiceCapabilityNotSupported) {
                        ServiceConnectionResult.Unsupported
                    }
                } else {
                    testConnection(service)
                }
                if (revision == connectionRevision) _uiState.update { it.copy(connectionResult = result) }
            } finally {
                if (revision == connectionRevision) _uiState.update { it.copy(isTestingKey = false) }
            }
        }
    }

    private fun refreshModels() {
        val state = _uiState.value
        val service = state.service ?: return
        if (state.isRefreshing || !state.canEdit) return
        val revision = connectionRevision
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorRes = null) }
            try {
                val result = refreshCatalog(service)
                if (revision != connectionRevision) return@launch
                result.fold(onSuccess = { models ->
                    // 远端目录可能缺失类型；先恢复本地元数据再按服务类别筛选，避免语音项被误当成 LLM。
                    val knownModels = catalogModels + _uiState.value.candidates.associate { it.model.id to it.model }
                    val validModels = models.map { knownModels[it.id] ?: it }
                        .filter { it.id.isNotBlank() && it.supportedBy(service.kind) }.distinctBy { it.id }
                    _uiState.update { current ->
                        val known = current.candidates.associateBy { it.model.id }
                        val rows = validModels.map { model ->
                            // 远端 /models 常常没有语音类型和能力元数据，已有项保留本地元数据。
                            known[model.id] ?: LLMModelSettingDetailRow.LLMModelItem("${service.id}-remote", model)
                        }
                        current.copy(candidates = (current.candidates + rows).distinctBy { it.key },
                            candidateGroupNames = current.candidateGroupNames + ("${service.id}-remote" to service.name),
                            errorRes = if (validModels.isEmpty()) R.string.modelsettings_no_remote_models else null)
                    }
                    _effects.trySend(LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.ModelsRefreshed(validModels.size)))
                }, onFailure = {
                    _uiState.update { it.copy(errorRes = R.string.modelsettings_notice_models_sync_failed) }
                })
            } finally {
                if (revision == connectionRevision) _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    private fun back() {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.page != ServiceEditorPage.Detail) {
            _uiState.update { it.copy(page = ServiceEditorPage.Detail, errorRes = null) }
        } else if (state.isDirty) {
            _uiState.update { it.copy(isDiscardDialogVisible = true) }
        } else viewModelScope.launch { close() }
    }

    private fun save() {
        val service = _uiState.value.service ?: return
        if (!service.hasValidConnectionConfiguration()) {
            // wss 语音/Realtime 服务的接口地址不是 http(s)，通用文案会误导用户
            val errorRes = if (service.kind != ModelServiceKind.Chat) R.string.modelsettings_invalid_connection_speech
            else R.string.modelsettings_invalid_connection
            _uiState.update { it.copy(errorRes = errorRes, isDiscardDialogVisible = false) }
            return
        }
        write(LLMModelSettingDetailNotice.Saved) { saveService(service.copy(isAdded = true)) }
    }

    private suspend fun close() {
        if (_uiState.value.isClosing) return
        _uiState.update { it.copy(isClosing = true) }
        _effects.send(LLMModelSettingDetailEffect.Close)
    }

    private fun write(notice: LLMModelSettingDetailNotice, block: suspend () -> Boolean) {
        if (!_uiState.value.canEdit) return
        cancelConnectionOperations()
        _uiState.update { it.copy(isSaving = true, errorRes = null,
            isDiscardDialogVisible = false) }
        viewModelScope.launch {
            try {
                when (val result = runWhenAgentIdle(block)) {
                    AgentMutationResult.BlockedByActiveAgent -> _effects.send(
                        LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.AgentMutationBlocked))
                    is AgentMutationResult.Applied -> if (result.value) {
                        _effects.send(LLMModelSettingDetailEffect.ShowToast(notice))
                        close()
                    } else _uiState.update { it.copy(errorRes = R.string.modelsettings_write_failed) }
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
}

private fun LLMModelSetting.editorDraft(category: ModelServiceKind): LLMModelSetting = if (isAdded) copy(groups = groups.forService(category)) else
    copy(isEnabled = true, isOfficialToolsEnabled = false, groups = emptyList())

/** 分类只限制可配置的模型，不改写模型本身的多模态能力。 */
private fun Model.supportedBy(kind: ModelServiceKind): Boolean = when (kind) {
    ModelServiceKind.Chat -> ModelCapability.LLM in capabilities
    ModelServiceKind.Speech -> ModelCapability.LLM !in capabilities && capabilities.any {
        it == ModelCapability.STT || it == ModelCapability.ASR || it == ModelCapability.TTS ||
            it == ModelCapability.Realtime
    }
}

private fun List<ModelGroup>.forService(kind: ModelServiceKind): List<ModelGroup> = mapNotNull { group ->
    group.copy(models = group.models.filter { it.supportedBy(kind) }).takeIf { it.models.isNotEmpty() }
}

private fun List<ModelGroup>.toRows(): List<LLMModelSettingDetailRow.LLMModelItem> = flatMap { group ->
    group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) }
}
