package github.ponyhuang.gimi.data.modelcatalog

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelGroup
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            isFileUploadEnabled = true, fileUploadRetentionDays = 30,
            groups = listOf(ModelGroup("custom", "Custom", listOf(Model("new", "New")))))
        assertFalse(repository.saveService(draft))
        assertEquals(original, repository.currentService("deepseek"))
        assertEquals(revision, repository.configurationRevision.value)
    }

    @Test
    fun selectedModelsAndToolPreferencesSurviveRestartPauseAndResume() = runBlocking {
        val repository = readyRepository()
        val service = repository.currentService("minimax")!!.copy(apiKey = "fake-key", isEnabled = true,
            isOfficialToolsEnabled = false, isFileUploadEnabled = true, fileUploadRetentionDays = 30,
            groups = listOf(ModelGroup("custom", "Custom",
                listOf(Model("voice", "Voice", isTts = true)))))
        assertTrue(repository.saveService(service))
        assertTrue(repository.setServiceEnabled("minimax", false))
        assertTrue(repository.setServiceEnabled("minimax", true))
        val restored = readyRepository().currentService("minimax")!!
        assertTrue(restored.isAdded)
        assertTrue(restored.isEnabled)
        assertFalse(restored.isOfficialToolsEnabled)
        assertTrue(restored.isFileUploadEnabled)
        assertEquals(30, restored.fileUploadRetentionDays)
        assertEquals(listOf("voice"), restored.groups.flatMap { it.models }.map { it.id })
        assertTrue(restored.groups.single().models.single().isTts)
        assertTrue(repository.modelCandidates("minimax").flatMap { it.models }.any { it.id != "voice" })
    }

    @Test
    fun zeroSelectedModelsStayEmptyAfterRestartAndDeletionRemovesCredentials() = runBlocking {
        val repository = readyRepository()
        val service = repository.currentService("deepseek")!!.copy(apiKey = "fake-key", isEnabled = true,
            isOfficialToolsEnabled = true, isFileUploadEnabled = true, fileUploadRetentionDays = 1, groups = emptyList())
        assertTrue(repository.saveService(service))
        assertTrue(readyRepository().currentService("deepseek")!!.groups.isEmpty())
        assertTrue(repository.deleteService("deepseek"))
        val restored = readyRepository().currentService("deepseek")!!
        assertFalse(restored.isAdded)
        assertFalse(restored.isEnabled)
        assertFalse(restored.isOfficialToolsEnabled)
        assertFalse(restored.isFileUploadEnabled)
        assertEquals(7, restored.fileUploadRetentionDays)
        assertEquals("", restored.apiKey)
        assertFalse(repository.setServiceEnabled("deepseek", true))
    }

    @Test
    fun invalidUploadRetentionDoesNotCommitOrChangeTheDefaultProtocol() = runBlocking {
        val repository = readyRepository()
        val original = repository.currentService("deepseek")!!
        assertFalse(original.isFileUploadEnabled)
        assertEquals(7, original.fileUploadRetentionDays)
        assertFalse(repository.saveService(original.copy(apiKey = "fake-key", fileUploadRetentionDays = 2)))
        assertEquals(original, repository.currentService("deepseek"))
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
