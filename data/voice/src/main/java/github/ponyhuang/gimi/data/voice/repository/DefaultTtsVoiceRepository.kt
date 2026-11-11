package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceCatalog
import github.ponyhuang.gimi.domain.voice.model.TtsVoiceContext
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceProvider
import github.ponyhuang.gimi.domain.voice.repository.TtsVoiceRepository
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

    override suspend fun getVoices(selection: ModelSelection): List<TtsVoice> {
        val service = modelCatalog.currentServices().firstOrNull { it.id == selection.serviceId }
        val apiKey = service?.apiKey?.substringBefore(',')?.trim().orEmpty()
        val baseUrl = service?.apiBaseUrl.orEmpty()
        // 音色与模型绑定，缓存键必须带上模型，否则换模型后会命中上一次的音色表。
        val cacheKey = "${selection.serviceId}|${selection.modelId}|$apiKey|$baseUrl"

        cache[cacheKey]?.let { return it }

        val context = TtsVoiceContext(
            serviceId = selection.serviceId,
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelId = selection.modelId,
        )

        val provider = providers.firstOrNull { selection.serviceId in it.serviceIds }
        val voices = provider?.getVoices(context) ?: TtsVoiceCatalog.forService(selection.serviceId)

        if (voices.isNotEmpty()) {
            cache[cacheKey] = voices
        }
        return voices
    }

}
