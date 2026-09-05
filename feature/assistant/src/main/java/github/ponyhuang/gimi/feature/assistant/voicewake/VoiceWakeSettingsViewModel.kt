package github.ponyhuang.gimi.feature.assistant.voicewake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.model.WakeKeywordError
import github.ponyhuang.gimi.domain.speech.model.WakeKeywordException
import github.ponyhuang.gimi.domain.speech.model.WakeModelStatus
import github.ponyhuang.gimi.domain.speech.model.normalizeWakeKeyword
import github.ponyhuang.gimi.domain.speech.model.validateWakeKeyword
import github.ponyhuang.gimi.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.gimi.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class VoiceWakeSettingsViewModel @Inject constructor(
    observeSettings: ObserveVoiceWakeSettingsUseCase,
    private val manageVoiceWake: ManageVoiceWakeUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())
    private var nextPermissionRequestId = 0
    private val settings = observeSettings()
    private val _effects = MutableSharedFlow<VoiceWakeSettingsEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    val uiState = combine(settings, localState) { settings, local ->
        val voiceState = settings.voiceState
        val modelId = voiceState.activeModelId
        val draft = local.keywordDrafts[modelId] ?: voiceState.wakeWord
        VoiceWakeSettingsUiState(
            voiceState = voiceState,
            configurationReady = settings.configurationReady,
            isStartPending = local.isStartPending,
            permissionRequestId = local.permissionRequestId,
            keywordDraft = draft,
            keywordError = local.keywordErrors[modelId] ?: validateWakeKeyword(draft, voiceState.activeModel),
            keywordSaveFailed = modelId in local.saveFailedModels,
            hasUnsavedKeyword = normalizeWakeKeyword(draft) != voiceState.wakeWord,
            showUnsavedChangesDialog = local.showUnsavedChangesDialog,
            isApplyingKeyword = local.applyingModelId == modelId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceWakeSettingsUiState(),
    )

    init {
        viewModelScope.launch {
            settings.collect { state ->
                if (
                    state.voiceState.isRunning ||
                    state.voiceState.status == VoiceWakeStatus.Error
                ) {
                    localState.update { local ->
                        if (local.isStartPending || local.permissionRequestId != null) {
                            local.copy(isStartPending = false, permissionRequestId = null)
                        } else {
                            local
                        }
                    }
                } else if (
                    state.configurationReady &&
                    state.voiceState.model.status == WakeModelStatus.Ready
                ) {
                    localState.update { local ->
                        if (local.isStartPending && local.permissionRequestId == null) {
                            local.copy(
                                permissionRequestId = ++nextPermissionRequestId,
                            )
                        } else {
                            local
                        }
                    }
                }
                val applyingModelId = localState.value.applyingModelId
                if (
                    applyingModelId == state.voiceState.activeModelId &&
                    state.voiceState.status in listOf(VoiceWakeStatus.Listening, VoiceWakeStatus.Error)
                ) {
                    localState.update { it.copy(applyingModelId = null) }
                }
            }
        }
    }

    fun onAction(action: VoiceWakeSettingsAction) {
        when (action) {
            is VoiceWakeSettingsAction.ToggleListening -> toggleListening(action.enabled)
            is VoiceWakeSettingsAction.KeywordChanged -> updateKeywordDraft(action.value)
            is VoiceWakeSettingsAction.SuggestedKeywordSelected -> updateKeywordDraft(action.value)
            VoiceWakeSettingsAction.UseDefaultKeyword -> updateKeywordDraft(
                uiState.value.voiceState.activeModel.defaultWakeWord,
            )
            VoiceWakeSettingsAction.SaveKeyword -> saveCurrentKeyword()
            VoiceWakeSettingsAction.RequestBack -> requestBack()
            VoiceWakeSettingsAction.DismissUnsavedChanges -> localState.update {
                it.copy(showUnsavedChangesDialog = false)
            }
            VoiceWakeSettingsAction.DiscardChangesAndLeave -> {
                localState.update { LocalState() }
                _effects.tryEmit(VoiceWakeSettingsEffect.NavigateBack)
            }
            VoiceWakeSettingsAction.SaveChangesAndLeave -> saveAllAndLeave()
            is VoiceWakeSettingsAction.SelectModel -> selectModel(action.modelId)
            is VoiceWakeSettingsAction.InstallModel -> manageVoiceWake.installModel(action.modelId)
            is VoiceWakeSettingsAction.CancelInstall -> {
                if (action.modelId == uiState.value.voiceState.activeModelId) {
                    localState.update { it.copy(isStartPending = false) }
                }
                manageVoiceWake.cancelInstall(action.modelId)
            }
            is VoiceWakeSettingsAction.RemoveModel -> {
                localState.update { it.copy(isStartPending = false) }
                manageVoiceWake.removeModel(action.modelId)
            }
            is VoiceWakeSettingsAction.PermissionsResult -> {
                val state = uiState.value
                if (
                    action.granted &&
                    state.configurationReady &&
                    state.voiceState.model.status == WakeModelStatus.Ready
                ) {
                    manageVoiceWake.start()
                } else {
                    localState.update {
                        it.copy(isStartPending = false, permissionRequestId = null)
                    }
                }
            }
            is VoiceWakeSettingsAction.PermissionRequestHandled -> localState.update {
                if (it.permissionRequestId == action.requestId) {
                    it.copy(permissionRequestId = null)
                } else {
                    it
                }
            }
        }
    }

    private fun selectModel(modelId: String) {
        val targetStatus = uiState.value.voiceState.modelStates[modelId]?.status
        manageVoiceWake.selectModel(modelId)
        if (targetStatus == WakeModelStatus.Missing || targetStatus == WakeModelStatus.Error) {
            manageVoiceWake.installModel(modelId)
        }
    }

    private fun updateKeywordDraft(value: String) {
        val state = uiState.value.voiceState
        val modelId = state.activeModelId
        localState.update { local ->
            local.copy(
                keywordDrafts = local.keywordDrafts + (modelId to value),
                originalWakeWords = if (modelId in local.originalWakeWords) {
                    local.originalWakeWords
                } else {
                    local.originalWakeWords + (modelId to state.wakeWord)
                },
                keywordErrors = local.keywordErrors +
                    (modelId to validateWakeKeyword(value, state.activeModel)),
                saveFailedModels = local.saveFailedModels - modelId,
            )
        }
    }

    private fun saveCurrentKeyword() {
        val state = uiState.value
        val modelId = state.voiceState.activeModelId
        val normalized = normalizeWakeKeyword(state.keywordDraft)
        val error = validateWakeKeyword(normalized, state.voiceState.activeModel)
        if (error != null) {
            localState.update { it.copy(keywordErrors = it.keywordErrors + (modelId to error)) }
            return
        }
        val wasRunning = state.voiceState.isRunning
        manageVoiceWake.setWakeWord(modelId, normalized)
            .onSuccess {
                localState.update {
                    it.copy(
                        keywordDrafts = it.keywordDrafts + (modelId to normalized),
                        originalWakeWords = it.originalWakeWords + (modelId to normalized),
                        keywordErrors = it.keywordErrors - modelId,
                        saveFailedModels = it.saveFailedModels - modelId,
                        applyingModelId = modelId.takeIf { wasRunning },
                    )
                }
                _effects.tryEmit(VoiceWakeSettingsEffect.KeywordSaved(normalized))
            }
            .onFailure { failure ->
                val typedError = (failure as? WakeKeywordException)?.error
                localState.update {
                    if (typedError != null) {
                        it.copy(keywordErrors = it.keywordErrors + (modelId to typedError))
                    } else {
                        it.copy(saveFailedModels = it.saveFailedModels + modelId)
                    }
                }
            }
    }

    private fun requestBack() {
        val local = localState.value
        val hasDirtyDraft = local.keywordDrafts.any { (modelId, draft) ->
            normalizeWakeKeyword(draft) != local.originalWakeWords[modelId]
        }
        if (hasDirtyDraft) {
            localState.update { it.copy(showUnsavedChangesDialog = true) }
        } else {
            _effects.tryEmit(VoiceWakeSettingsEffect.NavigateBack)
        }
    }

    private fun saveAllAndLeave() {
        val local = localState.value
        val invalid = local.keywordDrafts.entries.firstOrNull { (modelId, draft) ->
            val model = github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog.byId(modelId)
            model == null || validateWakeKeyword(draft, model) != null
        }
        if (invalid != null) {
            val model = github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog.byId(invalid.key)
            if (model != null) manageVoiceWake.selectModel(model.id)
            localState.update { it.copy(showUnsavedChangesDialog = false) }
            return
        }
        val failure = local.keywordDrafts.entries.firstNotNullOfOrNull { (modelId, draft) ->
            manageVoiceWake.setWakeWord(modelId, normalizeWakeKeyword(draft)).exceptionOrNull()
                ?.let { modelId to it }
        }
        if (failure != null) {
            manageVoiceWake.selectModel(failure.first)
            val typedError = (failure.second as? WakeKeywordException)?.error
            localState.update {
                it.copy(
                    showUnsavedChangesDialog = false,
                    keywordErrors = if (typedError != null) {
                        it.keywordErrors + (failure.first to typedError)
                    } else {
                        it.keywordErrors
                    },
                    saveFailedModels = if (typedError != null) {
                        it.saveFailedModels
                    } else {
                        it.saveFailedModels + failure.first
                    },
                )
            }
            return
        }
        localState.update { LocalState() }
        _effects.tryEmit(VoiceWakeSettingsEffect.NavigateBack)
    }

    private fun toggleListening(enabled: Boolean) {
        if (!enabled) {
            val wasRunning = uiState.value.voiceState.isRunning
            localState.update { it.copy(isStartPending = false) }
            if (wasRunning) manageVoiceWake.stop()
            return
        }

        val state = uiState.value
        when (state.voiceState.model.status) {
            WakeModelStatus.Missing,
            WakeModelStatus.Error,
            -> {
                localState.update { it.copy(isStartPending = true) }
                manageVoiceWake.installModel(state.voiceState.activeModelId)
            }
            WakeModelStatus.Downloading,
            WakeModelStatus.Extracting,
            -> localState.update { it.copy(isStartPending = true) }
            WakeModelStatus.Removing -> Unit
            WakeModelStatus.Ready -> if (state.configurationReady) localState.update {
                it.copy(
                    isStartPending = true,
                    permissionRequestId = ++nextPermissionRequestId,
                )
            }
        }
    }

    /** 仅由设置页内部消费的一次性权限请求状态。 */
    private data class LocalState(
        val isStartPending: Boolean = false,
        val permissionRequestId: Int? = null,
        val keywordDrafts: Map<String, String> = emptyMap(),
        val originalWakeWords: Map<String, String> = emptyMap(),
        val keywordErrors: Map<String, WakeKeywordError?> = emptyMap(),
        val saveFailedModels: Set<String> = emptySet(),
        val showUnsavedChangesDialog: Boolean = false,
        val applyingModelId: String? = null,
    )
}
