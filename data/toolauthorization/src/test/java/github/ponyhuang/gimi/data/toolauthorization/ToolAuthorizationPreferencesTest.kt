package github.ponyhuang.gimi.data.toolauthorization

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.gimi.domain.toolauthorization.repository.LocalToolDefinitionSource
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
        assertFalse(repository.isCustomizationEnabled.value)
    }

    @Test
    fun itemAndBulkChangesPersistAcrossRepositoryRecreation() {
        val preferences = FakePreferences()
        val repository = repository(preferences, "one", "two")

        repository.setCustomizationEnabled(true)
        repository.setEnabled("one", false)
        assertEquals(setOf("two"), repository.enabledToolIds())
        assertEquals(2L, repository.revision.value)

        val restored = repository(preferences, "one", "two")
        assertTrue(restored.isCustomizationEnabled.value)
        assertEquals(setOf("two"), restored.enabledToolIds())

        restored.setAllEnabled(false)
        assertTrue(restored.enabledToolIds().isEmpty())
        restored.setAllEnabled(true)
        assertEquals(setOf("one", "two"), restored.enabledToolIds())
    }

    @Test
    fun toolsAddedAfterInitializationDefaultToDisabledWhenCustomizationEnabled() {
        val preferences = FakePreferences()
        val repository = repository(preferences, "one", "two")
        repository.setCustomizationEnabled(true)

        val upgraded = repository(preferences, "one", "two", "new-tool")

        assertEquals(setOf("one", "two"), upgraded.enabledToolIds())
        assertFalse(upgraded.tools.value.single { it.id == "new-tool" }.isEnabled)
    }

    @Test
    fun customizationDisabledDefaultsAllToolsEnabledAndPreservesStoredChoices() {
        val preferences = FakePreferences()
        val repository = repository(preferences, "one", "two")
        repository.setCustomizationEnabled(true)
        repository.setEnabled("one", false)
        assertEquals(setOf("two"), repository.enabledToolIds())

        repository.setCustomizationEnabled(false)
        assertEquals(setOf("one", "two"), repository.enabledToolIds())
        assertTrue(repository.tools.value.all { it.isEnabled })

        repository.setCustomizationEnabled(true)
        assertEquals(setOf("two"), repository.enabledToolIds())
    }

    @Test
    fun togglingCustomizationIncrementsRevision() {
        val repository = repository(FakePreferences(), "one", "two")
        val initialRevision = repository.revision.value

        repository.setCustomizationEnabled(true)
        assertEquals(initialRevision + 1, repository.revision.value)

        repository.setCustomizationEnabled(false)
        assertEquals(initialRevision + 2, repository.revision.value)
    }

    @Test
    fun customizationDisabledDoesNotClearStoredEnabledIds() {
        val preferences = FakePreferences()
        val repository = repository(preferences, "one", "two")
        repository.setCustomizationEnabled(true)
        repository.setEnabled("one", false)

        repository.setCustomizationEnabled(false)
        val restored = repository(preferences, "one", "two")

        assertFalse(restored.isCustomizationEnabled.value)
        assertEquals(setOf("one", "two"), restored.enabledToolIds())

        restored.setCustomizationEnabled(true)
        assertEquals(setOf("two"), restored.enabledToolIds())
    }

    private fun repository(preferences: FakePreferences, vararg ids: String): ToolAuthorizationPreferences {
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences.delegate
        }
        val source = object : LocalToolDefinitionSource {
            override fun definitions(): List<ToolDefinition> = ids.mapIndexed { index, id ->
                val category = LocalToolCategory.entries[index % LocalToolCategory.entries.size]
                ToolDefinition(id = id, name = id, description = "Description for $id", category = category)
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
