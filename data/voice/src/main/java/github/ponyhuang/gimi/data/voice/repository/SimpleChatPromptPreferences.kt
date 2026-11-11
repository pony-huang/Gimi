package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.voice.repository.BuiltInVoicePresets
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptPreset
import github.ponyhuang.gimi.domain.voice.repository.SimpleChatPromptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 使用应用私有偏好持久化简单聊天提示词预设与当前选中项。
 * 读取容忍损坏数据：JSON 解析失败或字段缺失时丢弃对应条目，不阻塞语音功能。
 */
@Singleton
class SimpleChatPromptPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : SimpleChatPromptRepository {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val mutablePresets = MutableStateFlow(readPresets())

    override val builtInPresets: List<SimpleChatPromptPreset> = BuiltInVoicePresets.all

    private val mutableSelectedPresetId = MutableStateFlow(
        // 持久层可能残留指向已损坏/已删除预设的选中 id，读取时统一归一化。
        readSelectedId()?.takeIf { id -> isSelectable(id) },
    )

    override val presets: StateFlow<List<SimpleChatPromptPreset>> = mutablePresets.asStateFlow()

    override val selectedPresetId: StateFlow<String?> = mutableSelectedPresetId.asStateFlow()

    override fun upsertPreset(preset: SimpleChatPromptPreset) {
        require(preset.id.isNotBlank()) { "Preset id must not be blank" }
        require(preset.name.isNotBlank()) { "Preset name must not be blank" }
        require(preset.content.isNotBlank()) { "Preset content must not be blank" }
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

    override fun deletePreset(presetId: String) {
        val updated = mutablePresets.value.filterNot { it.id == presetId }
        if (updated.size == mutablePresets.value.size) return
        persistPresets(updated)
        mutablePresets.value = updated
        if (mutableSelectedPresetId.value == presetId) selectPreset(null)
    }

    override fun selectPreset(presetId: String?) {
        val target = presetId?.takeIf { id -> isSelectable(id) }
        if (mutableSelectedPresetId.value == target) return
        persistSelectedId(target)
        mutableSelectedPresetId.value = target
    }

    /** 选中项可指向官方预设或用户自建预设；两者都不存在时视为无效 id。 */
    private fun isSelectable(presetId: String): Boolean =
        builtInPresets.any { it.id == presetId } || mutablePresets.value.any { it.id == presetId }

    private fun persistPresets(presets: List<SimpleChatPromptPreset>) {
        preferences.edit { putString(PRESETS_KEY, gson.toJson(presets)) }
    }

    private fun persistSelectedId(presetId: String?) {
        preferences.edit { putString(SELECTED_ID_KEY, presetId) }
    }

    private fun readPresets(): List<SimpleChatPromptPreset> = preferences.getString(PRESETS_KEY, null)
        ?.let { raw ->
            runCatching {
                gson.decode<List<SimpleChatPromptPreferencesDto>>(raw).mapNotNull { dto ->
                    val id = dto.id?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val name = dto.name?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val content = dto.content?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    SimpleChatPromptPreset(id, name, content, dto.description.orEmpty())
                }
            }.getOrDefault(emptyList())
        }
        .orEmpty()

    private fun readSelectedId(): String? = preferences.getString(SELECTED_ID_KEY, null)

    /** 预设的持久化 DTO；仅存在序列化边界内，非法字段在读取时过滤。 */
    private data class SimpleChatPromptPreferencesDto(
        val id: String? = null,
        val name: String? = null,
        val content: String? = null,
        val description: String? = null,
    )

    private companion object {
        const val PREFERENCES_NAME = "simple_chat_prompt_preferences"
        const val PRESETS_KEY = "presets_json"
        const val SELECTED_ID_KEY = "selected_preset_id"
    }
}

private inline fun <reified T> Gson.decode(json: String): T =
    fromJson(json, object : TypeToken<T>() {}.type)
