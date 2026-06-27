package github.ponyhuang.asssistantai.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程外持久化"最近打开的会话 id"，让冷启动能恢复上次的会话。
 *
 * - 存储：SharedPreferences（无新依赖；只存一个 String，不需要 DataStore）。
 * - 写入点：bootstrap 命中、switchSession、reset、createConversation 兜底、ensureSessionId 兜底。
 * - 读取点：bootstrap 第一优先级。
 *
 * 文件名：`chat_prefs`；key：`last_session_id`。进程被杀后仍能恢复。
 */
@Singleton
class LastSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * 读取上次记录的 sessionId；为空 / 缺失返回 null。
     */
    fun get(): String? =
        prefs.getString(KEY_LAST_SESSION_ID, null)?.takeIf { it.isNotBlank() }

    /**
     * 记录当前打开的 sessionId。空串直接忽略（避免污染 prefs）。
     */
    fun set(sessionId: String) {
        if (sessionId.isBlank()) return
        prefs.edit(commit = true) { putString(KEY_LAST_SESSION_ID, sessionId) }
    }

    /**
     * 清掉记录。下次冷启动将走 fallback 路径（取最近活跃 session 或新建）。
     */
    fun clear() {
        prefs.edit(commit = true) { remove(KEY_LAST_SESSION_ID) }
    }

    companion object {
        private const val FILE_NAME = "chat_prefs"
        private const val KEY_LAST_SESSION_ID = "last_session_id"
    }
}
