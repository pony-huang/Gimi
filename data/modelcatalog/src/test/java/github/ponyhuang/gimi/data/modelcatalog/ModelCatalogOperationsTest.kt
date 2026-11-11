package github.ponyhuang.gimi.data.modelcatalog

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置目录元数据合并的行为。
 *
 * 应用升级时把新增的内置模型组并入用户已持久化的目录：用户自建模型必须原样保留，
 * 内置语音模型的能力标记必须被补齐。
 */
class ModelCatalogOperationsTest {

    @Test
    fun legacyStoredModelDefaultsToChatCapability() {
        // 旧持久化 blob（缺能力字段）应被兼容 Json 正常读回，并按可对话模型处理。
        val stored = modelCatalogJson.decodeFromString<StoredModel>(
            """{"modelId":"chat","modelName":"Chat","source":"REMOTE"}""",
        )

        assertTrue(ModelCapability.LLM in stored.capabilities)
    }

    @Test
    fun metadataUpgradeAddsDefaultSpeechGroupWithoutReplacingUserModels() {
        val existing = listOf(
            StoredModelGroup(
                groupId = "chat",
                groupName = "Chat",
                models = listOf(StoredModel("custom", "Custom", StoredModelSource.USER)),
            ),
        )
        val defaults = listOf(
            StoredModelGroup(
                groupId = "speech",
                groupName = "Speech",
                models = listOf(
                    StoredModel(
                        "asr", "ASR", StoredModelSource.REMOTE,
                        capabilities = setOf(ModelCapability.STT),
                    ),
                ),
            ),
        )

        val upgraded = mergeDefaultModelMetadata(existing, defaults)

        assertEquals("custom", upgraded[0].models.single().modelId)
        assertTrue(ModelCapability.STT in upgraded[1].models.single().capabilities)
        assertEquals(upgraded, mergeDefaultModelMetadata(upgraded, defaults))
    }

    /**
     * Simulates the app-update scenario: a user already has the `minimax` provider
     * persisted with no model groups, then an app update extends the default with a
     * `minimax-tts` group containing six TTS models. The upgrade must add the new
     * group without dropping the user's existing chat entries.
     */
    @Test
    fun metadataUpgradeAppendsNewTtsGroupWithoutDroppingExistingEntries() {
        val existing = listOf(
            StoredModelGroup(
                groupId = "minimax-chat",
                groupName = "Minimax 对话",
                models = listOf(
                    StoredModel("minimax-chat-1", "Chat 1", StoredModelSource.REMOTE),
                    StoredModel(
                        "minimax-custom",
                        "Custom Minimax",
                        StoredModelSource.USER,
                    ),
                ),
            ),
        )
        val defaults = listOf(
            StoredModelGroup(
                groupId = "minimax-tts",
                groupName = "Minimax 语音合成",
                models = listOf(
                    StoredModel("speech-2.8-hd", "speech-2.8-hd", StoredModelSource.REMOTE, capabilities = setOf(ModelCapability.TTS)),
                    StoredModel("speech-2.8-turbo", "speech-2.8-turbo", StoredModelSource.REMOTE, capabilities = setOf(ModelCapability.TTS)),
                    StoredModel("speech-2.6-hd", "speech-2.6-hd", StoredModelSource.REMOTE, capabilities = setOf(ModelCapability.TTS)),
                    StoredModel("speech-2.6-turbo", "speech-2.6-turbo", StoredModelSource.REMOTE, capabilities = setOf(ModelCapability.TTS)),
                    StoredModel("speech-02-hd", "speech-02-hd", StoredModelSource.REMOTE, capabilities = setOf(ModelCapability.TTS)),
                    StoredModel("speech-02-turbo", "speech-02-turbo", StoredModelSource.REMOTE, capabilities = setOf(ModelCapability.TTS)),
                ),
            ),
        )

        val upgraded = mergeDefaultModelMetadata(existing, defaults)

        // 1. Existing chat group is preserved verbatim — no user models lost.
        val chat = upgraded.first { it.groupId == "minimax-chat" }
        assertEquals(
            listOf("minimax-chat-1", "minimax-custom"),
            chat.models.map { it.modelId },
        )

        // 2. New TTS group is appended at the end with all six models carrying the TTS capability.
        val tts = upgraded.first { it.groupId == "minimax-tts" }
        assertEquals(
            listOf(
                "speech-2.8-hd",
                "speech-2.8-turbo",
                "speech-2.6-hd",
                "speech-2.6-turbo",
                "speech-02-hd",
                "speech-02-turbo",
            ),
            tts.models.map { it.modelId },
        )
        assertTrue(tts.models.all { ModelCapability.TTS in it.capabilities })

        // 3. Re-running the merge with the same defaults is a no-op (idempotency assertion
        // covering upgradeDefaultModelMetadata's "only write when changed" guarantee).
        assertEquals(upgraded, mergeDefaultModelMetadata(upgraded, defaults))
    }

    @Test
    fun minimaxDefaultsIncludeOfficialAsrModel() {
        val minimax = SpeechModelConfigs.services.single { it.serviceId == "minimax-speech" }

        val asr = minimax.lLMModelGroups.single { it.groupId == "minimax-stt" }.models.single()
        assertEquals("asr-1.0", asr.modelId)
        assertTrue(ModelCapability.STT in asr.capabilities)
        assertFalse(ModelCapability.TTS in asr.capabilities)
    }

    @Test
    fun minimaxDefaultsIncludeTtsModels() {
        val minimax = SpeechModelConfigs.services.single { it.serviceId == "minimax-speech" }

        val tts = minimax.lLMModelGroups.single { it.groupId == "minimax-tts" }.models
        assertEquals(6, tts.size)
        assertTrue(tts.all { ModelCapability.TTS in it.capabilities })
    }

    @Test
    fun qwenDefaultsIncludeAsrAndTtsModels() {
        val qwen = SpeechModelConfigs.services.single { it.serviceId == "qwen-audio" }

        val asr = qwen.lLMModelGroups.single { it.groupId == "qwen-audio-asr" }.models
        assertEquals(
            listOf(
                "qwen-audio-3.1-asr-flash-message",
                "qwen-audio-3.1-asr-flash-streaming",
                "qwen-audio-3.0-asr-flash-streaming",
            ),
            asr.map { it.modelId },
        )
        assertTrue(asr.all { ModelCapability.STT in it.capabilities })

        val tts = qwen.lLMModelGroups.single { it.groupId == "qwen-audio-tts" }.models
        assertEquals(
            listOf(
                "qwen-audio-3.1-tts-flash",
                "qwen-audio-3.1-tts-plus",
                "qwen-audio-3.0-tts-flash",
                "qwen-audio-3.0-tts-plus",
            ),
            tts.map { it.modelId },
        )
        assertTrue(tts.all { ModelCapability.TTS in it.capabilities })
    }
}
