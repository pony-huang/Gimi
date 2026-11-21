package github.ponyhuang.gimi.data.conversation.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.conversation.repository.BuiltInChatbotRoles
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRolePreset
import github.ponyhuang.gimi.domain.conversation.repository.ChatbotRoleRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 使用应用私有偏好持久化Chatbot 角色目录与每会话选择。
 * 读取容忍损坏数据：JSON 解析失败或字段缺失时丢弃对应条目，不阻塞语音功能。
 */
@Singleton
class ChatbotRolePreferences @Inject constructor(
    @ApplicationContext context: Context,
) : ChatbotRoleRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutablePresets = MutableStateFlow(readPresets())

    override val builtInPresets: List<ChatbotRolePreset> = BuiltInChatbotRoles.all

    private val mutableSelectedPresetIds = MutableStateFlow(readSelectedIds())

    override val presets: StateFlow<List<ChatbotRolePreset>> = mutablePresets.asStateFlow()
    override val selectedPresetIds: StateFlow<Map<String, String>> = mutableSelectedPresetIds.asStateFlow()

    @Synchronized
    override fun upsertPreset(preset: ChatbotRolePreset) {
        require(preset.id.isNotBlank()) { "Preset id must not be blank" }
        require(preset.name.isNotBlank()) { "Preset name must not be blank" }
        require(preset.content.isNotBlank()) { "Preset content must not be blank" }
        require(builtInPresets.none { it.id == preset.id }) { "Built-in preset ids are read-only" }
        val current = mutablePresets.value
        val index = current.indexOfFirst { it.id == preset.id }
        val updated = if (index >= 0) {
            current.toMutableList().apply { this[index] = preset }
        } else {
            current + preset
        }
        persistPresets(updated)
        mutablePresets.value = updated
    }

    @Synchronized
    override fun deletePreset(presetId: String) {
        val updated = mutablePresets.value.filterNot { it.id == presetId }
        if (updated.size == mutablePresets.value.size) return
        persistPresets(updated)
        mutablePresets.value = updated
        val selections = mutableSelectedPresetIds.value.filterValues { it != presetId }
        persistSelections(selections)
    }

    @Synchronized
    override fun selectPreset(conversationId: String, presetId: String?) {
        require(conversationId.isNotBlank()) { "Conversation id must not be blank" }
        val target = presetId?.takeIf(::isSelectable)
        val selections = mutableSelectedPresetIds.value.toMutableMap()
        if (target == null) selections.remove(conversationId) else selections[conversationId] = target
        persistSelections(selections)
    }

    private fun persistSelections(selections: Map<String, String>) {
        preferences.edit { putString(SELECTED_IDS_KEY, gson.toJson(selections)) }
        mutableSelectedPresetIds.value = selections
    }

    /** 选中项可指向官方预设或用户自建预设；两者都不存在时视为无效 id。 */
    private fun isSelectable(presetId: String): Boolean =
        builtInPresets.any { it.id == presetId } || mutablePresets.value.any { it.id == presetId }

    private fun persistPresets(presets: List<ChatbotRolePreset>) {
        preferences.edit { putString(PRESETS_KEY, gson.toJson(presets)) }
    }

    private fun readPresets(): List<ChatbotRolePreset> = preferences.getString(PRESETS_KEY, null)
        ?.let { raw ->
            runCatching {
                gson.decode<List<ChatbotRolePreferencesDto>>(raw).mapNotNull { dto ->
                    val id = dto.id?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val name = dto.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val content = dto.content?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    ChatbotRolePreset(id, name, content, dto.description.orEmpty())
                }
            }.getOrDefault(emptyList())
        }
        .orEmpty()

    private fun readSelectedIds(): Map<String, String> = preferences.getString(SELECTED_IDS_KEY, null)
        ?.let { raw ->
            try {
                gson.decode<Map<String, String?>?>(raw).orEmpty().filter { (conversationId, roleId) ->
                    conversationId.isNotBlank() && roleId != null && isSelectable(roleId)
                }.mapValues { requireNotNull(it.value) }
            } catch (_: com.google.gson.JsonParseException) {
                emptyMap()
            }
        }.orEmpty()

    /** 预设的持久化 DTO；仅存在序列化边界内，非法字段在读取时过滤。 */
    private data class ChatbotRolePreferencesDto(
        val id: String? = null,
        val name: String? = null,
        val content: String? = null,
        val description: String? = null,
    )

    private companion object {
        // 角色目录沿用现有文件，自建角色继续可用；会话选择使用独立的新键。
        const val PREFERENCES_NAME = "chatbot_prompt_preferences"
        const val PRESETS_KEY = "presets_json"
        const val SELECTED_IDS_KEY = "conversation_roles_json"
    }
}

private inline fun <reified T> Gson.decode(json: String): T =
    fromJson(json, object : TypeToken<T>() {}.type)
