package github.ponyhuang.gimi.data.modelcatalog

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import github.ponyhuang.gimi.core.security.KeystoreAesGcmStringCipher
import github.ponyhuang.gimi.domain.modelcatalog.model.CredentialSource
import github.ponyhuang.gimi.domain.modelcatalog.model.DefaultModelRole
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceInstance
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelServiceDraft
import github.ponyhuang.gimi.domain.modelcatalog.model.RealtimeVoiceOverride
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceCapability
import github.ponyhuang.gimi.domain.modelcatalog.model.ServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.usecase.toConnection
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
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

/** 单次加密提交、引用一致性与并发启停的风险边界；仅替换密码器，不访问真实 Keystore 或厂商网络。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class ModelServiceConfigurationPersistenceTest {
    private lateinit var database: ModelTemplateDatabase
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
        database = Room.inMemoryDatabaseBuilder(application, ModelTemplateDatabase::class.java).build()
        mockkConstructor(KeystoreAesGcmStringCipher::class)
        every { anyConstructed<KeystoreAesGcmStringCipher>().encryptOrThrow(any()) } answers { "cipher:" + firstArg<String>() }
        every { anyConstructed<KeystoreAesGcmStringCipher>().decryptHealing(any()) } answers { firstArg<String>().removePrefix("cipher:") }
    }

    @After
    fun tearDown() {
        unmockkConstructor(KeystoreAesGcmStringCipher::class)
        database.close()
    }

    @Test
    fun failedCommitDoesNotPublishAnyPartOfTheDraft() = runBlocking {
        val repository = readyRepository()
        val saved = repository.saveNewInstance(openAiDraft(sharedApiKey = "fake-key"))!!
        val revision = repository.configurationRevision.value
        preferences.failWrites = true
        val draft = editDraft(saved, name = "改名后的实例")
        assertFalse(repository.saveInstance(draft))
        assertEquals("OpenAI 个人", repository.currentInstance(saved.id)?.name)
        assertEquals(revision, repository.configurationRevision.value)
    }

    @Test
    fun twoInstancesFromOneTemplateStayIndependentAcrossRestart() = runBlocking {
        val repository = readyRepository()
        val personal = repository.saveNewInstance(openAiDraft(name = "OpenAI 个人", sharedApiKey = "key-a"))!!
        val work = repository.saveNewInstance(
            openAiDraft(name = "OpenAI 工作", sharedApiKey = "key-b").copy(
                connections = openAiDraft().connections.map { connection ->
                    if (connection.capability == ServiceCapability.Conversation) {
                        connection.copy(models = listOf(Model("gpt-4.1-mini", "gpt-4.1-mini")))
                    } else {
                        connection
                    }
                },
            ),
        )!!

        val restored = readyRepository()
        assertEquals(setOf(personal.id, work.id), restored.currentInstances().map { it.id }.toSet())
        // 凭据、模型与启停互不串扰
        assertEquals("key-a", restored.currentInstance(personal.id)?.sharedApiKey)
        assertEquals("key-b", restored.currentInstance(work.id)?.sharedApiKey)
        assertEquals(
            listOf("gpt-4.1"),
            restored.currentInstance(personal.id)?.conversationConnection?.models?.map { it.id },
        )
        assertEquals(
            listOf("gpt-4.1-mini"),
            restored.currentInstance(work.id)?.conversationConnection?.models?.map { it.id },
        )
    }

    @Test
    fun customInstanceSurvivesRestart() = runBlocking {
        val repository = readyRepository()
        val draft = customDraft(
            name = "我的中转服务",
            protocol = "Anthropic",
            endpoint = "https://relay.example.com",
            apiKey = "relay-key",
            models = listOf(Model("chat-model-v1", "日常对话")),
        )
        val saved = repository.saveNewInstance(draft)!!
        assertTrue(repository.setInstanceEnabled(saved.id, false))

        val restored = readyRepository()
        val instance = restored.currentInstance(saved.id)!!
        assertNull(instance.templateId)
        assertFalse(instance.isEnabled)
        val connection = instance.conversationConnection!!
        assertEquals("Anthropic", connection.protocol.name)
        assertEquals("https://relay.example.com", connection.endpoint)
        assertEquals("relay-key", instance.sharedApiKey)
        assertEquals(listOf("chat-model-v1"), connection.models.map { it.id })
        // 保存草稿读取存储中的最新启用状态：编辑停用的服务保持停用。
        assertTrue(restored.saveInstance(editDraft(instance, name = "我的中转服务")))
        assertEquals(false, restored.currentInstance(saved.id)?.isEnabled)
    }

    @Test
    fun loadFillsNewTemplateConnectionsIntoExistingInstances() = runBlocking {
        val repository = readyRepository()
        // 升级前的快照：DeepSeek 实例只有对话连接（当前模板已新增识别/合成连接）
        val conversationOnly = ModelServiceDraft(
            instanceId = null,
            templateId = "deepseek",
            name = "DeepSeek",
            isOfficialToolsEnabled = true,
            sharedApiKey = "deepseek-key",
            connections = listOf(
                ModelServiceTemplates.fromId("deepseek")!!.connection(ServiceCapability.Conversation)!!
                    .toConnection()
                    .copy(models = listOf(Model("deepseek-chat", "deepseek-chat"))),
            ),
        )
        val saved = repository.saveNewInstance(conversationOnly)!!
        assertEquals(1, repository.currentInstance(saved.id)?.connections?.size)

        // 重新装载触发启动对账：模板新增的语音连接补进已建实例
        val restored = readyRepository()
        val instance = restored.currentInstance(saved.id)!!
        assertEquals(
            setOf(ServiceCapability.Conversation, ServiceCapability.Recognition, ServiceCapability.Synthesis),
            instance.connections.map { it.capability }.toSet(),
        )
        // 既有连接不动：ID、已选模型与凭据保持原样
        val conversation = instance.conversationConnection!!
        assertEquals(saved.conversationConnection?.id, conversation.id)
        assertEquals(listOf("deepseek-chat"), conversation.models.map { it.id })
        assertEquals("deepseek-key", instance.sharedApiKey)
        // 新连接按模板首选项派生：官方端点、空模型、共享凭据
        val recognition = instance.connection(ServiceCapability.Recognition)!!
        assertEquals("https://api.deepseek.com", recognition.endpoint)
        assertTrue(recognition.models.isEmpty())
        assertEquals(CredentialSource.Shared, recognition.credential)
        assertEquals("https://api.deepseek.com", instance.connection(ServiceCapability.Synthesis)!!.endpoint)

        // 再次装载保持幂等：连接数量与 ID 稳定，不重复追加
        val reloaded = readyRepository()
        assertEquals(
            instance.connections.map { it.id },
            reloaded.currentInstance(saved.id)!!.connections.map { it.id },
        )
    }

    @Test
    fun speechConnectionOverrideIsolatesCredentials() = runBlocking {
        val repository = readyRepository()
        val draft = openAiDraft(sharedApiKey = "shared-key").copy(
            connections = openAiDraft().connections.map { connection ->
                if (connection.capability == ServiceCapability.Realtime) {
                    connection.copy(
                        credential = CredentialSource.Independent(" realtime-key "),
                        models = listOf(Model("gpt-realtime-2.1", "gpt-realtime-2.1")),
                    )
                } else if (connection.capability == ServiceCapability.Conversation) {
                    connection.copy(models = listOf(Model("gpt-4.1", "gpt-4.1")))
                } else {
                    connection
                }
            },
        )
        val saved = repository.saveNewInstance(draft)!!

        val restored = readyRepository()
        val instance = restored.currentInstance(saved.id)!!
        assertEquals(
            CredentialSource.Independent("realtime-key"),
            instance.connection(ServiceCapability.Realtime)?.credential,
        )
        assertEquals("shared-key", instance.effectiveApiKey(instance.conversationConnection!!))
        assertEquals("realtime-key", instance.effectiveApiKey(instance.connection(ServiceCapability.Realtime)!!))
        // 保存前独立凭据已 trim
        assertEquals("realtime-key", (instance.connection(ServiceCapability.Realtime)?.credential
            as? CredentialSource.Independent)?.apiKey)
    }

    @Test
    fun vendorManagedEndpointsSnapToTemplateDefaultsOnSave() = runBlocking {
        val repository = readyRepository()
        val draft = openAiDraft(sharedApiKey = "fake-key").copy(
            connections = openAiDraft().connections.map { connection ->
                when (connection.capability) {
                    ServiceCapability.Recognition -> connection.copy(endpoint = "https://user.example.com/edited")
                    ServiceCapability.Conversation -> connection.copy(endpoint = "https://api.example.com/v1")
                    else -> connection
                }
            },
        )
        val saved = repository.saveNewInstance(draft)!!
        val restored = readyRepository()
        assertEquals(
            "https://api.openai.com/v1",
            restored.currentInstance(saved.id)?.connection(ServiceCapability.Recognition)?.endpoint,
        )
        // 用户可配置的对话端点保持用户值
        assertEquals(
            "https://api.example.com/v1",
            restored.currentInstance(saved.id)?.conversationConnection?.endpoint,
        )
    }

    @Test
    fun draftWithoutSelectedModelsIsRejected() = runBlocking {
        val repository = readyRepository()
        val draft = openAiDraft(sharedApiKey = "fake-key").copy(
            connections = openAiDraft().connections.map { connection -> connection.copy(models = emptyList()) },
        )
        assertNull(repository.saveNewInstance(draft))
    }

    @Test
    fun deletionClearsAffectedDefaultsAndBoundVoices() = runBlocking {
        val repository = readyRepository()
        val instance = repository.saveNewInstance(openAiDraft(sharedApiKey = "fake-key"))!!
        val connectionId = instance.conversationConnection!!.id
        val realtimeId = instance.connection(ServiceCapability.Realtime)!!.id
        repository.selectAssistantModel(ModelSelection(instance.id, connectionId, "gpt-4.1"))
        repository.selectSpeechModel(
            ModelSelection(instance.id, instance.connection(ServiceCapability.Recognition)!!.id, "whisper-1"),
        )
        val realtimeSelection = ModelSelection(instance.id, realtimeId, "gpt-realtime-2.1")
        repository.selectRealtimeModel(realtimeSelection)
        repository.selectRealtimeVoice(RealtimeVoiceOverride(realtimeSelection, "cloned_voice_id"))

        val impact = repository.deletionImpact(instance.id)
        assertEquals(
            setOf(DefaultModelRole.Assistant, DefaultModelRole.SpeechRecognition, DefaultModelRole.Realtime),
            impact.roles.toSet(),
        )
        assertTrue(impact.hasRealtimeVoiceBinding)

        assertTrue(repository.deleteInstance(instance.id))
        val restored = readyRepository()
        assertNull(restored.currentInstance(instance.id))
        assertNull(restored.currentAssistantSelection())
        assertNull(restored.currentSpeechSelection())
        assertNull(restored.currentRealtimeSelection())
        assertNull(restored.currentRealtimeVoice())
    }

    @Test
    fun deletionKeepsUnrelatedDefaults() = runBlocking {
        val repository = readyRepository()
        val openAi = repository.saveNewInstance(openAiDraft(sharedApiKey = "fake-key"))!!
        val minimax = repository.saveNewInstance(
            minimaxDraft(sharedApiKey = "mm-key").let { draft ->
                draft.copy(
                    connections = draft.connections.map { connection ->
                        if (connection.capability == ServiceCapability.Conversation) {
                            connection.copy(models = listOf(Model("MiniMax-Text-01", "MiniMax-Text-01")))
                        } else {
                            connection
                        }
                    },
                )
            },
        )!!
        repository.selectAssistantModel(
            ModelSelection(minimax.id, minimax.conversationConnection!!.id, "MiniMax-Text-01"),
        )
        repository.selectFastModel(
            ModelSelection(openAi.id, openAi.conversationConnection!!.id, "gpt-4.1"),
        )

        assertTrue(repository.deleteInstance(openAi.id))
        val restored = readyRepository()
        assertEquals(
            ModelSelection(minimax.id, minimax.conversationConnection!!.id, "MiniMax-Text-01"),
            restored.currentAssistantSelection(),
        )
        assertNull(restored.currentFastSelection())
    }

    @Test
    fun concurrentEnableAndSaveStayConsistent() = runBlocking {
        val repository = readyRepository()
        val saved = repository.saveNewInstance(openAiDraft(sharedApiKey = "fake-key"))!!
        val draft = editDraft(repository.currentInstance(saved.id)!!, name = "并发改名")
        listOf(
            async(Dispatchers.Default) { repository.setInstanceEnabled(saved.id, false) },
            async(Dispatchers.Default) { repository.saveInstance(draft) },
        ).awaitAll()

        val restored = readyRepository()
        val instance = restored.currentInstance(saved.id)!!
        // 无论提交顺序如何：草稿内容与停用状态都不得丢失
        assertEquals("并发改名", instance.name)
        assertFalse(instance.isEnabled)
        assertTrue(instance.conversationConnection!!.models.isNotEmpty())
    }

    @Test
    fun candidatesMergeTemplateCatalogWithSelectedModels() = runBlocking {
        val repository = readyRepository()
        val saved = repository.saveNewInstance(openAiDraft(sharedApiKey = "fake-key"))!!
        val candidates = repository.modelCandidates(
            saved.id, saved.conversationConnection!!.id,
        ).map { it.id }
        // 对话目录内置为空、已选模型进入候选；模板能力目录（识别等）按连接合并
        assertTrue(candidates.contains("gpt-4.1"))
        val ttsCandidates = repository.modelCandidates(
            saved.id, saved.connection(ServiceCapability.Synthesis)!!.id,
        ).map { it.id }
        assertTrue("gpt-4o-mini-tts" in ttsCandidates)
    }

    @Test
    fun realtimeDefaultSelectionAndVoiceSurviveRestart() = runBlocking {
        val repository = readyRepository()
        val instance = repository.saveNewInstance(
            openAiDraft(sharedApiKey = "fake-key").let { draft ->
                draft.copy(
                    connections = draft.connections.map { connection ->
                        if (connection.capability == ServiceCapability.Realtime) {
                            connection.copy(models = listOf(Model("gpt-realtime-2.1", "gpt-realtime-2.1")))
                        } else {
                            connection
                        }
                    },
                )
            },
        )!!
        val selection = ModelSelection(
            instance.id, instance.connection(ServiceCapability.Realtime)!!.id, "gpt-realtime-2.1",
        )
        val override = RealtimeVoiceOverride(selection, "cloned_voice_id")
        repository.selectRealtimeModel(selection)
        repository.selectRealtimeVoice(override)
        val restored = readyRepository()
        assertEquals(selection, restored.currentRealtimeSelection())
        assertEquals(override, restored.currentRealtimeVoice())
    }

    @Test
    fun corruptConfigurationFallsBackToEmptyInstances() = runBlocking {
        readyRepository() // 先写入一份合法配置，再污染密文
        preferences.edit().putString("encrypted_configuration", "cipher:not-json").commit()
        val restored = readyRepository()
        assertTrue(restored.currentInstances().isEmpty())
    }

    @Test
    fun templatesAreSeededForTheBuiltInVendors() = runBlocking {
        val repository = readyRepository()
        val templateIds = repository.observeTemplates().first().map { it.id }
        assertTrue("openai" in templateIds)
        assertTrue("qwen" in templateIds)
        // 重复初始化幂等
        val again = readyRepository()
        assertEquals(
            templateIds.sorted(),
            again.observeTemplates().first().map { it.id }.sorted(),
        )
    }

    // ---- fixtures ----

    private suspend fun readyRepository(): ModelServiceConfigurationStore =
        ModelServiceConfigurationStore(context, database).also {
            withTimeout(10_000) { it.awaitReady() }
        }

    private fun openAiDraft(
        name: String = "OpenAI 个人",
        sharedApiKey: String = "",
    ): ModelServiceDraft {
        val template = ModelServiceTemplates.fromId("openai")!!
        return ModelServiceDraft(
            instanceId = null,
            templateId = "openai",
            name = name,
            isOfficialToolsEnabled = true,
            sharedApiKey = sharedApiKey,
            connections = template.connections.map { connection ->
                if (connection.capability == ServiceCapability.Conversation) {
                    connection.toConnection().copy(models = listOf(Model("gpt-4.1", "gpt-4.1")))
                } else {
                    connection.toConnection()
                }
            },
        )
    }

    private fun minimaxDraft(sharedApiKey: String): ModelServiceDraft {
        val template = ModelServiceTemplates.fromId("minimax")!!
        return ModelServiceDraft(
            instanceId = null,
            templateId = "minimax",
            name = "MiniMax",
            isOfficialToolsEnabled = true,
            sharedApiKey = sharedApiKey,
            connections = template.connections.map { it.toConnection() },
        )
    }

    private fun customDraft(
        name: String,
        protocol: String,
        endpoint: String,
        apiKey: String,
        models: List<Model>,
    ) = ModelServiceDraft(
        instanceId = null,
        templateId = null,
        name = name,
        isOfficialToolsEnabled = false,
        sharedApiKey = apiKey,
        connections = listOf(
            ServiceConnection(
                id = "conn-conversation-custom",
                capability = ServiceCapability.Conversation,
                protocol = apiProtocolByName(protocol)!!,
                endpoint = endpoint,
                credential = CredentialSource.Shared,
                models = models,
            ),
        ),
    )

    /** 已落库实例的编辑草稿：保留连接 ID，仅替换可编辑字段。 */
    private fun editDraft(instance: ModelServiceInstance, name: String) = ModelServiceDraft(
        instanceId = instance.id,
        templateId = instance.templateId,
        name = name,
        isOfficialToolsEnabled = instance.isOfficialToolsEnabled,
        sharedApiKey = instance.sharedApiKey,
        connections = instance.connections,
    )

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
