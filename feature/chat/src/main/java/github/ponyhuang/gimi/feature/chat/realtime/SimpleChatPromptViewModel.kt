package github.ponyhuang.gimi.feature.chat.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 预设管理页：持久化选择与编辑草稿分离，弹层状态不落盘。 */
@HiltViewModel
class SimpleChatPromptViewModel @Inject constructor(
    private val repository: SimpleChatPromptRepository,
) : ViewModel() {
    private val editing = MutableStateFlow<SimpleChatPromptPreset?>(null)
    private val confirmDelete = MutableStateFlow<SimpleChatPromptPreset?>(null)
    private val viewing = MutableStateFlow<SimpleChatPromptPreset?>(null)

    val uiState: StateFlow<SimpleChatPromptUiState> = combine(
        repository.presets,
        repository.selectedPresetId,
        editing,
        confirmDelete,
        viewing,
    ) { presets, selectedPresetId, editing, confirmDelete, viewing ->
        SimpleChatPromptUiState(
            presets = presets,
            builtInPresets = repository.builtInPresets,
            selectedPresetId = selectedPresetId,
            editing = editing,
            confirmDelete = confirmDelete,
            viewing = viewing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SimpleChatPromptUiState(
            presets = repository.presets.value,
            builtInPresets = repository.builtInPresets,
            selectedPresetId = repository.selectedPresetId.value,
        ),
    )

    fun onAction(action: SimpleChatPromptAction) {
        when (action) {
            is SimpleChatPromptAction.Select -> repository.selectPreset(action.presetId)
            SimpleChatPromptAction.StartCreate ->
                editing.value = SimpleChatPromptPreset(id = "", name = "", content = "")
            is SimpleChatPromptAction.StartEdit ->
                editing.value = repository.presets.value.firstOrNull { it.id == action.presetId }
            SimpleChatPromptAction.DismissEdit -> editing.value = null
            is SimpleChatPromptAction.Save -> save(action)
            is SimpleChatPromptAction.RequestDelete ->
                confirmDelete.value = repository.presets.value.firstOrNull { it.id == action.presetId }
            SimpleChatPromptAction.ConfirmDelete -> {
                confirmDelete.value?.let { repository.deletePreset(it.id) }
                confirmDelete.value = null
            }
            SimpleChatPromptAction.DismissDelete -> confirmDelete.value = null
            is SimpleChatPromptAction.ViewPrompt ->
                viewing.value = findPreset(action.presetId)
            SimpleChatPromptAction.DismissViewPrompt -> viewing.value = null
        }
    }

    private fun save(action: SimpleChatPromptAction.Save) {
        val target = editing.value ?: return
        val name = action.name.trim()
        val content = action.content.trim()
        // 空白输入不落库也不关闭弹层，让用户继续补全。
        if (name.isEmpty() || content.isEmpty()) return
        // 新建草稿用空 id 表示，保存时补一个稳定 id：仓库要求 id 非空，空 id 落库会被拒绝。
        val id = target.id.ifBlank { UUID.randomUUID().toString() }
        repository.upsertPreset(target.copy(id = id, name = name, content = content))
        editing.value = null
    }

    /** 官方预设与自建预设共用一套 id 空间，查找时先官方后自建。 */
    private fun findPreset(presetId: String): SimpleChatPromptPreset? =
        repository.builtInPresets.firstOrNull { it.id == presetId }
            ?: repository.presets.value.firstOrNull { it.id == presetId }
}
