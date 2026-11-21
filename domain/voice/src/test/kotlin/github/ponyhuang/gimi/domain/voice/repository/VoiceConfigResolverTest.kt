package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 有效语音配置解析的领域规则：字段继承、恢复默认、链路记忆与音色绑定。
 * 全部使用隔离 fixture 与假查询，不依赖目录实现。
 */
class VoiceConfigResolverTest {

    /** 可编程的假查询：模型/音色合法性与音色记忆由用例逐项给定。 */
    private class FakeLookup(
        private val invalidModels: Set<ModelSelection> = emptySet(),
        private val invalidVoices: Map<VoicePipeline, Set<String>> = emptyMap(),
        private val memory: Map<Pair<VoicePipeline, ModelSelection>, String> = emptyMap(),
    ) : VoiceConfigLookup {
        override fun isModelValid(selection: ModelSelection): Boolean = selection !in invalidModels
        override fun isVoiceValid(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String): Boolean? =
            invalidVoices[pipeline]?.let { voiceId !in it }
        override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? =
            memory[pipeline to selection]
    }

    private val modelA = ModelSelection("instance-a", "conn-a", "model-a")
    private val modelB = ModelSelection("instance-b", "conn-b", "model-b")
    private val ttsA = ModelSelection("instance-a", "conn-a", "tts-a")
    private val ttsB = ModelSelection("instance-b", "conn-b", "tts-b")

    private fun defaults(
        pipeline: VoicePipeline = VoicePipeline.CASCADED,
        style: VoiceConversationStyle = VoiceConversationStyle.CHATBOT,
        realtime: ModelSelection? = modelA,
    ) = VoiceGlobalDefaults(
        pipeline = pipeline,
        cascadedRecognitionModel = ttsA,
        cascadedSynthesisModel = ttsA,
        semiCascadedUnderstandingModel = modelA,
        semiCascadedSynthesisModel = ttsA,
        realtimeModel = realtime,
    )

    // ---- 字段继承 ----

    @Test
    fun `no override inherits every global default`() {
        val config = VoiceConfigResolver.resolve(defaults(), emptyOverrides(), FakeLookup())

        assertEquals(VoicePipeline.CASCADED, config.pipeline)
        assertEquals(ttsA, config.cascadedRecognitionModel)
        assertEquals(ttsA, config.cascadedSynthesisModel)
        assertEquals(modelA, config.realtimeModel)
        assertTrue(config.missingRequired.isEmpty())
    }

    @Test
    fun `override wins and inherited fields keep following global defaults`() {
        val overrides = emptyOverrides().copy(
            realtimeModel = VoiceFieldOverride.Value(modelB),
        )

        // 全局仍是 Chatbot + modelA：覆盖只改变被覆盖字段。
        val config = VoiceConfigResolver.resolve(defaults(), overrides, FakeLookup())
        assertEquals(modelB, config.realtimeModel)
        assertEquals(VoicePipeline.CASCADED, config.pipeline)
        assertEquals(ttsA, config.cascadedSynthesisModel)
    }

    @Test
    fun `new conversation inherits global defaults`() {
        // 新对话没有覆盖记录：空覆盖等价于全继承。
        val config = VoiceConfigResolver.resolve(defaults(pipeline = VoicePipeline.NATIVE_REALTIME), emptyOverrides(), FakeLookup())
        assertEquals(VoicePipeline.NATIVE_REALTIME, config.pipeline)
        assertEquals(modelA, config.realtimeModel)
    }

    @Test
    fun `semi cascaded inherits the global synthesis model when no dedicated model is set`() {
        val global = defaults(pipeline = VoicePipeline.SEMI_CASCADED).copy(semiCascadedSynthesisModel = null)
        val config = VoiceConfigResolver.resolve(
            global,
            emptyOverrides().copy(cascadedSynthesisModel = VoiceFieldOverride.Value(ttsB)),
            FakeLookup(memory = mapOf((VoicePipeline.SEMI_CASCADED to ttsA) to "semi-voice")),
        )

        assertEquals(ttsA, config.semiCascadedSynthesisModel)
        assertEquals("semi-voice", config.semiCascadedVoice.voiceId)
        assertTrue(config.missingRequired.isEmpty())
    }

    @Test
    fun `semi cascaded reports only an invalid synthesis model as missing`() {
        val global = defaults(pipeline = VoicePipeline.SEMI_CASCADED)
            .copy(semiCascadedSynthesisModel = ttsB, cascadedSynthesisModel = null)
        val config = VoiceConfigResolver.resolve(global, emptyOverrides(), FakeLookup(invalidModels = setOf(ttsB)))

        assertEquals(ttsB, config.semiCascadedSynthesisModel)
        assertEquals(setOf(VoiceConfigField.SEMI_CASCADED_SYNTHESIS_MODEL), config.missingRequired)
    }

