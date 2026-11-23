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
class ChatbotPromptViewModel @Inject constructor(
    private val repository: ChatbotRoleRepository,
    private val voiceCallController: RealtimeVoiceCallController,
) : ViewModel() {
    val callState = voiceCallController.state
    private val conversationId = MutableStateFlow<String?>(null)

    fun bind(conversationId: String?) { this.conversationId.value = conversationId?.takeIf(String::isNotBlank) }

    private val editing = MutableStateFlow<ChatbotRolePreset?>(null)
    private val confirmDelete = MutableStateFlow<ChatbotRolePreset?>(null)
    private val viewing = MutableStateFlow<ChatbotRolePreset?>(null)

    val uiState: StateFlow<ChatbotPromptUiState> = combine(
        repository.presets,
        repository.builtInPresets,
        combine(repository.selectedPresetIds, conversationId) { selections, id -> id to selections[id] },
        combine(editing, confirmDelete, viewing) { e, c, v -> Triple(e, c, v) },
    ) { presets, builtInPresets, (id, selectedPresetId), (editing, confirmDelete, viewing) ->
        ChatbotPromptUiState(
            presets = presets,
            builtInPresets = builtInPresets,
            selectedPresetId = selectedPresetId,
            canSelect = id != null,
            editing = editing,
            confirmDelete = confirmDelete,
            viewing = viewing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatbotPromptUiState(
            presets = repository.presets.value,
            builtInPresets = repository.builtInPresets.value,
            selectedPresetId = null,
        ),
    )

    fun onAction(action: ChatbotPromptAction) {
        if (callState.value.configurationLocked) return
        when (action) {
            is ChatbotPromptAction.Select -> conversationId.value?.let { repository.selectPreset(it, action.presetId) }
            is ChatbotPromptAction.ToggleEnabled -> repository.setEnabled(action.presetId, action.enabled)
            ChatbotPromptAction.StartCreate ->
                editing.value = ChatbotRolePreset(id = "", name = "", content = "")
            is ChatbotPromptAction.StartEdit ->
                editing.value = repository.presets.value.firstOrNull { it.id == action.presetId }
            ChatbotPromptAction.DismissEdit -> editing.value = null
            is ChatbotPromptAction.Save -> save(action)
            is ChatbotPromptAction.RequestDelete ->
                confirmDelete.value = repository.presets.value.firstOrNull { it.id == action.presetId }
            ChatbotPromptAction.ConfirmDelete -> {
                confirmDelete.value?.let { repository.deletePreset(it.id) }
                confirmDelete.value = null
            }
            ChatbotPromptAction.DismissDelete -> confirmDelete.value = null
            is ChatbotPromptAction.ViewPrompt ->
                viewing.value = findPreset(action.presetId)
            ChatbotPromptAction.CopyViewedPreset -> {
                editing.value = viewing.value?.copy(id = "", enabled = true)
                viewing.value = null
            }
            ChatbotPromptAction.DismissViewPrompt -> viewing.value = null
        }
    }

    private fun save(action: ChatbotPromptAction.Save) {
        val target = editing.value ?: return
        val name = action.name.trim()
        val content = action.content.trim()
        // 空白输入不落库也不关闭弹层，让用户继续补全。
        if (name.isEmpty() || content.isEmpty()) return
        // 新建草稿用空 id 表示，保存时补一个稳定 id：仓库要求 id 非空，空 id 落库会被拒绝。
        val id = target.id.ifBlank { UUID.randomUUID().toString() }
        repository.upsertPreset(target.copy(id = id, name = name, content = content, description = action.description.trim(), enabled = target.enabled))
        editing.value = null
    }

    /** 官方预设与自建预设共用一套 id 空间，查找时先官方后自建。 */
    private fun findPreset(presetId: String): ChatbotRolePreset? =
        repository.builtInPresets.value.firstOrNull { it.id == presetId }
            ?: repository.presets.value.firstOrNull { it.id == presetId }
}
