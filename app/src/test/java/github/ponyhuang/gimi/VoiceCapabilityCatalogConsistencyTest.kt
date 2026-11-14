package github.ponyhuang.gimi

import github.ponyhuang.gimi.data.modelaccess.deepseek.speech.DeepSeekSpeechRecognizer
import github.ponyhuang.gimi.data.modelaccess.deepseek.speech.DeepSeekSpeechSynthesizer
import github.ponyhuang.gimi.data.modelaccess.openai.realtime.OpenAiRealtimeConnectionProbe
import github.ponyhuang.gimi.data.modelaccess.qwen.realtime.QwenRealtimeConnectionProbe
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeOneShotRecognizer
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeRecognitionProtocol
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeStreamingRecognizer
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DashScopeSynthesizer
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoAudioRecognizer
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoSpeechProtocol
import github.ponyhuang.gimi.data.modelaccess.mimo.speech.MiMoSynthesizer
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxBidiSynthesizer
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxRecognizer
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxSpeechProtocol
import github.ponyhuang.gimi.data.modelaccess.minimax.speech.MinimaxSynthesizer
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleAudioRecognizer
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleChatProtocol
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleConnectionProbe
import github.ponyhuang.gimi.data.modelaccess.openaicompatible.OpenAiCompatibleSynthesizer
import github.ponyhuang.gimi.data.modelaccess.openai.speech.OpenAiSpeechRecognizer
import github.ponyhuang.gimi.data.modelaccess.openai.speech.OpenAiSpeechSynthesizer
import github.ponyhuang.gimi.data.modelcatalog.ModelServiceTemplates
import github.ponyhuang.gimi.data.voice.remote.VoiceCapabilityRegistry
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.isVoiceTask
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型模板目录与语音能力注册表的一致性。
 *
 * 模板里声明了语音能力的模型必须都能按模板标识解析到对应适配器：新增厂商或新增模型时，
 * 如果只改了模板而没有注册适配器，这里会立刻失败，
 * 避免用户在选择器里选到一个永远调不通的模型。
 */
class VoiceCapabilityCatalogConsistencyTest {

    @Test
    fun everyVoiceModelInCatalogResolvesToAnAdapter() {
        val registry = registry()
        val unresolved = catalogVoiceModels()
            .filterNot { (templateId, capability) -> registry.isSupported(templateId, capability) }
            .map { (templateId, capability) -> "$templateId/$capability" }

        assertTrue(
            "模板目录中的语音模型没有对应适配器：$unresolved",
            unresolved.isEmpty(),
        )
    }

    /** 模板中声明了语音能力的（模板标识，能力）组合。 */
    private fun catalogVoiceModels(): List<Pair<String, ModelCapability>> =
        ModelServiceTemplates.all.flatMap { template ->
            template.connections.flatMap { connection ->
                connection.catalog.flatMap { model ->
                    model.capabilities.filter { it.isVoiceTask }.map { template.id to it }
                }
            }
        }

    /**
     * 与 SpeechModule 的生产装配保持一致的适配器集合。
     *
     * 这里是手工镜像：新增厂商或适配器时，除了在 SpeechModule 注册，也必须同步此清单，
     * 否则模板里新增的模型会被误判为"没有适配器"。
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
                DeepSeekSpeechSynthesizer(client),
                MinimaxSynthesizer(minimax),
                MiMoSynthesizer(mimo),
                OpenAiCompatibleSynthesizer(openAiCompatible),
                OpenAiSpeechSynthesizer(client),
            ),
            recognizers = setOf(
                DashScopeOneShotRecognizer(dashScope),
                DeepSeekSpeechRecognizer(client),
                MinimaxRecognizer(minimax),
                MiMoAudioRecognizer(mimo),
                OpenAiCompatibleAudioRecognizer(openAiCompatible),
                OpenAiSpeechRecognizer(client),
            ),
            streamingRecognizers = setOf(DashScopeStreamingRecognizer(dashScope)),
            connectionProbes = setOf(
                // qwen / openai 的语音端点由厂商自有，连接测试走实时握手探测
                // （实现在 :data:agent，与生产 SpeechModule/AdkVoiceAudioBackend 装配一致）。
                QwenRealtimeConnectionProbe(client),
                OpenAiRealtimeConnectionProbe(client),
                OpenAiCompatibleConnectionProbe(client),
            ),
            streamingSynthesizers = setOf(MinimaxBidiSynthesizer(client)),
        )
    }
}
