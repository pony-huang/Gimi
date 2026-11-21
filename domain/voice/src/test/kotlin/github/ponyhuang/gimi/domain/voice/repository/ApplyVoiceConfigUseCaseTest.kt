package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 对话语音配置应用流程：必需项校验、基线冲突检测与提交编排。
 * 全部使用内存假件，不依赖目录实现。
 */
class ApplyVoiceConfigUseCaseTest {

    private val tts = ModelSelection("instance-a", "conn-a", "tts-a")
    private val realtime = ModelSelection("instance-a", "conn-a", "rt-a")
    private val conversationId = "conversation-1"

    private class FakeDefaults(initial: VoiceGlobalDefaults) : VoiceGlobalDefaultsRepository {
        // MutableStateFlow 本身实现 StateFlow，直接以只读接口暴露。
        private val flow = kotlinx.coroutines.flow.MutableStateFlow(initial)
        override val defaults: kotlinx.coroutines.flow.StateFlow<VoiceGlobalDefaults> = flow
        val current: VoiceGlobalDefaults get() = flow.value
        fun set(value: VoiceGlobalDefaults) { flow.value = value }
        override suspend fun update(transform: (VoiceGlobalPreference) -> VoiceGlobalPreference) {
            val current = flow.value
            val updated = transform(
                VoiceGlobalPreference(
                    pipeline = current.pipeline,
                    semiCascadedUnderstandingModel = current.semiCascadedUnderstandingModel,
                    semiCascadedSynthesisModel = current.semiCascadedSynthesisModel,
                ),
            )
            flow.value = current.copy(
                pipeline = updated.pipeline,
                semiCascadedUnderstandingModel = updated.semiCascadedUnderstandingModel,
                semiCascadedSynthesisModel = updated.semiCascadedSynthesisModel,
            )
        }
    }

    private class FakeOverrides : VoiceOverrideRepository {
        val stored = mutableMapOf<String, ConversationVoiceOverrides>()
        var applies = 0
        override val rebuildCount = kotlinx.coroutines.flow.MutableStateFlow(0)
        override suspend fun acknowledgeRebuilds() = Unit
        override fun observe(conversationId: String) =
            kotlinx.coroutines.flow.flowOf(stored[conversationId] ?: ConversationVoiceOverrides(conversationId))
        override fun current(conversationId: String) =
            stored[conversationId] ?: ConversationVoiceOverrides(conversationId)
        override suspend fun apply(draft: VoiceConfigDraft): VoiceApplyResult {
            applies++
            val existing = current(draft.overrides.conversationId)
            if (existing == draft.overrides) return VoiceApplyResult.Unchanged
            stored[draft.overrides.conversationId] = draft.overrides
            return VoiceApplyResult.Applied
        }
        override suspend fun resetFields(conversationId: String, fields: Set<VoiceConfigField>) = Unit
        override suspend fun clear(conversationId: String) = Unit
    }

    private val validLookup = object : VoiceConfigLookup {
        override fun isModelValid(selection: ModelSelection) = true
        override fun isVoiceValid(pipeline: VoicePipeline, selection: ModelSelection, voiceId: String): Boolean? = null
        override fun rememberedVoice(pipeline: VoicePipeline, selection: ModelSelection): String? = null
    }

    private fun defaults(realtimeModel: ModelSelection? = realtime) = VoiceGlobalDefaults(
        cascadedRecognitionModel = tts,
        cascadedSynthesisModel = tts,
        realtimeModel = realtimeModel,
    )

    @Test
    fun `valid draft applies through the repository`() = runTest {
        val defaults = FakeDefaults(defaults())
        val overrides = FakeOverrides()
        val useCase = ApplyVoiceConfigUseCase(defaults, overrides, validLookup)

        val result = useCase(VoiceConfigDraft(
            ConversationVoiceOverrides(conversationId).copy(
                realtimeModel = VoiceFieldOverride.Value(realtime),
            ),
            defaults.current,
        ))

        assertEquals(VoiceApplyResult.Applied, result)
        assertEquals(1, overrides.applies)
    }

    @Test
    fun `missing required fields block apply without touching the store`() = runTest {
        val defaults = FakeDefaults(defaults(realtimeModel = null))
        val overrides = FakeOverrides()
        val useCase = ApplyVoiceConfigUseCase(defaults, overrides, validLookup)

        val result = useCase(VoiceConfigDraft(
            ConversationVoiceOverrides(conversationId).copy(
                pipeline = VoiceFieldOverride.Value(VoicePipeline.NATIVE_REALTIME),
            ),
            defaults.current,
        ))

        assertEquals(VoiceApplyResult.Invalid(setOf(VoiceConfigField.REALTIME_MODEL)), result)
        assertEquals(0, overrides.applies)
    }

