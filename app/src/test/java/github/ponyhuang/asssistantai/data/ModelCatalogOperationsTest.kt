package github.ponyhuang.asssistantai.data

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
}
