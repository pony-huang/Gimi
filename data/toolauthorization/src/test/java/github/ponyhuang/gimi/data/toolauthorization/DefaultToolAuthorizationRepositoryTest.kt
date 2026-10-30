package github.ponyhuang.gimi.data.toolauthorization

import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.gimi.domain.toolauthorization.repository.LocalToolDefinitionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultToolAuthorizationRepositoryTest {
    @Test
    fun recreatedCatalogIncludesNewToolsWithoutStoredSelection() {
        assertEquals(setOf("one"), repository("one").enabledToolIds())
        val updated = repository("one", "new-tool")
        assertEquals(setOf("one", "new-tool"), updated.enabledToolIds())
        assertTrue(updated.tools.value.all { it.isEnabled })
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsAreRejectedBeforeToolsCanBeResolved() {
        repository("one", "one")
    }

    private fun repository(vararg ids: String) = DefaultToolAuthorizationRepository(
        object : LocalToolDefinitionSource {
            override fun definitions() = ids.map { ToolDefinition(it, it, "Tool $it") }
        },
    )
}
