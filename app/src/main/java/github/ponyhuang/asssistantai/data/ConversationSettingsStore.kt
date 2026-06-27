package github.ponyhuang.asssistantai.data

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话级配置的持久化入口。
 *
 * 配置以 sessionId 为 key 单独保存，不与模型服务或 UI 临时状态耦合。新增会话配置时只需向
 * [ConversationSettings] 添加字段，已有记录会使用字段默认值，因而不需要改变存储结构。
 */
@Singleton
class ConversationSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private var settingsBySessionId: Map<String, ConversationSettings> = readAll()

    /** 返回指定会话的配置；未配置的会话使用默认值。 */
    fun get(sessionId: String): ConversationSettings = settingsBySessionId[sessionId]
        ?: ConversationSettings()

    /** 更新指定会话的模型选择，同时保留未来扩展字段的现有值。 */
    fun setModelSelection(sessionId: String, selection: ModelSelection?) {
        if (sessionId.isBlank()) return
        settingsBySessionId = settingsBySessionId + (
            sessionId to get(sessionId).copy(modelSelection = selection)
        )
        persist()
    }

    /** 会话删除后移除其配置，避免偏好设置无限累积。 */
    fun remove(sessionId: String) {
        if (settingsBySessionId.containsKey(sessionId).not()) return
        settingsBySessionId = settingsBySessionId - sessionId
        persist()
    }

    private fun readAll(): Map<String, ConversationSettings> {
        val raw = preferences.getString(KEY_SETTINGS, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, ConversationSettings>>(raw, settingsType).orEmpty()
        }.getOrDefault(emptyMap())
    }

    private fun persist() {
        // 模型切换后可能立刻发生进程重启；使用同步提交，避免异步 apply 尚未落盘时
        // 冷启动恢复到旧选择。
        preferences.edit(commit = true) { putString(KEY_SETTINGS, gson.toJson(settingsBySessionId)) }
    }

    private val settingsType = object : TypeToken<Map<String, ConversationSettings>>() {}.type

    private companion object {
        const val FILE_NAME = "conversation_settings"
        const val KEY_SETTINGS = "settings_by_session_id"
    }
}

/**
 * 可持久化的会话配置。
 *
 * 后续的 system prompt、temperature、工具权限等设置应作为字段加在这里，而不是继续添加
 * 按功能分散的 SharedPreferences key。
 */
data class ConversationSettings(
    val modelSelection: ModelSelection? = null,
)
