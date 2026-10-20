package github.ponyhuang.gimi.data.plugin.catalog

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogLoad
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogSource
import github.ponyhuang.gimi.domain.plugin.repository.PluginCatalogRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 读取用户追加的多个插件订阅源，合并后返回；全部失败时回退到 APK 内置目录。
 *
 * 远端订阅是用户可配置的外部输入：只接受 HTTPS，限制单个响应大小为 2 MiB，
 * 单条无效数据由解析器跳过。条目按 key 合并，先添加的订阅源优先。
 * 成功结果按 URL 列表做 5 分钟进程内缓存，刷新时强制重取。
 */
@Singleton
class SubscribedPluginCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: PluginCatalogParser,
    private val subscriptionStore: PluginSubscriptionStore,
    private val okHttpClient: OkHttpClient,
) : PluginCatalogRepository {

    private val mutex = Mutex()
    private var cached: CachedCatalog? = null

    override suspend fun load(force: Boolean): PluginCatalogLoad = mutex.withLock {
        val urls = subscriptionStore.urls.value
        val cacheKey = urls.joinToString("\n")
        val now = System.currentTimeMillis()
        if (!force) {
            cached?.takeIf { it.cacheKey == cacheKey && now - it.loadedAt < CACHE_TTL_MS }
                ?.let { return@withLock it.result }
        }
        val result = if (urls.isEmpty()) {
            PluginCatalogLoad(
                entries = readBundledEntries(),
                source = PluginCatalogSource.BUNDLED,
                error = false,
            )
        } else {
            val remoteEntries = coroutineScope {
                urls.map { url -> async { fetchRemote(url) } }.awaitAll()
            }
            val successful = remoteEntries.filterNotNull()
            if (successful.isEmpty()) {
                PluginCatalogLoad(
                    entries = readBundledEntries(),
                    source = PluginCatalogSource.BUNDLED,
                    error = true,
                )
            } else {
                PluginCatalogLoad(
                    entries = mergeEntries(successful),
                    source = PluginCatalogSource.REMOTE,
                    error = successful.size != remoteEntries.size,
                )
            }
        }
        cached = CachedCatalog(loadedAt = now, cacheKey = cacheKey, result = result)
        result
    }

    private suspend fun fetchRemote(url: String): List<PluginCatalogEntry>? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    // 拒绝 HTTPS 降级重定向，订阅内容必须全程走 TLS。
                    if (!response.request.url.scheme.equals("https", ignoreCase = true)) {
                        return@use null
                    }
                    val body = response.body ?: return@use null
                    if (body.contentLength() > MAX_RESPONSE_BYTES) return@use null
                    val content = body.byteStream().readBoundedOrNull(MAX_RESPONSE_BYTES)
                        ?: return@use null
                    parser.parse(content)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** 按添加顺序合并；同一 key 只保留先出现的订阅源。 */
    private fun mergeEntries(sources: List<List<PluginCatalogEntry>>): List<PluginCatalogEntry> {
        val merged = LinkedHashMap<String, PluginCatalogEntry>()
        sources.forEach { entries ->
            entries.forEach { entry -> merged.putIfAbsent(entry.key, entry) }
        }
        return merged.values.sortedWith(compareBy({ it.sortOrder }, { it.title }))
    }

    private fun readBundledEntries(): List<PluginCatalogEntry> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            parser.parse(reader.readText())
        }
    }.getOrDefault(emptyList())

    private fun InputStream.readBoundedOrNull(maxBytes: Int): String? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private data class CachedCatalog(
        val loadedAt: Long,
        val cacheKey: String,
        val result: PluginCatalogLoad,
    )

    private companion object {
        const val ASSET_NAME = "plugin_catalog.json"
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
