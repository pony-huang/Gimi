package github.ponyhuang.gimi.data.speech.repository

import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.speech.model.TtsVoice
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceCatalog
import github.ponyhuang.gimi.domain.speech.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceProvider
import github.ponyhuang.gimi.domain.speech.repository.TtsVoiceRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultTtsVoiceRepository @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards TtsVoiceProvider>,
    private val modelCatalog: ModelCatalogRepository,
) : TtsVoiceRepository {

    // 内存缓存：key 为 serviceId+apiKey 的哈希组合，避免同一配置重复网络请求
    private val cache = ConcurrentHashMap<String, List<TtsVoice>>()

    override suspend fun getVoices(serviceId: String): List<TtsVoice> {
        val service = modelCatalog.currentServices().firstOrNull { it.id == serviceId }
        val apiKey = service?.apiKey?.substringBefore(',')?.trim().orEmpty()
        val baseUrl = service?.apiBaseUrl.orEmpty()
        val cacheKey = "$serviceId|$apiKey|$baseUrl"

        cache[cacheKey]?.let { return it }

        val context = TtsVoiceContext(
            serviceId = serviceId,
            apiKey = apiKey,
            baseUrl = baseUrl,
        )

        val provider = providers.firstOrNull { it.serviceId == serviceId }
        val voices = provider?.getVoices(context) ?: TtsVoiceCatalog.forService(serviceId)

        if (voices.isNotEmpty()) {
            cache[cacheKey] = voices
        }
        return voices
    }

    /** 清理内存缓存（例如用户修改配置后或测试重置）。 */
    fun clearCache() {
        cache.clear()
    }
}
