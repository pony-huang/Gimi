package github.ponyhuang.asssistantai.data.toolauthorization

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.LocalToolDefinitionSource
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAuthorizationPreferencesTest {
    @Test
    fun firstInitializationEnablesCompleteExistingCatalog() {
        val repository = repository(FakePreferences(), "one", "two")

        assertEquals(setOf("one", "two"), repository.enabledToolIds())
    }

    @Test
    fun itemAndBulkChangesPersistAcrossRepositoryRecreation() {
        val preferences = FakePreferences()
        val repository = repository(preferences, "one", "two")

        repository.setEnabled("one", false)
        assertEquals(setOf("two"), repository.enabledToolIds())
        assertEquals(1L, repository.revision.value)

        val restored = repository(preferences, "one", "two")
        assertEquals(setOf("two"), restored.enabledToolIds())

        restored.setAllEnabled(false)
        assertTrue(restored.enabledToolIds().isEmpty())
        restored.setAllEnabled(true)
        assertEquals(setOf("one", "two"), restored.enabledToolIds())
    }

    @Test
    fun toolsAddedAfterInitializationDefaultToDisabled() {
        val preferences = FakePreferences()
        repository(preferences, "one", "two")

        val upgraded = repository(preferences, "one", "two", "new-tool")

        assertEquals(setOf("one", "two"), upgraded.enabledToolIds())
        assertFalse(upgraded.tools.value.single { it.id == "new-tool" }.isEnabled)
    }

    private fun repository(preferences: FakePreferences, vararg ids: String): ToolAuthorizationPreferences {
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences.delegate
        }
        val source = object : LocalToolDefinitionSource {
            override fun definitions(): List<ToolDefinition> = ids.map { id ->
                ToolDefinition(id = id, name = id, description = "Description for $id")
            }
        }
        return ToolAuthorizationPreferences(context, source)
    }

    private class FakePreferences {
        private val values = mutableMapOf<String, Any?>()
        private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val delegate = mockk<SharedPreferences>()

        init {
            every { delegate.getBoolean(any(), any()) } answers {
                values[firstArg()] as? Boolean ?: secondArg()
            }
            every { delegate.getStringSet(any(), any()) } answers {
                @Suppress("UNCHECKED_CAST")
                (values[firstArg()] as? Set<String>)?.toMutableSet() ?: secondArg<MutableSet<String>?>()
            }
            every { delegate.edit() } returns editor
            every { editor.putBoolean(any(), any()) } answers {
                values[firstArg()] = secondArg<Boolean>()
                editor
            }
            every { editor.putStringSet(any(), any()) } answers {
                values[firstArg()] = secondArg<MutableSet<String>?>()?.toSet()
                editor
            }
            every { editor.apply() } returns Unit
        }
    }
}
