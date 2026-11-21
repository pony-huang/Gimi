package github.ponyhuang.gimi.feature.chat.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRolePreset
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRoleRepository
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceCallController
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
    private val repository: ChatbotRoleRepository,
    private val voiceCallController: RealtimeVoiceCallController,
) : ViewModel() {
    val callState = voiceCallController.state
    private val conversationId = MutableStateFlow<String?>(null)

    fun bind(conversationId: String?) { this.conversationId.value = conversationId?.takeIf(String::isNotBlank) }

    private val editing = MutableStateFlow<ChatbotRolePreset?>(null)
    private val confirmDelete = MutableStateFlow<ChatbotRolePreset?>(null)
    private val viewing = MutableStateFlow<ChatbotRolePreset?>(null)

    val uiState: StateFlow<SimpleChatPromptUiState> = combine(
        repository.presets,
        combine(repository.selectedPresetIds, conversationId) { selections, id -> id to selections[id] },
        editing,
        confirmDelete,
        viewing,
    ) { presets, selectedPresetId, editing, confirmDelete, viewing ->
        SimpleChatPromptUiState(
            presets = presets,
            builtInPresets = repository.builtInPresets,
            selectedPresetId = selectedPresetId.second,
            canSelect = selectedPresetId.first != null,
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
            selectedPresetId = null,
        ),
    )

    fun onAction(action: SimpleChatPromptAction) {
        if (callState.value.configurationLocked) return
        when (action) {
            is SimpleChatPromptAction.Select -> conversationId.value?.let { repository.selectPreset(it, action.presetId) }
            SimpleChatPromptAction.StartCreate ->
                editing.value = ChatbotRolePreset(id = "", name = "", content = "")
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
            SimpleChatPromptAction.CopyViewedPreset -> {
                editing.value = viewing.value?.copy(id = "")
                viewing.value = null
            }
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
        repository.upsertPreset(target.copy(id = id, name = name, content = content, description = action.description.trim()))
        editing.value = null
    }

    /** 官方预设与自建预设共用一套 id 空间，查找时先官方后自建。 */
    private fun findPreset(presetId: String): ChatbotRolePreset? =
        repository.builtInPresets.firstOrNull { it.id == presetId }
            ?: repository.presets.value.firstOrNull { it.id == presetId }
}