    @Test
    fun `semi cascaded understanding model is supplied by the chat selection`() {
        val global = defaults(pipeline = VoicePipeline.SEMI_CASCADED).copy(semiCascadedUnderstandingModel = null)
        val config = VoiceConfigResolver.resolve(global, emptyOverrides(), FakeLookup())

        assertNull(config.semiCascadedUnderstandingModel)
        assertTrue(config.missingRequired.isEmpty())
    }

    // ---- 恢复默认 ----

    @Test
    fun `restoring a field falls back to inheritance`() {
        val overrides = emptyOverrides().copy(
            realtimeModel = VoiceFieldOverride.Value(modelB),
        )

        val restored = emptyOverrides()
        val config = VoiceConfigResolver.resolve(defaults(), restored, FakeLookup())
        assertEquals(modelA, config.realtimeModel)
        assertFalse(restored.hasAnyOverride)
        assertTrue(restored.overriddenFields.isEmpty())
    }

    // ---- 链路记忆 ----

    @Test
    fun `each pipeline keeps its own configuration`() {
        val overrides = emptyOverrides().copy(
            cascadedSynthesisModel = VoiceFieldOverride.Value(ttsB),
            realtimeModel = VoiceFieldOverride.Value(modelB),
        )

        val config = VoiceConfigResolver.resolve(defaults(), overrides, FakeLookup())
        // 级联与原生实时互不影响：各自字段各自记忆。
        assertEquals(ttsB, config.cascadedSynthesisModel)
        assertEquals(modelB, config.realtimeModel)
        assertEquals(ttsA, config.semiCascadedSynthesisModel)
    }

    @Test
    fun `missing required fields are scoped to the active pipeline only`() {
        // 激活级联：实时与半级联缺项不算级联缺项。
        val config = VoiceConfigResolver.resolve(
            defaults().copy(realtimeModel = null),
            emptyOverrides(),
            FakeLookup(),
        )
        assertTrue(config.missingRequired.isEmpty())

        // 激活原生实时：实时模型缺失即定位该字段。
        val realtimeConfig = VoiceConfigResolver.resolve(
            defaults(realtime = null),
            emptyOverrides().copy(pipeline = VoiceFieldOverride.Value(VoicePipeline.NATIVE_REALTIME)),
            FakeLookup(),
        )
        assertEquals(setOf(VoiceConfigField.REALTIME_MODEL), realtimeConfig.missingRequired)
    }

    // ---- 音色绑定 ----

    @Test
    fun `voice is remembered per full model selection per pipeline`() {
        val lookup = FakeLookup(
            memory = mapOf(
                VoicePipeline.CASCADED to ttsA to "voice-a",
                VoicePipeline.NATIVE_REALTIME to modelA to "rt-a",
            ),
        )

        val config = VoiceConfigResolver.resolve(defaults(), emptyOverrides(), lookup)
        assertEquals("voice-a", config.cascadedVoice.voiceId)
        assertEquals("rt-a", config.realtimeVoice.voiceId)
        assertFalse(config.cascadedVoice.substituted)
    }

    @Test
    fun `switching model falls back to that model memory or vendor default`() {
        val lookup = FakeLookup(
            memory = mapOf(VoicePipeline.CASCADED to ttsA to "voice-a"),
        )
        val overrides = emptyOverrides().copy(cascadedSynthesisModel = VoiceFieldOverride.Value(ttsB))

        val config = VoiceConfigResolver.resolve(defaults(), overrides, lookup)
        // B 没有记忆音色：采用厂商默认而非沿用 A 的音色。
        assertNull(config.cascadedVoice.voiceId)
        assertFalse(config.cascadedVoice.substituted)
    }

    @Test
    fun `switching back restores remembered voice of the original model`() {
        val lookup = FakeLookup(
            memory = mapOf(
                VoicePipeline.CASCADED to ttsA to "voice-a",
                VoicePipeline.CASCADED to ttsB to "voice-b",
            ),
        )

        val onB = VoiceConfigResolver.resolve(
            defaults(),
            emptyOverrides().copy(cascadedSynthesisModel = VoiceFieldOverride.Value(ttsB)),
            lookup,
        )
        assertEquals("voice-b", onB.cascadedVoice.voiceId)

        val backToA = VoiceConfigResolver.resolve(defaults(), emptyOverrides(), lookup)
        assertEquals("voice-a", backToA.cascadedVoice.voiceId)
    }

