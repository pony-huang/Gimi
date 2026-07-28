package github.ponyhuang.asssistantai.feature.voicewake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordError
import github.ponyhuang.asssistantai.domain.speech.model.WakeKeywordException
import github.ponyhuang.asssistantai.domain.speech.model.WakeModelStatus
import github.ponyhuang.asssistantai.domain.speech.usecase.ManageVoiceWakeUseCase
import github.ponyhuang.asssistantai.domain.speech.usecase.ObserveVoiceWakeSettingsUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
class VoiceWakeSettingsViewModel @Inject constructor(
    observeSettings: ObserveVoiceWakeSettingsUseCase,
    private val manageVoiceWake: ManageVoiceWakeUseCase,
) : ViewModel() {
    private val localState = MutableStateFlow(LocalState())
    private var nextPermissionRequestId = 0

    private val _effects = MutableSharedFlow<VoiceWakeSettingsEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    val uiState = combine(observeSettings(), localState) { settings, local ->
        val activeModelId = settings.voiceState.activeModelId
        // 草稿绑定输入时的模型；切换模型后回落为该模型语言已保存的唤醒词。
        val draftForActiveModel = local.keywordDraft.takeIf { local.keywordDraftModelId == activeModelId }
        VoiceWakeSettingsUiState(
            voiceState = settings.voiceState,
            configurationReady = settings.configurationReady,
            keywordDraft = draftForActiveModel ?: settings.voiceState.keyword,
            keywordError = local.keywordError.takeIf { draftForActiveModel != null },
            permissionRequestId = local.permissionRequestId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VoiceWakeSettingsUiState(),
    )

    fun onAction(action: VoiceWakeSettingsAction) {
        when (action) {
            is VoiceWakeSettingsAction.KeywordChanged -> localState.update {
                it.copy(
                    keywordDraft = action.value,
                    keywordDraftModelId = uiState.value.voiceState.activeModelId,
                    keywordError = null,
                )
            }
            VoiceWakeSettingsAction.SaveKeyword -> saveKeyword()
            is VoiceWakeSettingsAction.ToggleListening -> toggleListening(action.enabled)
            is VoiceWakeSettingsAction.SelectModel -> selectModel(action.modelId)
            is VoiceWakeSettingsAction.InstallModel -> manageVoiceWake.installModel(action.modelId)
            is VoiceWakeSettingsAction.CancelInstall -> manageVoiceWake.cancelInstall(action.modelId)
            is VoiceWakeSettingsAction.PermissionsResult -> {
                val state = uiState.value
                if (
                    action.granted &&
                    state.configurationReady &&
                    state.voiceState.model.status == WakeModelStatus.Ready
                ) {
                    manageVoiceWake.start()
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
        manageVoiceWake.selectModel(modelId)
        localState.update {
            it.copy(keywordDraft = null, keywordDraftModelId = null, keywordError = null)
        }
    }

    private fun saveKeyword() {
        val draft = uiState.value.keywordDraft
        manageVoiceWake.setKeyword(draft)
            .onSuccess {
                localState.update {
                    it.copy(keywordDraft = draft.trim(), keywordError = null)
                }
            }
            .onFailure { error ->
                val keywordError = (error as? WakeKeywordException)?.error
                    ?: WakeKeywordError.InvalidCharacters
                localState.update { it.copy(keywordError = keywordError) }
            }
    }

    private fun toggleListening(enabled: Boolean) {
        if (!enabled) {
            manageVoiceWake.stop()
            return
        }

        val state = uiState.value
        when {
            state.voiceState.model.status != WakeModelStatus.Ready ->
                _effects.tryEmit(
                    VoiceWakeSettingsEffect.ShowToast(R.string.voicewake_model_download_prompt),
                )
            !state.configurationReady -> Unit
            else -> localState.update {
                it.copy(permissionRequestId = ++nextPermissionRequestId)
            }
        }
    }

    private data class LocalState(
        val keywordDraft: String? = null,
        val keywordDraftModelId: String? = null,
        val keywordError: WakeKeywordError? = null,
        val permissionRequestId: Int? = null,
    )
}
