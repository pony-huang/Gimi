package github.ponyhuang.asssistantai.feature.skills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportFailure
import github.ponyhuang.asssistantai.domain.skills.model.SkillImportSource
import github.ponyhuang.asssistantai.domain.skills.usecase.CommitSkillImportUseCase
import github.ponyhuang.asssistantai.domain.skills.usecase.DiscardSkillImportUseCase
import github.ponyhuang.asssistantai.domain.skills.usecase.ObserveInstalledSkillsUseCase
import github.ponyhuang.asssistantai.domain.skills.usecase.PrepareSkillImportUseCase
import github.ponyhuang.asssistantai.domain.skills.usecase.RemoveSkillUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SkillsSettingsViewModel @Inject constructor(
    observeInstalled: ObserveInstalledSkillsUseCase,
    private val prepareImport: PrepareSkillImportUseCase,
    private val commitImport: CommitSkillImportUseCase,
    private val discardImport: DiscardSkillImportUseCase,
    private val removeSkill: RemoveSkillUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SkillsSettingsUiState())
    val uiState: StateFlow<SkillsSettingsUiState> = mutableState.asStateFlow()
    private var nextPickerRequestId = 0

    init {
        viewModelScope.launch {
            observeInstalled().collect { skills ->
                mutableState.update { it.copy(skills = skills) }
            }
        }
    }

    fun onAction(action: SkillsSettingsAction) {
        when (action) {
            SkillsSettingsAction.OpenUrlDialog ->
                mutableState.update { it.copy(isUrlDialogVisible = true) }
            SkillsSettingsAction.DismissUrlDialog ->
                mutableState.update { it.copy(isUrlDialogVisible = false, urlDraft = "") }
            is SkillsSettingsAction.UrlChanged ->
                mutableState.update { it.copy(urlDraft = action.value) }
            SkillsSettingsAction.SubmitUrl -> submitUrl()
            SkillsSettingsAction.RequestLocalArchive -> {
                if (!mutableState.value.isImporting) {
                    mutableState.update {
                        it.copy(filePickerRequestId = ++nextPickerRequestId)
                    }
                }
            }
            is SkillsSettingsAction.LocalArchiveSelected -> {
                if (action.uri.isNotBlank()) prepare(SkillImportSource.LocalDocument(action.uri))
            }
            is SkillsSettingsAction.FilePickerHandled -> mutableState.update {
                if (it.filePickerRequestId == action.requestId) {
                    it.copy(filePickerRequestId = null)
                } else {
                    it
                }
            }
            SkillsSettingsAction.ConfirmReplacement -> confirmReplacement()
            SkillsSettingsAction.CancelReplacement -> cancelReplacement()
            is SkillsSettingsAction.RequestRemove ->
                mutableState.update { it.copy(pendingRemoval = action.skill) }
            SkillsSettingsAction.ConfirmRemoval -> confirmRemoval()
            SkillsSettingsAction.CancelRemoval ->
                mutableState.update { it.copy(pendingRemoval = null) }
            SkillsSettingsAction.ConsumeNotice ->
                mutableState.update { it.copy(notice = null) }
        }
    }

    private fun submitUrl() {
        val url = mutableState.value.urlDraft.trim()
        if (url.isEmpty()) {
            mutableState.update {
                it.copy(notice = SkillsNotice.Failed(SkillImportFailure.Reason.InvalidSource))
            }
            return
        }
        mutableState.update { it.copy(isUrlDialogVisible = false, urlDraft = "") }
        prepare(SkillImportSource.Url(url))
    }

    private fun prepare(source: SkillImportSource) {
        if (mutableState.value.isImporting) return
        mutableState.update { it.copy(isImporting = true, notice = null) }
        viewModelScope.launch {
            cancellationAwareRunCatching { prepareImport(source) }
                .onSuccess { prepared ->
                    if (prepared.replacesExisting) {
                        mutableState.update {
                            it.copy(
                                isImporting = false,
                                pendingReplacement = prepared,
                            )
                        }
                    } else {
                        install(prepared.id, prepared.name, allowReplace = false)
                    }
                }
                .onFailure(::publishFailure)
        }
    }

    private suspend fun install(preparedId: String, name: String, allowReplace: Boolean) {
        cancellationAwareRunCatching { commitImport(preparedId, allowReplace) }
            .onSuccess {
                mutableState.update {
                    it.copy(
                        isImporting = false,
                        pendingReplacement = null,
                        notice = SkillsNotice.Installed(name),
                    )
                }
            }
            .onFailure(::publishFailure)
    }

    private fun confirmReplacement() {
        val pending = mutableState.value.pendingReplacement ?: return
        mutableState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            install(pending.id, pending.name, allowReplace = true)
        }
    }

    private fun cancelReplacement() {
        val pending = mutableState.value.pendingReplacement ?: return
        mutableState.update { it.copy(pendingReplacement = null) }
        viewModelScope.launch {
            cancellationAwareRunCatching { discardImport(pending.id) }
                .onFailure(::publishFailure)
        }
    }

    private fun confirmRemoval() {
        val pending = mutableState.value.pendingRemoval ?: return
        mutableState.update { it.copy(pendingRemoval = null) }
        viewModelScope.launch {
            cancellationAwareRunCatching { removeSkill(pending.name) }
                .onSuccess {
                    mutableState.update {
                        it.copy(notice = SkillsNotice.Removed(pending.name))
                    }
                }
                .onFailure(::publishFailure)
        }
    }

    private fun publishFailure(error: Throwable) {
        val reason = (error as? SkillImportFailure)?.reason
            ?: SkillImportFailure.Reason.StorageFailure
        mutableState.update {
            it.copy(
                isImporting = false,
                pendingReplacement = null,
                notice = SkillsNotice.Failed(reason),
            )
        }
    }
}
