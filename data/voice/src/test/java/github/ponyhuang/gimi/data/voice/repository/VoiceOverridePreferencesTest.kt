package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.ConversationVoiceOverrides
import github.ponyhuang.gimi.domain.voice.repository.VoiceApplyResult
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigDraft
import github.ponyhuang.gimi.domain.voice.repository.VoiceConfigField
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.voice.repository.VoiceFieldOverride
import github.ponyhuang.gimi.domain.voice.repository.VoiceGlobalDefaults
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import github.ponyhuang.gimi.domain.voice.repository.isOverridden
import io.mockk.every
import io.mockk.mockk
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话语音覆盖持久化：原子应用、重复提交幂等、恢复默认幂等、删除清理与损坏重建。
 * SharedPreferences 使用内存假件，不依赖 Android 环境。
 */
class VoiceOverridePreferencesTest {

    /** 手工排空调度器：让冷启动修复写与用户写入的交错顺序可确定。 */
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queued = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queued.addLast(block)
        }
        fun drain() {
            while (queued.isNotEmpty()) queued.removeFirst().run()
        }
    }

    /** 构造并同步执行冷启动修复；损坏数据用例必须在断言前让修复写落盘。 */
    private fun repairedRepository(preferences: SharedPreferences): VoiceOverridePreferences {
        val dispatcher = QueuedDispatcher()
        val repository = VoiceOverridePreferences(stubContext(preferences), CoroutineScope(Dispatchers.Unconfined), dispatcher)
        dispatcher.drain()
        return repository
    }

    private val conversationId = "conversation-1"
    private val tts = ModelSelection("instance-a", "conn-a", "tts-a")
    private val ttsOther = ModelSelection("instance-b", "conn-b", "tts-b")

    /** 应用私有偏好的内存假件；返回偏好与底层存储以便注入失败。 */
    private fun memoryPreferences(): Pair<SharedPreferences, MutableMap<String, Any?>> {
        val values = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { values[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { values.remove(firstArg()); editor }
        // 生产代码改用同步 commit：内存假件必须返回成功，否则写入会被判为落盘失败。
        every { editor.commit() } returns true
        val preferences = mockk<SharedPreferences>()
        every { preferences.all } answers { values.toMap() }
        every { preferences.getString(any(), any()) } answers { values[firstArg()] as? String ?: secondArg() }
        every { preferences.edit() } returns editor
        return preferences to values
    }

    private fun stubContext(preferences: SharedPreferences): Context = mockk {
        every { getSharedPreferences(any(), any()) } returns preferences
    }

    private fun draftWith(overrides: ConversationVoiceOverrides, baseline: VoiceGlobalDefaults) =
        VoiceConfigDraft(overrides, baseline)

    // ---- 原子应用与重复提交 ----

    @Test
    fun `apply persists the whole batch and duplicate apply is unchanged`() = runTest {
        val (preferences, _) = memoryPreferences()
        val repository = repairedRepository(preferences)

        val overrides = ConversationVoiceOverrides(conversationId).copy(
            cascadedSynthesisModel = VoiceFieldOverride.Value(tts),
        )
        val draft = draftWith(overrides, VoiceGlobalDefaults())
        assertEquals(VoiceApplyResult.Applied, repository.apply(draft))
        // 重复应用同一内容只提交一次。
        assertEquals(VoiceApplyResult.Unchanged, repository.apply(draft))
        assertEquals(overrides, repository.current(conversationId))
        // 重新进入对话恢复其覆盖。
        assertEquals(overrides, repository.observe(conversationId).first())
    }

    @Test
    fun `apply with changed content commits again`() = runTest {
        val (preferences, _) = memoryPreferences()
        val repository = repairedRepository(preferences)
        val first = draftWith(
            ConversationVoiceOverrides(conversationId).copy(cascadedVoice = VoiceFieldOverride.Value("voice-a")),
            VoiceGlobalDefaults(),
        )
        assertEquals(VoiceApplyResult.Applied, repository.apply(first))
        val second = draftWith(
            ConversationVoiceOverrides(conversationId).copy(cascadedVoice = VoiceFieldOverride.Value("voice-b")),
            VoiceGlobalDefaults(),
        )
        assertEquals(VoiceApplyResult.Applied, repository.apply(second))
        assertEquals("voice-b", (repository.current(conversationId).cascadedVoice as VoiceFieldOverride.Value).value)
    }

    @Test
    fun `storage failure keeps memory consistent and propagates`() = runTest {
        val (preferences, values) = memoryPreferences()
        val repository = repairedRepository(preferences)
        // 注入写入失败：apply 上抛，内存流不出现半提交状态。
        every { preferences.edit() } throws IllegalStateException("disk full")
        val draft = draftWith(
            ConversationVoiceOverrides(conversationId).copy(cascadedSynthesisModel = VoiceFieldOverride.Value(tts)),
            VoiceGlobalDefaults(),
        )
        try {
            repository.apply(draft)
            throw AssertionError("apply should propagate storage failure")
        } catch (expected: IllegalStateException) {
            assertEquals("disk full", expected.message)
        }
        assertTrue(repository.current(conversationId).hasAnyOverride.not())
    }

    // ---- 恢复默认 ----

    @Test
    fun `resetFields restores inheritance and repeated reset is idempotent`() = runTest {
        val (preferences, _) = memoryPreferences()
        val repository = repairedRepository(preferences)
        repository.apply(draftWith(
            ConversationVoiceOverrides(conversationId).copy(
                cascadedSynthesisModel = VoiceFieldOverride.Value(tts),
                realtimeModel = VoiceFieldOverride.Value(ttsOther),
            ),
            VoiceGlobalDefaults(),
        ))

        repository.resetFields(conversationId, setOf(VoiceConfigField.CASCADED_SYNTHESIS_MODEL))
        val afterFirst = repository.current(conversationId)
        assertFalse(afterFirst.cascadedSynthesisModel.isOverridden)
        assertTrue(afterFirst.realtimeModel.isOverridden)

        // 重复恢复保持继承状态，不再产生提交。
        repository.resetFields(conversationId, setOf(VoiceConfigField.CASCADED_SYNTHESIS_MODEL))
        assertEquals(afterFirst, repository.current(conversationId))
    }

    // ---- 删除清理 ----

    @Test
    fun `clear removes conversation overrides entirely`() = runTest {
        val (preferences, values) = memoryPreferences()
        val repository = repairedRepository(preferences)
        repository.apply(draftWith(
            ConversationVoiceOverrides(conversationId).copy(realtimeModel = VoiceFieldOverride.Value(ttsOther)),
            VoiceGlobalDefaults(),
        ))
        assertTrue(values.containsKey(conversationId))

        repository.clear(conversationId)
        assertFalse(values.containsKey(conversationId))
        assertEquals(ConversationVoiceOverrides(conversationId), repository.observe(conversationId).first())
        // 清理幂等。
        repository.clear(conversationId)
    }

    // ---- 当前格式初始化与重建提示 ----

    @Test
    fun `readable explicit choices survive while corrupted fields fall back to inheritance and are repaired`() {
        val (preferences, _) = memoryPreferences()
        // 枚举名损坏但模型选择可读：可读取的明确选择保留，损坏字段按继承处理。
        preferences.edit {
            putString(conversationId, "{\"pipeline\":\"BROKEN\",\"cascadedSynthesisModel\":" +
                "{\"instanceId\":\"instance-a\",\"connectionId\":\"conn-a\",\"modelId\":\"tts-a\"}}")
        }
        val repository = repairedRepository(preferences)
        val overrides = repository.current(conversationId)
        assertEquals(tts, (overrides.cascadedSynthesisModel as VoiceFieldOverride.Value).value)
        // 字段级损坏也要提示，并把降级结果写回，避免下次冷启动重复计数。
        assertEquals(1, repository.rebuildCount.value)
        assertEquals(0, repairedRepository(preferences).rebuildCount.value)
    }

    @Test
    fun `cold start repair never overwrites a value written before it runs`() {
        val (preferences, values) = memoryPreferences()
        preferences.edit { putString(conversationId, "{\"pipeline\":\"BROKEN\"}") }
        val dispatcher = QueuedDispatcher()
        val repository = VoiceOverridePreferences(
            stubContext(preferences),
            CoroutineScope(Dispatchers.Unconfined),
            dispatcher,
        )
        assertEquals(1, repository.rebuildCount.value)

        // 修复协程真正执行前用户已写入新值：旧快照必须放弃修复，否则下次冷启动丢失新值。
        val fresh = "{\"pipeline\":\"NATIVE_REALTIME\"}"
        preferences.edit { putString(conversationId, fresh) }
        dispatcher.drain()

        assertEquals(fresh, values[conversationId])
    }

    @Test
    fun `unparseable entry is removed from disk and raises rebuild notice`() {
        val (preferences, values) = memoryPreferences()
        preferences.edit { putString(conversationId, "{not-json") }
        val repository = repairedRepository(preferences)
        assertEquals(ConversationVoiceOverrides(conversationId), repository.current(conversationId))
        assertEquals(1, repository.rebuildCount.value)
        assertFalse(values.containsKey(conversationId))
        // 冷启动不会因同一条损坏数据再次提示。
        assertEquals(0, repairedRepository(preferences).rebuildCount.value)
    }

    @Test
    fun `blank conversation id never writes a shared override`() = runTest {
        val (preferences, values) = memoryPreferences()
        val repository = repairedRepository(preferences)
        val draft = draftWith(
            ConversationVoiceOverrides("").copy(cascadedVoice = VoiceFieldOverride.Value("voice-a")),
            VoiceGlobalDefaults(),
        )
        assertEquals(VoiceApplyResult.Unchanged, repository.apply(draft))
        assertTrue(values.isEmpty())
        assertEquals(ConversationVoiceOverrides(""), repository.current(""))
    }

    @Test
    fun `failed commit is reported instead of silently marking the draft saved`() = runTest {
        val (preferences, _) = memoryPreferences()
        val repository = VoiceOverridePreferences(stubContext(preferences), CoroutineScope(Dispatchers.Unconfined))
        every { preferences.edit() } returns mockk<SharedPreferences.Editor>(relaxed = true)
        val draft = draftWith(
            ConversationVoiceOverrides(conversationId).copy(cascadedVoice = VoiceFieldOverride.Value("voice-a")),
            VoiceGlobalDefaults(),
        )
        try {
            repository.apply(draft)
            throw AssertionError("apply should propagate a failed commit")
        } catch (expected: java.io.IOException) {
            // 内存状态也不得出现已保存的覆盖。
        }
        assertFalse(repository.current(conversationId).hasAnyOverride)
    }

    @Test
    fun `acknowledging rebuilds clears the notice`() = runTest {
        val (preferences, _) = memoryPreferences()
        preferences.edit { putString(conversationId, "{not-json") }
        val repository = repairedRepository(preferences)
        assertEquals(1, repository.rebuildCount.value)
        repository.acknowledgeRebuilds()
        assertEquals(0, repository.rebuildCount.value)
    }

    @Test
    fun `fresh store initializes to full inheritance`() = runTest {
        val (preferences, _) = memoryPreferences()
        val repository = repairedRepository(preferences)
        assertEquals(ConversationVoiceOverrides(conversationId), repository.observe(conversationId).first())
        assertEquals(0, repository.rebuildCount.value)
    }
}
