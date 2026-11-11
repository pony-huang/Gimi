package github.ponyhuang.gimi

import github.ponyhuang.gimi.data.agent.voice.OpenAiRealtimeConnectionProbe
import github.ponyhuang.gimi.data.agent.voice.QwenRealtimeConnectionProbe
import github.ponyhuang.gimi.data.modelcatalog.LLMModelConfigs
import github.ponyhuang.gimi.data.modelcatalog.SpeechModelConfigs
import github.ponyhuang.gimi.data.voice.remote.DashScopeOneShotRecognizer
import github.ponyhuang.gimi.data.voice.remote.DashScopeRecognitionProtocol
import github.ponyhuang.gimi.data.voice.remote.DashScopeStreamingRecognizer
import github.ponyhuang.gimi.data.voice.remote.DashScopeSynthesizer
import github.ponyhuang.gimi.data.voice.remote.MiMoAudioRecognizer
import github.ponyhuang.gimi.data.voice.remote.MiMoSpeechProtocol
import github.ponyhuang.gimi.data.voice.remote.MiMoSynthesizer
import github.ponyhuang.gimi.data.voice.remote.MinimaxBidiSynthesizer
import github.ponyhuang.gimi.data.voice.remote.MinimaxRecognizer
import github.ponyhuang.gimi.data.voice.remote.MinimaxSpeechProtocol
import github.ponyhuang.gimi.data.voice.remote.MinimaxSynthesizer
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleAudioRecognizer
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleChatProtocol
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleConnectionProbe
import github.ponyhuang.gimi.data.voice.remote.OpenAiCompatibleSynthesizer
import github.ponyhuang.gimi.data.voice.remote.OpenAiSpeechRecognizer
import github.ponyhuang.gimi.data.voice.remote.OpenAiSpeechSynthesizer
import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.isVoiceTask
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型目录与语音能力注册表的一致性。
 *
 * 目录里声明了语音能力的模型必须都能解析到对应适配器：新增厂商或新增模型时，
 * 如果只改了目录而没有注册适配器，这里会立刻失败，
 * 避免用户在选择器里选到一个永远调不通的模型。
 */
class VoiceCapabilityCatalogConsistencyTest {

    @Test
    fun everyVoiceModelInCatalogResolvesToAnAdapter() {
        val registry = registry()
        val unresolved = catalogVoiceModels()
            .filterNot { (serviceId, capability) -> registry.isSupported(serviceId, capability) }
            .map { (serviceId, capability) -> "$serviceId/$capability" }

        assertTrue(
            "目录中的语音模型没有对应适配器：$unresolved",
            unresolved.isEmpty(),
        )
    }

    /** 目录中声明了语音能力的（服务标识，能力）组合。 */
    private fun catalogVoiceModels(): List<Pair<String, ModelCapability>> =
        (LLMModelConfigs.services + SpeechModelConfigs.services).flatMap { provider ->
            provider.lLMModelGroups.flatMap { group ->
                group.models.flatMap { model ->
                    model.capabilities.filter { it.isVoiceTask }.map { provider.serviceId to it }
                }
            }
        }

    /**
     * 与 SpeechModule 的生产装配保持一致的适配器集合。
     *
     * 这里是手工镜像：新增厂商或适配器时，除了在 SpeechModule 注册，也必须同步此清单，
     * 否则目录里新增的模型会被误判为"没有适配器"。
     */
    private fun registry(): VoiceCapabilityRegistry {
        val client = OkHttpClient()
        val dashScope = DashScopeRecognitionProtocol(client)
        val minimax = MinimaxSpeechProtocol(client)
        val mimo = MiMoSpeechProtocol(client)
        val openAiCompatible = OpenAiCompatibleChatProtocol(client)
        return VoiceCapabilityRegistry(
            synthesizers = setOf(
                DashScopeSynthesizer(client),
                MinimaxSynthesizer(minimax),
                MiMoSynthesizer(mimo),
                OpenAiCompatibleSynthesizer(openAiCompatible),
                OpenAiSpeechSynthesizer(client),
            ),
            recognizers = setOf(
                DashScopeOneShotRecognizer(dashScope),
                MinimaxRecognizer(minimax),
                MiMoAudioRecognizer(mimo),
                OpenAiCompatibleAudioRecognizer(openAiCompatible),
                OpenAiSpeechRecognizer(client),
            ),
            streamingRecognizers = setOf(DashScopeStreamingRecognizer(dashScope)),
            connectionProbes = setOf(
                // qwen-audio / openai-speech 的端点由厂商自有，连接测试走实时握手探测
                // （实现在 :data:agent，与生产 SpeechModule/AdkVoiceAudioBackend 装配一致）。
                QwenRealtimeConnectionProbe(client),
                OpenAiRealtimeConnectionProbe(client),
                OpenAiCompatibleConnectionProbe(client),
            ),
            streamingSynthesizers = setOf(MinimaxBidiSynthesizer(client)),
        )
    }
}
