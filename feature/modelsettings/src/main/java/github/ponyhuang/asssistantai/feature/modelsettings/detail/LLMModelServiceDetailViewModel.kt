package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.asssistantai.domain.conversation.runtime.isBusy
import github.ponyhuang.asssistantai.domain.conversation.usecase.RunWhenAgentIdleUseCase
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.Model
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
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
    private val _uiState = MutableStateFlow(ModelServiceDetailUiState())
    val uiState = _uiState.asStateFlow()

    private var serviceId: String? = null
    private var expandedGroupIds: Set<String> = emptySet()
    private var observationJob: Job? = null

    init {
        viewModelScope.launch {
            runWhenAgentIdle.state.collect { runtimeState ->
                _uiState.update { it.copy(isMutationBlocked = runtimeState.isBusy) }
            }
        }
    }

    fun onAction(action: ModelServiceDetailAction) {
        when (action) {
            is ModelServiceDetailAction.Load -> load(action.serviceId)
            is ModelServiceDetailAction.ApiKeyChanged -> changeApiKey(action.value)
            is ModelServiceDetailAction.ApiBaseUrlChanged -> changeBaseUrl(action.value)
            is ModelServiceDetailAction.ApiProtocolChanged -> changeProtocol(action.value)
            is ModelServiceDetailAction.EnabledChanged -> changeEnabled(action.value)
            is ModelServiceDetailAction.ToggleGroup -> toggleGroup(action.groupId)
            is ModelServiceDetailAction.RemoveModel -> removeModel(action.groupId, action.modelId)
            is ModelServiceDetailAction.NewModelIdChanged ->
                _uiState.update { it.copy(newModelId = action.value) }
            is ModelServiceDetailAction.NewModelKindChanged ->
                _uiState.update { it.copy(newModelKind = action.value) }
            ModelServiceDetailAction.ToggleApiKeyVisibility ->
                _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
            ModelServiceDetailAction.ToggleProtocolMenu ->
                _uiState.update { it.copy(isProtocolMenuExpanded = !it.isProtocolMenuExpanded) }
            ModelServiceDetailAction.DismissProtocolMenu ->
                _uiState.update { it.copy(isProtocolMenuExpanded = false) }
            ModelServiceDetailAction.ShowAddDialog ->
                _uiState.update { it.copy(isAddDialogVisible = true) }
            ModelServiceDetailAction.DismissAddDialog -> resetAddDialog()
            ModelServiceDetailAction.ConfirmAddModel -> addModel()
            ModelServiceDetailAction.TestConnection -> testCurrentConnection()
            ModelServiceDetailAction.RefreshModels -> refreshModels()
            ModelServiceDetailAction.NoticeConsumed ->
                _uiState.update { it.copy(notice = null) }
            ModelServiceDetailAction.CloseConsumed ->
                _uiState.update { it.copy(shouldClose = false) }
        }
    }

    private fun load(id: String) {
        if (serviceId == id && observationJob?.isActive == true) return
        observationJob?.cancel()
        serviceId = id
        _uiState.value = ModelServiceDetailUiState(
            isLoading = true,
            isMutationBlocked = _uiState.value.isMutationBlocked,
        )

        viewModelScope.launch {
            val initial = loadModelService(id)
            if (initial == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        notice = ModelServiceDetailNotice.ServiceNotFound,
                        shouldClose = true,
                    )
                }
                return@launch
            }

            expandedGroupIds = initial.groups.map { it.id }.toSet()
            publishService(initial)
            observationJob = launch {
                observeModelService(id).collect(::publishService)
            }
        }
    }

    private fun publishService(service: ModelService?) {
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
                        ModelServiceDetailNotice.ConnectionSucceeded
                    } else {
                        ModelServiceDetailNotice.ConnectionFailed
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
                        onSuccess = ModelServiceDetailNotice::ModelsSynchronized,
                        onFailure = { ModelServiceDetailNotice.ModelSynchronizationFailed },
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
                    it.copy(notice = ModelServiceDetailNotice.AgentMutationBlocked)
                }
            }
        }
    }
}

private fun ModelService.toRows(expandedGroupIds: Set<String>): List<ModelServiceDetailRow> =
    buildList {
        groups.forEach { group ->
            val expanded = group.id in expandedGroupIds
            add(ModelServiceDetailRow.GroupHeader(group.id, group.name, expanded))
            if (expanded) {
                group.models.forEach { model ->
                    add(ModelServiceDetailRow.ModelItem(group.id, model))
                }
            }
        }
    }
