package github.ponyhuang.gimi.data.conversation

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolApprovalPreferencesTest {
    @Test
    fun defaultsToEmptyWhitelistAndFullAccessOff() {
        val repository = repository(FakePreferences())

        assertTrue(repository.alwaysAllowedToolNames.value.isEmpty())
        assertFalse(repository.fullAccess.value)
        assertFalse(repository.isAutoApproved("any_tool"))
    }

    @Test
    fun alwaysAllowPersistsAcrossRepositoryRecreation() {
        val preferences = FakePreferences()
        val repository = repository(preferences)

        repository.setAlwaysAllowed("brightness_set")
        assertTrue(repository.isAutoApproved("brightness_set"))
        assertFalse(repository.isAutoApproved("camera_open"))

        val restored = repository(preferences)
        assertEquals(setOf("brightness_set"), restored.alwaysAllowedToolNames.value)
        assertTrue(restored.isAutoApproved("brightness_set"))
    }

    @Test
    fun removeAlwaysAllowedDropsToolFromWhitelist() {
        val preferences = FakePreferences()
        val repository = repository(preferences)
        repository.setAlwaysAllowed("brightness_set")
        repository.setAlwaysAllowed("camera_open")

        repository.removeAlwaysAllowed("brightness_set")

        assertEquals(setOf("camera_open"), repository.alwaysAllowedToolNames.value)
        assertFalse(repository.isAutoApproved("brightness_set"))
        assertTrue(repository.isAutoApproved("camera_open"))
    }

    @Test
    fun fullAccessPersistsAndAutoApprovesAnyTool() {
        val preferences = FakePreferences()
        val repository = repository(preferences)

        repository.setFullAccess(true)
        assertTrue(repository.isAutoApproved("any_tool"))

        val restored = repository(preferences)
        assertTrue(restored.fullAccess.value)
        assertTrue(restored.isAutoApproved("any_tool"))

        restored.setFullAccess(false)
        assertFalse(restored.isAutoApproved("any_tool"))
    }

    @Test
    fun blankToolNameIsIgnored() {
        val repository = repository(FakePreferences())

        repository.setAlwaysAllowed("")

        assertTrue(repository.alwaysAllowedToolNames.value.isEmpty())
    }

    private fun repository(preferences: FakePreferences): ToolApprovalPreferences {
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences.delegate
        }
        return ToolApprovalPreferences(context)
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
                (values[firstArg()] as? Set<String>)?.toMutableSet() ?: secondArg<Set<String>?>()
            }
            every { delegate.edit() } returns editor
            every { editor.putBoolean(any(), any()) } answers {
                values[firstArg()] = secondArg<Boolean>()
                editor
            }
            every { editor.putStringSet(any(), any()) } answers {
                values[firstArg()] = secondArg<Set<String>?>()?.toSet()
                editor
            }
            every { editor.apply() } returns Unit
        }
    }
}
