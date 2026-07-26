package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.LoadModelServiceUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.ObserveModelServiceUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.RefreshModelCatalogUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.TestModelServiceConnectionUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.usecase.UpdateModelServiceUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ModelServiceDetailViewModel @Inject constructor(
    private val loadModelService: LoadModelServiceUseCase,
    private val observeModelService: ObserveModelServiceUseCase,
    private val updateModelService: UpdateModelServiceUseCase,
    private val testConnection: TestModelServiceConnectionUseCase,
    private val refreshCatalog: RefreshModelCatalogUseCase,
    private val runWhenAgentIdle: RunWhenAgentIdleUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LLMModelSettingDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var serviceId: String? = null
    private var expandedGroupIds: Set<String> = emptySet()
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    init {
        viewModelScope.launch {
            runWhenAgentIdle.state.collect { runtimeState ->
                _uiState.update { it.copy(isMutationBlocked = runtimeState.isBusy) }
            }
        }
    }

    fun onAction(action: LLmModelSettingDetailAction) {
        when (action) {
            is LLmModelSettingDetailAction.Load -> load(action.serviceId)
            is LLmModelSettingDetailAction.ApiKeyChanged -> changeApiKey(action.value)
            is LLmModelSettingDetailAction.ApiBaseUrlChanged -> changeBaseUrl(action.value)
            is LLmModelSettingDetailAction.ApiProtocolChanged -> changeProtocol(action.value)
            is LLmModelSettingDetailAction.EnabledChanged -> changeEnabled(action.value)
            is LLmModelSettingDetailAction.OfficialToolEnabledChanged ->
                changeOfficialTool(action.toolId, action.enabled)
            is LLmModelSettingDetailAction.ToggleGroup -> toggleGroup(action.groupId)
            is LLmModelSettingDetailAction.RemoveLLmModel -> removeModel(action.groupId, action.modelId)
            is LLmModelSettingDetailAction.NewLLmModelIdChanged ->
                _uiState.update { it.copy(newModelId = action.value) }
            is LLmModelSettingDetailAction.NewLLmModelKindChanged ->
                _uiState.update { it.copy(newModelKind = action.value) }
            LLmModelSettingDetailAction.ToggleApiKeyVisibility ->
                _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
            LLmModelSettingDetailAction.ToggleProtocolMenu ->
                _uiState.update { it.copy(isProtocolMenuExpanded = !it.isProtocolMenuExpanded) }
            LLmModelSettingDetailAction.DismissProtocolMenu ->
                _uiState.update { it.copy(isProtocolMenuExpanded = false) }
            LLmModelSettingDetailAction.ShowAddDialog ->
                _uiState.update { it.copy(isAddDialogVisible = true) }
            LLmModelSettingDetailAction.DismissAddDialog -> resetAddDialog()
            LLmModelSettingDetailAction.ConfirmAddLLmModel -> addModel()
            LLmModelSettingDetailAction.TestConnection -> testCurrentConnection()
            LLmModelSettingDetailAction.RefreshModels -> refreshModels()
            LLmModelSettingDetailAction.NoticeConsumed ->
                _uiState.update { it.copy(notice = null) }
            LLmModelSettingDetailAction.CloseConsumed ->
                _uiState.update { it.copy(shouldClose = false) }
        }
    }

    private fun load(id: String) {
        if (serviceId == id && loadJob?.isActive == true) return
        loadJob?.cancel()
        val generation = ++loadGeneration
        serviceId = id
        _uiState.value = LLMModelSettingDetailUiState(
            isLoading = true,
            isMutationBlocked = _uiState.value.isMutationBlocked,
        )

        loadJob = viewModelScope.launch {
            val initial = loadModelService(id)
            if (generation != loadGeneration || serviceId != id) return@launch
            if (initial == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        notice = LLMModelSettingDetailNotice.SettingNotFoundLLM,
                        shouldClose = true,
                    )
                }
                return@launch
            }

            expandedGroupIds = initial.groups.map { it.id }.toSet()
            publishService(initial)
            observeModelService(id).collect { service ->
                if (generation == loadGeneration && serviceId == id) {
                    publishService(service)
                }
            }
        }
    }

    private fun publishService(service: LLMModelSetting?) {
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                service = service,
                rows = service?.toRows(expandedGroupIds).orEmpty(),
            )
        }
    }

    private fun changeApiKey(value: String) {
        val id = serviceId ?: return
        mutate {
            updateModelService.apiKey(id, value)
            _uiState.update { state ->
                state.copy(service = state.service?.copy(apiKey = value))
            }
        }
    }

    private fun changeBaseUrl(value: String) {
        val id = serviceId ?: return
        mutate {
            updateModelService.baseUrl(id, value)
            _uiState.update { state ->
                val service = state.service ?: return@update state
                state.copy(
                    service = when (service.apiProtocol) {
                        ApiProtocol.Standard -> service.copy(apiBaseUrl = value)
                        ApiProtocol.Anthropic -> service.copy(anthropicBaseUrl = value)
                    },
                )
            }
        }
    }

    private fun changeProtocol(protocol: ApiProtocol) {
        val id = serviceId ?: return
        // 单协议厂商（OpenAI / Anthropic）不允许切换到不支持的接口标准。
        val supported = _uiState.value.service?.supportedProtocols ?: return
        if (protocol !in supported) return
        mutate {
            updateModelService.protocol(id, protocol)
            _uiState.update { state ->
                state.copy(
                    service = state.service?.copy(apiProtocol = protocol),
                    isProtocolMenuExpanded = false,
                )
            }
        }
    }

    private fun changeEnabled(enabled: Boolean) {
        val id = serviceId ?: return
        mutate {
            if (!updateModelService.enabled(id, enabled)) return@mutate
            _uiState.update { state -> state.copy(service = state.service?.copy(isEnabled = enabled)) }
        }
    }

    private fun changeOfficialTool(toolId: String, enabled: Boolean) {
        val id = serviceId ?: return
        val service = _uiState.value.service ?: return
        if (toolId !in service.supportedOfficialTools) return
        mutate {
            updateModelService.officialTool(id, toolId, enabled)
            _uiState.update { state ->
                val current = state.service ?: return@update state
                state.copy(
                    service = current.copy(
                        enabledOfficialTools = if (enabled) {
                            current.enabledOfficialTools + toolId
                        } else {
                            current.enabledOfficialTools - toolId
                        },
                    ),
                )
            }
        }
    }

    private fun toggleGroup(groupId: String) {
        expandedGroupIds = if (groupId in expandedGroupIds) {
            expandedGroupIds - groupId
        } else {
            expandedGroupIds + groupId
        }
        _uiState.update { state ->
            state.copy(rows = state.service?.toRows(expandedGroupIds).orEmpty())
        }
    }

    private fun removeModel(groupId: String, modelId: String) {
        val id = serviceId ?: return
        mutate { updateModelService.removeModel(id, groupId, modelId) }
    }

    private fun addModel() {
        val id = serviceId ?: return
        val state = _uiState.value
        val modelId = state.newModelId.trim()
        if (modelId.isBlank()) return
        val kind = state.newModelKind
        mutate {
            updateModelService.addModel(
                id,
                Model(
                    id = modelId,
                    name = modelId,
                    isStt = kind == NewModelKind.Stt,
                    isTts = kind == NewModelKind.Tts,
                ),
            )
            resetAddDialog()
        }
    }

    private fun resetAddDialog() {
        _uiState.update {
            it.copy(
                isAddDialogVisible = false,
                newModelId = "custom-model",
                newModelKind = NewModelKind.Chat,
            )
        }
    }

    private fun testCurrentConnection() {
        val service = _uiState.value.service ?: return
        if (_uiState.value.isTestingKey) return
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingKey = true) }
            val success = testConnection(service)
            _uiState.update {
                it.copy(
                    isTestingKey = false,
                    notice = if (success) {
                        LLMModelSettingDetailNotice.ConnectionSucceeded
                    } else {
                        LLMModelSettingDetailNotice.ConnectionFailed
                    },
                )
            }
        }
    }

    private fun refreshModels() {
        val service = _uiState.value.service ?: return
        if (_uiState.value.isRefreshing) return
        mutate {
            _uiState.update { it.copy(isRefreshing = true) }
            val result = refreshCatalog(service)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    notice = result.fold(
                        onSuccess = LLMModelSettingDetailNotice::ModelsSynchronized,
                        onFailure = { LLMModelSettingDetailNotice.LLMModelSynchronizationFailed },
                    ),
                )
            }
        }
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            when (runWhenAgentIdle(block)) {
                is AgentMutationResult.Applied -> Unit
                AgentMutationResult.BlockedByActiveAgent -> _uiState.update {
                    it.copy(notice = LLMModelSettingDetailNotice.AgentMutationBlocked)
                }
            }
        }
    }
}

private fun LLMModelSetting.toRows(expandedGroupIds: Set<String>): List<LLMModelSettingDetailRow> =
    buildList {
        groups.forEach { group ->
            val expanded = group.id in expandedGroupIds
            add(LLMModelSettingDetailRow.GroupHeader(group.id, group.name, expanded))
            if (expanded) {
                group.models.forEach { model ->
                    add(LLMModelSettingDetailRow.LLMModelItem(group.id, model))
                }
            }
        }
    }