    @Test
    fun `invalid remembered voice substitutes vendor default with explanation flag`() {
        val lookup = FakeLookup(
            memory = mapOf(VoicePipeline.NATIVE_REALTIME to modelA to "deleted-voice"),
            invalidVoices = mapOf(VoicePipeline.NATIVE_REALTIME to setOf("deleted-voice")),
        )

        val config = VoiceConfigResolver.resolve(
            defaults(pipeline = VoicePipeline.NATIVE_REALTIME),
            emptyOverrides(),
            lookup,
        )
        assertNull(config.realtimeVoice.voiceId)
        assertTrue(config.realtimeVoice.substituted)
    }

    @Test
    fun `invalid conversation voice override substitutes with explanation flag`() {
        val lookup = FakeLookup(
            invalidVoices = mapOf(VoicePipeline.CASCADED to setOf("deleted-voice")),
        )
        val overrides = emptyOverrides().copy(cascadedVoice = VoiceFieldOverride.Value("deleted-voice"))

        val config = VoiceConfigResolver.resolve(defaults(), overrides, lookup)
        assertNull(config.cascadedVoice.voiceId)
        assertTrue(config.cascadedVoice.substituted)
    }

    @Test
    fun `invalid conversation voice override falls back to remembered voice`() {
        val lookup = FakeLookup(
            invalidVoices = mapOf(VoicePipeline.CASCADED to setOf("deleted-voice")),
            memory = mapOf(VoicePipeline.CASCADED to ttsA to "voice-a"),
        )
        val overrides = emptyOverrides().copy(cascadedVoice = VoiceFieldOverride.Value("deleted-voice"))

        val config = VoiceConfigResolver.resolve(defaults(), overrides, lookup)
        // 覆盖音色失效时先尝试该模型的记忆音色，而不是直接丢回厂商默认。
        assertEquals("voice-a", config.cascadedVoice.voiceId)
        assertTrue(config.cascadedVoice.substituted)
    }

    @Test
    fun `unverifiable voice validity is treated as valid`() {
        val lookup = object : VoiceConfigLookup {
            override fun isModelValid(selection: ModelSelection) = true
            override fun isVoiceValid(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String): Boolean? = null
            override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? = "maybe-voice"
        }

        val config = VoiceConfigResolver.resolve(defaults(), emptyOverrides(), lookup)
        assertEquals("maybe-voice", config.cascadedVoice.voiceId)
        assertFalse(config.cascadedVoice.substituted)
    }

    // ---- 草稿校验 ----

    @Test
    fun `validateDraft reports missing required fields of the active pipeline`() {
        val broken = defaults(realtime = null).copy(cascadedSynthesisModel = null)
        val cascadeDraft = VoiceConfigDraft(emptyOverrides(), broken)
        assertEquals(
            setOf(VoiceConfigField.CASCADED_SYNTHESIS_MODEL),
            VoiceConfigResolver.validateDraft(broken, cascadeDraft, FakeLookup()),
        )

        val realtimeDraft = VoiceConfigDraft(
            emptyOverrides().copy(pipeline = VoiceFieldOverride.Value(VoicePipeline.NATIVE_REALTIME)),
            broken,
        )
        assertEquals(
            setOf(VoiceConfigField.REALTIME_MODEL),
            VoiceConfigResolver.validateDraft(broken, realtimeDraft, FakeLookup()),
        )
    }

    @Test
    fun `validateDraft accepts drafts whose inactive pipelines are incomplete`() {
        // 激活级联的草稿无需补齐其他链路的缺项。
        val broken = defaults(realtime = null).copy(semiCascadedUnderstandingModel = null)
        val draft = VoiceConfigDraft(emptyOverrides(), broken)
        assertTrue(VoiceConfigResolver.validateDraft(broken, draft, FakeLookup()).isEmpty())
    }

    @Test
    fun `invalid selected model counts as missing`() {
        val config = VoiceConfigResolver.resolve(
            defaults(),
            emptyOverrides(),
            FakeLookup(invalidModels = setOf(modelA)),
        )
        // 级联缺项不涉及 modelA；原生实时默认模型失效即缺项。
        assertTrue(config.missingRequired.isEmpty())
        val realtimeConfig = VoiceConfigResolver.resolve(
            defaults(),
            emptyOverrides().copy(pipeline = VoiceFieldOverride.Value(VoicePipeline.NATIVE_REALTIME)),
            FakeLookup(invalidModels = setOf(modelA)),
        )
        assertEquals(setOf(VoiceConfigField.REALTIME_MODEL), realtimeConfig.missingRequired)
    }

    private fun emptyOverrides() = ConversationVoiceOverrides(conversationId = "conversation-1")
}
