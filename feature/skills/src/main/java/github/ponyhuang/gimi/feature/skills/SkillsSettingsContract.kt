package github.ponyhuang.gimi.feature.skills

import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.model.SkillImportFailure

data class SkillsSettingsUiState(
    val skills: List<InstalledSkill> = emptyList(),
    val isImporting: Boolean = false,
    val isUrlDialogVisible: Boolean = false,
    val urlDraft: String = "",
    val filePickerRequestId: Int? = null,
    val pendingReplacement: PreparedSkillImport? = null,
    val pendingRemoval: InstalledSkill? = null,
    val notice: SkillsNotice? = null,
)

sealed interface SkillsNotice {
    data class Installed(val name: String) : SkillsNotice
    data class Removed(val name: String) : SkillsNotice
    data class Failed(val reason: SkillImportFailure.Reason) : SkillsNotice
}

sealed interface SkillsSettingsAction {
    data object OpenUrlDialog : SkillsSettingsAction
    data object DismissUrlDialog : SkillsSettingsAction
    data class UrlChanged(val value: String) : SkillsSettingsAction
    data object SubmitUrl : SkillsSettingsAction
    data object RequestLocalArchive : SkillsSettingsAction
    data class LocalArchiveSelected(val uri: String) : SkillsSettingsAction
    data class FilePickerHandled(val requestId: Int) : SkillsSettingsAction
    data object ConfirmReplacement : SkillsSettingsAction
    data object CancelReplacement : SkillsSettingsAction
    data class RequestRemove(val skill: InstalledSkill) : SkillsSettingsAction
    data object ConfirmRemoval : SkillsSettingsAction
    data object CancelRemoval : SkillsSettingsAction
    data object ConsumeNotice : SkillsSettingsAction
}
