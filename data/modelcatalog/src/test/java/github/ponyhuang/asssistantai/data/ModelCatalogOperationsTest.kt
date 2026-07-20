package github.ponyhuang.asssistantai.data

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
}
