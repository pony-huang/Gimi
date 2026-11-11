package github.ponyhuang.gimi.data.modelcatalog

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 单次加密提交与运行时发布的风险边界；仅替换密码器，不访问真实 Keystore 或厂商网络。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ModelServiceConfigurationPersistenceTest {
    private lateinit var database: LLMModelRoomDatabase
    private lateinit var preferences: FailingPreferences
    private lateinit var context: Context

    @Before
    fun setUp() {
        val application = RuntimeEnvironment.getApplication()
        preferences = FailingPreferences(application.getSharedPreferences("model-service-test", Context.MODE_PRIVATE))
        preferences.edit().clear().commit()
        context = object : ContextWrapper(application) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = preferences
        }
        database = Room.inMemoryDatabaseBuilder(application, LLMModelRoomDatabase::class.java).build()
        mockkConstructor(KeystoreAesGcmStringCipher::class)
        every { anyConstructed<KeystoreAesGcmStringCipher>().encryptOrThrow(any()) } answers { "cipher:" + firstArg<String>() }
        every { anyConstructed<KeystoreAesGcmStringCipher>().decryptHealing(any()) } answers { firstArg<String>().removePrefix("cipher:") }
    }

    @After
    fun tearDown() {
        unmockkConstructor(KeystoreAesGcmStringCipher::class)
    }

    @Test
    fun failedCommitDoesNotPublishAnyPartOfTheDraft() = runBlocking {
        val repository = readyRepository()
        val original = repository.currentService("deepseek")!!
        val revision = repository.configurationRevision.value
        preferences.failWrites = true
        val draft = original.copy(apiKey = "fake-key", isEnabled = true, isAdded = true,
            groups = listOf(ModelGroup("custom", "Custom", listOf(Model("new", "New")))))
        assertFalse(repository.saveService(draft))
        assertEquals(original, repository.currentService("deepseek"))
        assertEquals(revision, repository.configurationRevision.value)
    }

    @Test
    fun selectedModelsAndToolPreferencesSurviveRestartPauseAndResume() = runBlocking {
        val repository = readyRepository()
        val service = repository.currentService("minimax-speech")!!.copy(apiKey = "fake-key", isEnabled = true,
            isOfficialToolsEnabled = false, groups = listOf(ModelGroup("custom", "Custom",
                listOf(Model("voice", "Voice", capabilities = setOf(ModelCapability.TTS))))))
        assertTrue(repository.saveService(service))
        assertTrue(repository.setServiceEnabled("minimax-speech", false))
        assertTrue(repository.setServiceEnabled("minimax-speech", true))
        val restored = readyRepository().currentService("minimax-speech")!!
        assertTrue(restored.isAdded)
        assertTrue(restored.isEnabled)
        assertFalse(restored.isOfficialToolsEnabled)
        assertEquals(listOf("voice"), restored.groups.flatMap { it.models }.map { it.id })
        assertTrue(ModelCapability.TTS in restored.groups.single().models.single().capabilities)
        assertTrue(repository.modelCandidates("minimax-speech").flatMap { it.models }.any { it.id != "voice" })
    }

    @Test
    fun zeroSelectedModelsStayEmptyAfterRestart() = runBlocking {
        val repository = readyRepository()
        // 未配置密钥的服务不可启用；空模型选择需要原样持久化。
        assertFalse(repository.setServiceEnabled("deepseek", true))
        val service = repository.currentService("deepseek")!!.copy(apiKey = "fake-key", isEnabled = true,
            isOfficialToolsEnabled = true, groups = emptyList())
        assertTrue(repository.saveService(service))
        assertTrue(readyRepository().currentService("deepseek")!!.groups.isEmpty())
    }

    @Test
    fun speechAndLlmSettingsRemainIndependentAcrossRestart() = runBlocking {
        val repository = readyRepository()
        for (brand in listOf("minimax", "mimo")) {
            val llm = repository.currentService(brand)!!.copy(apiKey = "llm-key", isEnabled = true)
            val speech = repository.currentService("$brand-speech")!!.copy(apiKey = "voice-key", isEnabled = true,
                groups = listOf(ModelGroup("voice", "Voice", listOf(Model("voice-id", "Voice", capabilities = setOf(ModelCapability.TTS))))))
            assertTrue(repository.saveService(llm))
            assertTrue(repository.saveService(speech))
            assertTrue(repository.setServiceEnabled(speech.id, false))
            val restored = readyRepository()
            assertEquals("llm-key", restored.currentService(brand)!!.apiKey)
            assertTrue(restored.currentService(brand)!!.isEnabled)
            assertEquals(llm.groups, restored.currentService(brand)!!.groups)
            assertEquals("voice-key", restored.currentService(speech.id)!!.apiKey)
            assertFalse(restored.currentService(speech.id)!!.isEnabled)
            assertEquals(speech.groups, restored.currentService(speech.id)!!.groups)
        }
    }

    @Test
    fun workspaceIdSurvivesRestart() = runBlocking {
        val repository = readyRepository()
        val service = repository.currentService("qwen-audio")!!.copy(
            apiKey = "fake-key", isEnabled = true,
            workspaceId = " ws-123 ",
        )
        assertTrue(repository.saveService(service))
        val restored = readyRepository().currentService("qwen-audio")!!
        // 业务空间 ID 保存前规范化（trim）。
        assertEquals("ws-123", restored.workspaceId)
    }

    @Test
    fun realtimeDefaultSelectionAndVoiceSurviveRestart() = runBlocking {
        val repository = readyRepository()
        val selection = ModelSelection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus")
        val override = RealtimeVoiceOverride(selection, "cloned_voice_id")
        repository.selectRealtimeModel(selection)
        repository.selectRealtimeVoice(override)
        val restored = readyRepository()
        assertEquals(selection, restored.currentRealtimeSelection())
        assertEquals(override, restored.currentRealtimeVoice())
    }

    @Test
    fun clearingRealtimeDefaultsRemovesThem() = runBlocking {
        val repository = readyRepository()
        val selection = ModelSelection("qwen-audio", "qwen-audio-realtime", "qwen-audio-3.1-realtime-plus")
        repository.selectRealtimeModel(selection)
        repository.selectRealtimeVoice(RealtimeVoiceOverride(selection, "cloned_voice_id"))
        repository.selectRealtimeModel(null)
        repository.selectRealtimeVoice(null)
        val restored = readyRepository()
        assertNull(restored.currentRealtimeSelection())
        assertNull(restored.currentRealtimeVoice())
    }

    private suspend fun readyRepository(): ModelServiceRepository = ModelServiceRepository(context, database).also {
        withTimeout(10_000) { it.awaitReady() }
    }

    /** 模拟磁盘提交失败，确保测试的是仓库发布边界而非数据类 copy。 */
    private class FailingPreferences(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        var failWrites = false
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }
                override fun commit(): Boolean = if (failWrites) false else editor.commit()
            }
        }
    }
}
