package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import github.ponyhuang.gimi.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.OfficialToolSupport
import github.ponyhuang.gimi.domain.modelcatalog.usecase.DeleteModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelCandidatesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.LoadModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.ObserveModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SaveModelServiceUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.SetModelServiceEnabledUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.gimi.domain.modelcatalog.usecase.hasValidConnectionConfiguration
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
    private val deleteService: DeleteModelServiceUseCase,
    private val setServiceEnabled: SetModelServiceEnabledUseCase,
    private val testConnection: TestModelServiceConnectionUseCase,
    private val refreshCatalog: RefreshModelCatalogUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
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
            is LLMModelSettingDetailAction.FileUploadEnabledChanged -> edit {
                if (!action.value || it.apiProtocol == ApiProtocol.OpenaiChatcompletions) {
                    it.copy(isFileUploadEnabled = action.value)
                } else it
            }
            is LLMModelSettingDetailAction.FileUploadRetentionChanged -> if (action.days in setOf(1, 7, 30)) edit {
                it.copy(fileUploadRetentionDays = action.days)
            }
            is LLMModelSettingDetailAction.EnabledChanged -> changeEnabled(action.value)
            is LLMModelSettingDetailAction.OpenPage -> openPage(action.page)
            is LLMModelSettingDetailAction.ToggleModel -> if (state.canEdit) _uiState.update {
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
            is LLMModelSettingDetailAction.NewLLMModelKindChanged -> _uiState.update { it.copy(newModelKind = action.value) }
            LLMModelSettingDetailAction.ToggleApiKeyVisibility -> _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
            LLMModelSettingDetailAction.ToggleProtocolMenu -> if (state.canEdit) _uiState.update { it.copy(isProtocolMenuExpanded = !it.isProtocolMenuExpanded) }
            LLMModelSettingDetailAction.DismissProtocolMenu -> _uiState.update { it.copy(isProtocolMenuExpanded = false) }
            LLMModelSettingDetailAction.ToggleServiceMenu -> _uiState.update { it.copy(isServiceMenuExpanded = !it.isServiceMenuExpanded) }
            LLMModelSettingDetailAction.DismissServiceMenu -> _uiState.update { it.copy(isServiceMenuExpanded = false) }
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
            LLMModelSettingDetailAction.RequestDelete -> if (state.canEdit) _uiState.update {
                it.copy(isDeleteDialogVisible = true, isServiceMenuExpanded = false)
            }
            LLMModelSettingDetailAction.CancelDelete -> _uiState.update { it.copy(isDeleteDialogVisible = false) }
            LLMModelSettingDetailAction.ConfirmDelete -> delete()
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
                val draft = initial.editorDraft()
                _uiState.update {
                    it.copy(isLoading = false, service = draft, baseline = draft, supportedTools = toolsFor(draft),
                        candidates = (groups + initial.groups).toRows().distinctBy { row -> row.key },
                        candidateGroupNames = (groups + initial.groups).associate { group -> group.id to group.name })
                }
                observeModelService(id).collect { service ->
                    // 外部刷新不得覆盖未保存草稿；保存中也不让仓库回流打断表单。
                    _uiState.update { state ->
                        if (service == null || state.isDirty || state.isSaving) state
                        else service.editorDraft().let { next ->
                            state.copy(service = next, baseline = next, supportedTools = toolsFor(next),
                                candidates = (state.candidates + service.groups.toRows()).distinctBy { it.key },
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
        if (!state.canEdit || id.isBlank()) return
        val service = state.service ?: return
        val existing = state.candidates.firstOrNull { it.model.id == id }
        val row = existing ?: LLMModelSettingDetailRow.LLMModelItem(
            "${service.id}-custom",
            Model(id, id, isStt = state.newModelKind == NewModelKind.Stt, isTts = state.newModelKind == NewModelKind.Tts),
        )
        _uiState.update {
            it.copy(candidates = if (existing == null) it.candidates + row else it.candidates,
                pendingModelKeys = it.pendingModelKeys + row.key,
                candidateGroupNames = it.candidateGroupNames + (row.groupId to (it.candidateGroupNames[row.groupId] ?: service.name)),
                isAddDialogVisible = false, newModelId = "", newModelKind = NewModelKind.Chat)
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
                val result = testConnection(service)
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
                    _uiState.update { current ->
                        val known = current.candidates.associateBy { it.model.id }
                        val rows = models.filter { it.id.isNotBlank() }.map { model ->
                            // 远端 /models 常常没有语音类型和能力元数据，已有项保留本地元数据。
                            known[model.id] ?: LLMModelSettingDetailRow.LLMModelItem("${service.id}-remote", model)
                        }
                        current.copy(candidates = (current.candidates + rows).distinctBy { it.key },
                            candidateGroupNames = current.candidateGroupNames + ("${service.id}-remote" to service.name),
                            errorRes = if (models.isEmpty()) R.string.modelsettings_no_remote_models else null)
                    }
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
            _uiState.update { it.copy(errorRes = R.string.modelsettings_invalid_connection, isDiscardDialogVisible = false) }
            return
        }
        write(LLMModelSettingDetailNotice.Saved, close = true) { saveService(service.copy(isAdded = true)) }
    }

    private fun delete() {
        val id = serviceId ?: return
        write(LLMModelSettingDetailNotice.Deleted, close = true) { deleteService(id) }
    }

    private fun changeEnabled(enabled: Boolean) {
        val state = _uiState.value
        val service = state.service ?: return
        if (state.isDirty || !service.isAdded) return
        write(notice = null, close = false) {
            val result = setServiceEnabled(service.id, enabled)
            if (result) _uiState.update {
                val next = service.copy(isEnabled = enabled)
                it.copy(service = next, baseline = next)
            }
            result
        }
    }

    private suspend fun close() {
        if (_uiState.value.isClosing) return
        _uiState.update { it.copy(isClosing = true) }
        _effects.send(LLMModelSettingDetailEffect.Close)
    }

    private fun write(notice: LLMModelSettingDetailNotice?, close: Boolean, block: suspend () -> Boolean) {
        if (!_uiState.value.canEdit) return
        cancelConnectionOperations()
        _uiState.update { it.copy(isSaving = true, errorRes = null,
            isServiceMenuExpanded = false, isDeleteDialogVisible = false, isDiscardDialogVisible = false) }
        viewModelScope.launch {
            try {
                when (val result = runWhenAgentIdle(block)) {
                    AgentMutationResult.BlockedByActiveAgent -> _effects.send(
                        LLMModelSettingDetailEffect.ShowToast(LLMModelSettingDetailNotice.AgentMutationBlocked))
                    is AgentMutationResult.Applied -> if (result.value) {
                        if (notice != null) _effects.send(LLMModelSettingDetailEffect.ShowToast(notice))
                        if (close) close()
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

private fun LLMModelSetting.editorDraft(): LLMModelSetting = if (isAdded) this else
    copy(isEnabled = true, isOfficialToolsEnabled = false, groups = emptyList())

private fun List<ModelGroup>.toRows(): List<LLMModelSettingDetailRow.LLMModelItem> = flatMap { group ->
    group.models.map { LLMModelSettingDetailRow.LLMModelItem(group.id, it) }
}
