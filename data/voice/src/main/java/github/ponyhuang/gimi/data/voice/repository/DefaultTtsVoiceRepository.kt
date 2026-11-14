package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.model.TtsVoice
import github.ponyhuang.gimi.domain.voice.repository.VoiceCatalogResolver
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
    private val voiceCatalog: VoiceCatalogResolver,
) : TtsVoiceRepository {

    // 内存缓存：key 为模板+模型+凭据+端点的组合，避免同一配置重复网络请求
    private val cache = ConcurrentHashMap<String, List<TtsVoice>>()

    override suspend fun getVoices(selection: ModelSelection): List<TtsVoice> {
        val resolved = modelCatalog.resolveConnection(selection)
        val templateId = resolved?.templateId
        // 音色与模型绑定，缓存键必须带上模型，否则换模型后会命中上一次的音色表。
        val cacheKey = "${selection.instanceId}|${selection.modelId}|${resolved?.apiKey}|${resolved?.endpoint}"

        cache[cacheKey]?.let { return it }

        val context = resolved?.let {
            TtsVoiceContext(
                serviceId = it.templateId.orEmpty(),
                apiKey = it.apiKey,
                baseUrl = it.endpoint,
                modelId = it.modelId,
            )
        } ?: return voiceCatalog.builtInVoices(templateId)

        val provider = providers.firstOrNull { templateId in it.serviceIds }
        val voices = provider?.getVoices(context) ?: voiceCatalog.builtInVoices(templateId)

        if (voices.isNotEmpty()) {
            cache[cacheKey] = voices
        }
        return voices
    }

}
