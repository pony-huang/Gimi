package github.ponyhuang.gimi.data.modelcatalog

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogOperationsTest {
    @Test
    fun syncRemoteModelsReplacesRemoteAndPreservesUserModels() {
        val groups = listOf(
            StoredModelGroup(
                groupId = "group",
                groupName = "Group",
                models = listOf(
                    StoredModel("stale", "Stale", StoredModelSource.REMOTE),
                    StoredModel("custom", "Custom", StoredModelSource.USER),
                ),
            ),
        )

        val result = syncStoredRemoteModels(
            existingGroups = groups,
            serviceId = "service",
            serviceName = "Service",
            models = listOf(
                LLMModelItem("fresh", "Fresh"),
                LLMModelItem("custom", "Remote duplicate"),
                LLMModelItem("fresh", "Duplicate"),
            ),
        )

        assertEquals(listOf("fresh", "custom"), result.single().models.map { it.modelId })
        assertEquals(StoredModelSource.REMOTE, result.single().models[0].source)
        assertEquals(StoredModelSource.USER, result.single().models[1].source)
    }

    @Test
    fun appendAndRemoveLastModelKeepsProviderGroup() {
        val appended = appendUserModel(
            groups = emptyList(),
            serviceId = "service",
            serviceName = "Service",
            model = LLMModelItem("custom", "Custom"),
        )

        val removed = removeStoredModel(appended, "service-default", "custom")

        assertEquals(1, removed.size)
        assertTrue(removed.single().models.isEmpty())
    }

    @Test
    fun legacyStoredModelDefaultsToNonStt() {
        val stored = Gson().fromJson(
            """{"modelId":"chat","modelName":"Chat","source":"REMOTE"}""",
            StoredModel::class.java,
        )

        assertFalse(stored.isStt)
    }

    @Test
    fun syncRemoteModelsPreservesSpeechCapabilityAndGroup() {
        val groups = listOf(
            StoredModelGroup("chat", "Chat"),
            StoredModelGroup(
                groupId = "speech",
                groupName = "Speech",
                models = listOf(
                    StoredModel("asr", "ASR", StoredModelSource.REMOTE, isStt = true),
                ),
            ),
        )

        val result = syncStoredRemoteModels(
            existingGroups = groups,
            serviceId = "service",
            serviceName = "Service",
            models = listOf(
                LLMModelItem("chat-new", "Chat New"),
                LLMModelItem("asr", "ASR New"),
            ),
        )

        assertEquals(listOf("chat-new"), result[0].models.map { it.modelId })
        assertEquals(listOf("asr"), result[1].models.map { it.modelId })
        assertTrue(result[1].models.single().isStt)
        assertEquals("ASR New", result[1].models.single().modelName)
    }

    @Test
    fun syncRemoteModelsAutomaticallyGroupsStandardAndMimoNames() {
        val standard = syncStoredRemoteModels(emptyList(), "service", "Service", listOf(
            LLMModelItem("deepseek-v3-chat", "deepseek-v3-chat"),
            LLMModelItem("deepseek-v4-chat", "deepseek-v4-chat"),
        ))
        assertEquals(listOf("deepseek-v3", "deepseek-v4"), standard.map { it.groupId })
        val mimo = syncStoredRemoteModels(emptyList(), LLMModelType.Mimo.serviceId, "MIMO", listOf(
            LLMModelItem("mimo-v2.5-pro-ultraspeed", "mimo-v2.5-pro-ultraspeed"),
            LLMModelItem("mimo-v2.5-asr", "mimo-v2.5-asr"),
            LLMModelItem("mimo-v2.5-tts", "mimo-v2.5-tts"),
        ))
        assertEquals(listOf("mimo-v2.5-pro", "mimo-v2.5-asr", "mimo-v2.5-tts"), mimo.map { it.groupId })
        assertTrue(mimo[1].models.single().isStt)
        assertTrue(mimo[2].models.single().isTts)
    }

    @Test
    fun syncRemoteModelsGroupsTwoSegmentModelIdsByFamily() {
        val result = syncStoredRemoteModels(
            existingGroups = emptyList(),
            serviceId = LLMModelType.MiniMax.serviceId,
            serviceName = "MiniMax",
            models = listOf(
                LLMModelItem("MiniMax-M3", "MiniMax-M3"),
                LLMModelItem("MiniMax-M2.7", "MiniMax-M2.7"),
                LLMModelItem("MiniMax-M2", "MiniMax-M2"),
            ),
        )

        assertEquals(listOf("MiniMax"), result.map { it.groupId })
        assertEquals(
            listOf("MiniMax-M3", "MiniMax-M2.7", "MiniMax-M2"),
            result.single().models.map { it.modelId },
        )
    }

    @Test
    fun syncRemoteModelsPreservesExistingSpeechModelsMissingFromChatCatalog() {
        val result = syncStoredRemoteModels(
            existingGroups = listOf(
                StoredModelGroup(
                    groupId = "minimax-tts",
                    groupName = "MiniMax 语音合成",
                    models = listOf(
                        StoredModel(
                            "speech-2.8-hd",
                            "speech-2.8-hd",
                            StoredModelSource.REMOTE,
                            isTts = true,
                        ),
                    ),
                ),
            ),
            serviceId = LLMModelType.MiniMax.serviceId,
            serviceName = "MiniMax",
            models = listOf(LLMModelItem("MiniMax-M2.7", "MiniMax-M2.7")),
        )

        val speechGroup = result.first { it.groupId == "minimax-tts" }
        assertEquals(listOf("speech-2.8-hd"), speechGroup.models.map { it.modelId })
        assertTrue(speechGroup.models.single().isTts)
        assertEquals(
            listOf("MiniMax-M2.7"),
            result.first { it.groupId == "MiniMax" }.models.map { it.modelId },
        )
    }

    @Test
    fun syncRemoteModelsKeepsUserModelsInTheirExistingGroups() {
        val groups = listOf(StoredModelGroup("custom", "Custom", models = listOf(
            StoredModel("local-model", "Local", StoredModelSource.USER),
        )))
        val result = syncStoredRemoteModels(groups, "service", "Service", listOf(
            LLMModelItem("vendor-v1-chat", "vendor-v1-chat"),
        ))
        assertEquals(listOf("custom", "vendor-v1"), result.map { it.groupId })
        assertEquals(listOf("local-model"), result[0].models.map { it.modelId })
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
                    StoredModel("asr", "ASR", StoredModelSource.REMOTE, isStt = true),
                ),
            ),
        )

        val upgraded = mergeDefaultModelMetadata(existing, defaults)

        assertEquals("custom", upgraded[0].models.single().modelId)
        assertTrue(upgraded[1].models.single().isStt)
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
                    StoredModel("speech-2.8-hd", "speech-2.8-hd", StoredModelSource.REMOTE, isTts = true),
                    StoredModel("speech-2.8-turbo", "speech-2.8-turbo", StoredModelSource.REMOTE, isTts = true),
                    StoredModel("speech-2.6-hd", "speech-2.6-hd", StoredModelSource.REMOTE, isTts = true),
                    StoredModel("speech-2.6-turbo", "speech-2.6-turbo", StoredModelSource.REMOTE, isTts = true),
                    StoredModel("speech-02-hd", "speech-02-hd", StoredModelSource.REMOTE, isTts = true),
                    StoredModel("speech-02-turbo", "speech-02-turbo", StoredModelSource.REMOTE, isTts = true),
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

        // 2. New TTS group is appended at the end with all six models flagged isTts.
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
        assertTrue(tts.models.all { it.isTts })

        // 3. Re-running the merge with the same defaults is a no-op (idempotency assertion
        // covering upgradeDefaultModelMetadata's "only write when changed" guarantee).
        assertEquals(upgraded, mergeDefaultModelMetadata(upgraded, defaults))
    }
}
