package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.EndpointPolicy
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.isRecognition
import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内置模板清单的集成范围与协议约束。 */
class ModelServiceTemplatesTest {

    @Test
    fun chatOnlyVendorsDeclareTheirProtocolConstraints() {
        val templates = ModelServiceTemplates.all.associateBy { it.id }
        assertEquals(
            listOf(ApiProtocol.OpenaiChatcompletions),
            templates.getValue("openai").connection(ServiceCapability.Conversation)?.protocols,
        )
        assertEquals(
            listOf(ApiProtocol.Anthropic),
            templates.getValue("anthropic").connection(ServiceCapability.Conversation)?.protocols,
        )
        // 双协议厂商的 Anthropic 默认地址是厂商专属路径
        val anthropicEndpoints = mapOf(
            "deepseek" to "https://api.deepseek.com/anthropic",
            "minimax" to "https://api.minimaxi.com/anthropic",
            "mimo" to "https://api.xiaomimimo.com/anthropic",
            "kimi" to "https://api.moonshot.cn/anthropic",
            "glm" to "https://open.bigmodel.cn/api/anthropic",
        )
        for ((id, endpoint) in anthropicEndpoints) {
            assertEquals(
                endpoint,
                templates.getValue(id).connection(ServiceCapability.Conversation)
                    ?.defaultEndpoint(ApiProtocol.Anthropic),
            )
        }
    }

    @Test
    fun integratedVendorsCarrySpeechCapabilities() {
        val templates = ModelServiceTemplates.all.associateBy { it.id }
        assertEquals(
            setOf(ServiceCapability.Conversation, ServiceCapability.Recognition, ServiceCapability.Synthesis, ServiceCapability.Realtime),
            templates.getValue("openai").connections.map { it.capability }.toSet(),
        )
        assertEquals(
            setOf(ServiceCapability.Conversation, ServiceCapability.Recognition, ServiceCapability.Synthesis),
            templates.getValue("minimax").connections.map { it.capability }.toSet(),
        )
        // 百炼没有对话集成：模板只承载语音连接
        assertEquals(
            setOf(ServiceCapability.Recognition, ServiceCapability.Synthesis, ServiceCapability.Realtime),
            templates.getValue("qwen").connections.map { it.capability }.toSet(),
        )
        // DeepSeek 的语音是固定的 /v1/tools 端点，模板同样承载识别/合成连接
        assertEquals(
            setOf(ServiceCapability.Conversation, ServiceCapability.Recognition, ServiceCapability.Synthesis),
            templates.getValue("deepseek").connections.map { it.capability }.toSet(),
        )
    }

    @Test
    fun vendorManagedSpeechEndpointsComeFromTheTemplate() {
        val qwen = ModelServiceTemplates.fromId("qwen")!!
        val recognition = qwen.connection(ServiceCapability.Recognition)!!
        assertEquals(EndpointPolicy.VendorManaged, recognition.endpointPolicy)
        assertEquals(SpeechEndpoints.QWEN_AUDIO_CN, recognition.defaultEndpoint(recognition.protocols.first()))
        assertTrue(recognition.supportsWorkspace)
        val realtime = qwen.connection(ServiceCapability.Realtime)!!
        assertEquals(SpeechEndpoints.QWEN_AUDIO_REALTIME_CN, realtime.defaultEndpoint(realtime.protocols.first()))
        // 实时语音端点必须是 wss
        assertEquals(setOf("wss"), realtime.endpointSchemes)
    }

    @Test
    fun speechCatalogsDeclareVoiceTaskCapabilities() {
        val openAi = ModelServiceTemplates.fromId("openai")!!
        val synthesis = openAi.connection(ServiceCapability.Synthesis)!!.catalog
        assertTrue(synthesis.isNotEmpty())
        assertTrue(synthesis.all { ModelCapability.TTS in it.capabilities })
        val recognition = openAi.connection(ServiceCapability.Recognition)!!.catalog
        assertTrue(recognition.all { it.capabilities.any { capability -> capability.isRecognition } })
        // Realtime 标记不参与级联选择，实时语音入口按该标记分派
        val realtime = openAi.connection(ServiceCapability.Realtime)!!.catalog
        assertTrue(realtime.all { ModelCapability.Realtime in it.capabilities })
    }

    @Test
    fun unknownTemplateIdsResolveToNull() {
        assertFalse(ModelServiceTemplates.all.any { it.id == "qwen-audio" || it.id == "openai-speech" })
        assertEquals(null, ModelServiceTemplates.fromId("openai-speech"))
    }
}
