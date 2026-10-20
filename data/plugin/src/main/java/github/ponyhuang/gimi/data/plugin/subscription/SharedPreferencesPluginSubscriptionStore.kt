package github.ponyhuang.gimi.data.plugin.subscription

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 插件订阅地址列表的共享偏好存储。
 *
 * 首次启动使用官方默认源；用户追加/移除后按顺序持久化。空白、非 HTTPS 与超长地址
 * 被拒绝，重复地址只保留首次出现。列表允许为空（仅使用内置目录）。
 */
@Singleton
class SharedPreferencesPluginSubscriptionStore @Inject constructor(
    @ApplicationContext context: Context,
) : PluginSubscriptionStore {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val mutableUrls = MutableStateFlow(readStoredUrls())

    override val urls: StateFlow<List<String>> = mutableUrls.asStateFlow()

    override val defaultUrl: String = DEFAULT_URL

    override fun add(url: String) {
        val normalized = normalize(url) ?: return
        val current = mutableUrls.value
        if (normalized in current) return
        persist(current + normalized)
    }

    override fun remove(url: String) {
        val current = mutableUrls.value
        if (url !in current) return
        persist(current - url)
    }

    override fun reset() {
        persist(listOf(DEFAULT_URL))
    }

    private fun readStoredUrls(): List<String> {
        if (!preferences.contains(URLS_KEY)) return listOf(DEFAULT_URL)
        val stored = preferences.getString(URLS_KEY, null).orEmpty()
        return stored.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull(::normalize)
            .distinct()
            .toList()
    }

    private fun persist(urls: List<String>) {
        preferences.edit().putString(URLS_KEY, urls.joinToString("\n")).apply()
        mutableUrls.value = urls
    }

    private fun normalize(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > MAX_URL_LENGTH) return null
        if (!trimmed.startsWith("https://", ignoreCase = true)) return null
        if (trimmed.any(Char::isWhitespace)) return null
        return trimmed
    }

    companion object {
        const val DEFAULT_URL: String =
            "https://github.com/pony-huang/Gimi/releases/latest/download/plugin-subscription.json"

        private const val PREFS_NAME = "plugin_subscription"
        private const val URLS_KEY = "urls_v1"
        private const val MAX_URL_LENGTH = 2_048
    }
}
