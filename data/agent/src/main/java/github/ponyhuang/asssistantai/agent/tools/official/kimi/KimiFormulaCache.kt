package github.ponyhuang.asssistantai.agent.tools.official.kimi

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * Caches the Moonshot formula manifest shared by the settings catalog and the Agent runtime.
 *
 * The cache key includes the model service and credential so concurrent conversations cannot
 * reuse declarations discovered with another account. Failed loads are deliberately not cached,
 * allowing a later `tool_search` or settings refresh to retry immediately.
 */
@Singleton
class KimiFormulaCache internal constructor(
    private val loader: suspend (apiKey: String) -> List<FormulaDeclaration>,
    private val nowMillis: () -> Long,
) {
    @Inject
    constructor(httpClient: OkHttpClient) : this(
        loader = { apiKey ->
            KimiFormulaManifest(
                apiKey = apiKey,
                httpClient = httpClient,
            ).fetch()
        },
        nowMillis = System::currentTimeMillis,
    )

    private val mutex = Mutex()
    private val entries = mutableMapOf<CacheKey, CacheEntry>()

    internal suspend fun fetch(
        serviceId: String,
        apiKey: String,
    ): List<FormulaDeclaration> = mutex.withLock {
        val key = CacheKey(
            serviceId = serviceId,
            apiKey = apiKey,
        )
        val now = nowMillis()
        entries[key]
            ?.takeIf { entry -> now - entry.loadedAtMillis < CACHE_DURATION_MILLIS }
            ?.let { entry -> return@withLock entry.declarations }

        val declarations = runCatching { loader(apiKey) }
            .getOrElse { return@withLock emptyList() }
            .toList()
        entries[key] = CacheEntry(
            declarations = declarations,
            loadedAtMillis = now,
        )
        declarations
    }

    /** Identifies declarations that are safe to reuse for one model-service credential. */
    private data class CacheKey(
        val serviceId: String,
        val apiKey: String,
    )

    /** Holds one successful manifest snapshot and the time at which it was loaded. */
    private data class CacheEntry(
        val declarations: List<FormulaDeclaration>,
        val loadedAtMillis: Long,
    )

    private companion object {
        const val CACHE_DURATION_MILLIS = 5 * 60 * 1_000L
    }
}
