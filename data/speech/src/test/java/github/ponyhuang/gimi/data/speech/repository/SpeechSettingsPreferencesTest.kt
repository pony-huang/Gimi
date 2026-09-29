package github.ponyhuang.gimi.data.speech.repository

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSettingsPreferencesTest {
    @Test
    fun freshInstallDefaultsToOffAndPreservesExplicitChoice() {
        val values = mutableMapOf<String, Boolean>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences> {
            every { getBoolean(any(), any()) } answers {
                values[firstArg()] ?: secondArg()
            }
            every { edit() } returns editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences
        }

        val initial = SpeechSettingsPreferences(context)
        assertFalse(initial.autoSpeakEnabled.value)

        initial.setAutoSpeakEnabled(true)
        assertTrue(SpeechSettingsPreferences(context).autoSpeakEnabled.value)
    }
}