    @Test
    fun `stale baseline conflicts and keeps the draft uncommitted`() = runTest {
        val defaults = FakeDefaults(defaults())
        val overrides = FakeOverrides()
        val useCase = ApplyVoiceConfigUseCase(defaults, overrides, validLookup)
        val staleBaseline = defaults.current

        // 编辑期间全局默认变化：草稿基线落后即冲突。
        defaults.set(defaults.current.copy(cascadedSynthesisModel = ModelSelection("i", "c", "tts-new")))

        val result = useCase(VoiceConfigDraft(
            ConversationVoiceOverrides(conversationId).copy(cascadedVoice = VoiceFieldOverride.Value("voice")),
            staleBaseline,
        ))

        assertEquals(VoiceApplyResult.Conflict, result)
        assertEquals(0, overrides.applies)
    }

    @Test
    fun `unrelated default changes do not conflict`() = runTest {
        val defaults = FakeDefaults(defaults())
        val overrides = FakeOverrides()
        val useCase = ApplyVoiceConfigUseCase(defaults, overrides, validLookup)
        val baseline = defaults.current
        // 草稿显式覆盖链路、方式以及该链路的两个模型：其余链路的默认值与它无关。
        val draftOverrides = ConversationVoiceOverrides(conversationId).copy(
            pipeline = VoiceFieldOverride.Value(VoicePipeline.CASCADED),
            cascadedRecognitionModel = VoiceFieldOverride.Value(tts),
            cascadedSynthesisModel = VoiceFieldOverride.Value(tts),
        )
        defaults.set(defaults.current.copy(realtimeModel = null, semiCascadedSynthesisModel = tts))

        val result = useCase(VoiceConfigDraft(draftOverrides, baseline))

        assertEquals(VoiceApplyResult.Applied, result)
        assertEquals(1, overrides.applies)
    }

    @Test
    fun `conflict wins over an invalid draft`() = runTest {
        val defaults = FakeDefaults(defaults(realtimeModel = null))
        val overrides = FakeOverrides()
        val useCase = ApplyVoiceConfigUseCase(defaults, overrides, validLookup)
        val staleBaseline = defaults.current
        // 全局默认在编辑期间变化，同时草稿选了缺模型的实时链路：
        // 缺项结论针对旧默认值，不能用来掩盖冲突；若先校验再查冲突，
        // 新默认值会让草稿看起来有效，从而错误地提交。
        defaults.set(defaults.current.copy(realtimeModel = realtime))

        val result = useCase(VoiceConfigDraft(
            ConversationVoiceOverrides(conversationId).copy(
                pipeline = VoiceFieldOverride.Value(VoicePipeline.NATIVE_REALTIME),
            ),
            staleBaseline,
        ))

        assertEquals(VoiceApplyResult.Conflict, result)
        assertEquals(0, overrides.applies)
    }

    @Test
    fun `unchanged content short-circuits without a new commit`() = runTest {
        val defaults = FakeDefaults(defaults())
        val overrides = FakeOverrides()
        val useCase = ApplyVoiceConfigUseCase(defaults, overrides, validLookup)
        val draftOverrides = ConversationVoiceOverrides(conversationId).copy(
            cascadedVoice = VoiceFieldOverride.Value("voice"),
        )

        assertEquals(VoiceApplyResult.Applied, useCase(VoiceConfigDraft(draftOverrides, defaults.current)))
        // 同一内容重复触发只产生一次提交。
        assertEquals(VoiceApplyResult.Unchanged, useCase(VoiceConfigDraft(draftOverrides, defaults.current)))
        assertEquals(2, overrides.applies)
        assertEquals(1, overrides.stored[conversationId]?.let { 1 } ?: 0)
    }
}

/** StateFlow 委托助手：把 MutableStateFlow 以只读视图暴露。 */
private fun <T> StateFlowDelegate(flow: kotlinx.coroutines.flow.MutableStateFlow<T>) =
    object : kotlinx.coroutines.flow.StateFlow<T> {
        override val value: T get() = flow.value
        override val replayCache: List<T> get() = flow.replayCache
        override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<T>): Nothing =
            flow.collect(collector)
    }
