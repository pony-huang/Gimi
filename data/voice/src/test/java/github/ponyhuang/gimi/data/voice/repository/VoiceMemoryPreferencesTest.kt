package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 模型音色记忆持久化：按链路与完整模型选择记忆，切回模型恢复其音色。
 * SharedPreferences 使用内存假件，不依赖 Android 环境。
 */
class VoiceMemoryPreferencesTest {

    private val ttsA = ModelSelection("instance-a", "conn-a", "tts-a")
    private val ttsB = ModelSelection("instance-b", "conn-b", "tts-b")
    private val realtimeA = ModelSelection("instance-a", "conn-a", "rt-a")

    /** 应用私有偏好的内存假件；返回偏好与底层存储供新实例重放读取。 */
    private fun memoryPreferences(): Pair<SharedPreferences, MutableMap<String, Any?>> {
        val values = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { values[firstArg()] = secondArg(); editor }
        // 生产代码改用同步 commit：内存假件必须返回成功，否则记忆写入会被判为落盘失败。
        every { editor.commit() } returns true
        val preferences = mockk<SharedPreferences>()
        every { preferences.all } answers { values.toMap() }
        every { preferences.getString(any(), any()) } answers { values[firstArg()] as? String ?: secondArg() }
        every { preferences.edit() } returns editor
        return preferences to values
    }

    private fun repository(preferences: SharedPreferences) = VoiceMemoryPreferences(mockk {
        every { getSharedPreferences(any(), any()) } returns preferences
    })

    @Test
    fun `voice is remembered per model selection and restored on switching back`() = runTest {
        val repository = repository(memoryPreferences().first)
        repository.rememberVoice(VoicePipeline.CASCADED, ttsA, "voice-a")
        // 切到 B：B 没有记忆，采用厂商默认。
        assertNull(repository.rememberedVoice(VoicePipeline.CASCADED, ttsB))
        repository.rememberVoice(VoicePipeline.CASCADED, ttsB, "voice-b")
        assertEquals("voice-b", repository.rememberedVoice(VoicePipeline.CASCADED, ttsB))
        // 切回 A：恢复 A 的合法记忆音色。
        assertEquals("voice-a", repository.rememberedVoice(VoicePipeline.CASCADED, ttsA))
    }

    @Test
    fun `each pipeline keeps its own memory`() = runTest {
        val repository = repository(memoryPreferences().first)
        repository.rememberVoice(VoicePipeline.CASCADED, ttsA, "voice-a")
        repository.rememberVoice(VoicePipeline.NATIVE_REALTIME, realtimeA, "rt-a")
        assertEquals("voice-a", repository.rememberedVoice(VoicePipeline.CASCADED, ttsA))
        assertEquals("rt-a", repository.rememberedVoice(VoicePipeline.NATIVE_REALTIME, realtimeA))
        // 同一模型选择在不同链路互不混淆。
        assertNull(repository.rememberedVoice(VoicePipeline.NATIVE_REALTIME, ttsA))
    }

    @Test
    fun `repeated remember keeps the latest choice`() = runTest {
        val repository = repository(memoryPreferences().first)
        repository.rememberVoice(VoicePipeline.CASCADED, ttsA, "voice-a")
        repository.rememberVoice(VoicePipeline.CASCADED, ttsA, "voice-a2")
        assertEquals("voice-a2", repository.rememberedVoice(VoicePipeline.CASCADED, ttsA))
    }

    @Test
    fun `memory persists across instances`() = runTest {
        val (preferences, _) = memoryPreferences()
        repository(preferences).rememberVoice(VoicePipeline.NATIVE_REALTIME, realtimeA, "rt-a")
        // 新实例从同一存储重建记忆。
        assertEquals("rt-a", repository(preferences).rememberedVoice(VoicePipeline.NATIVE_REALTIME, realtimeA))
    }

    @Test
    fun `corrupted entries are dropped instead of failing`() {
        val (preferences, _) = memoryPreferences()
        preferences.edit()?.putString("realtime_voices", "{not-json")?.apply()
        val repository = repository(preferences)
        assertNull(repository.rememberedVoice(VoicePipeline.NATIVE_REALTIME, realtimeA))
        assertNull(repository.rememberedVoice(VoicePipeline.CASCADED, ttsA))
    }

    @Test
    fun `null elements and missing fields are skipped without breaking construction`() {
        val (preferences, values) = memoryPreferences()
        // Gson 可在数组里产出 null 元素，也可让非空 Kotlin 字段静默为 null。
        values["cascaded_voices"] = """[null,{"selection":{"modelId":null},"voiceId":"orphan"},""" +
            """{"selection":null,"voiceId":null},""" +
            """{"selection":{"instanceId":"instance-a","connectionId":"conn-a","modelId":"tts-a"},"voiceId":"voice-a"}]"""
        val repository = repository(preferences)
        assertEquals("voice-a", repository.rememberedVoice(VoicePipeline.CASCADED, ttsA))
    }

    @Test
    fun `failed commit propagates and keeps the previous memory`() = runTest {
        val (preferences, _) = memoryPreferences()
        val repository = repository(preferences)
        repository.rememberVoice(VoicePipeline.CASCADED, ttsA, "voice-a")
        // commit() 返回 false 等价于落盘失败：不得先把内存更新成新音色。
        every { preferences.edit() } returns mockk<SharedPreferences.Editor>(relaxed = true)
        try {
            repository.rememberVoice(VoicePipeline.CASCADED, ttsA, "voice-b")
            throw AssertionError("rememberVoice should propagate a failed commit")
        } catch (expected: java.io.IOException) {
            // expected
        }
        assertEquals("voice-a", repository.rememberedVoice(VoicePipeline.CASCADED, ttsA))
    }
}
