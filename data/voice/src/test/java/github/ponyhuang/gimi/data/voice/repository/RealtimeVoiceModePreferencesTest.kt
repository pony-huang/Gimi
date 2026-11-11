package github.ponyhuang.gimi.data.voice.repository

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeVoiceModePreferencesTest {
    @Test
    fun freshInstallDefaultsToSimpleAndPreservesLastChoice() {
        val values = mutableMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences> {
            every { getString(any(), any()) } answers {
                values[firstArg()] ?: secondArg<String?>()
            }
            every { edit() } returns editor
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences
        }

        val initial = RealtimeVoiceModePreferences(context)
        assertEquals(RealtimeVoiceMode.Simple, initial.lastMode.value)

        initial.setLastMode(RealtimeVoiceMode.RealtimeAssistant)
        assertEquals(
            RealtimeVoiceMode.RealtimeAssistant,
            RealtimeVoiceModePreferences(context).lastMode.value,
        )
    }

    @Test
    fun corruptedModeNameFallsBackToSimple() {
        val values = mutableMapOf<String, String>("last_mode" to "not-a-mode")
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences> {
            every { getString(any(), any()) } answers {
                values[firstArg()] ?: secondArg<String?>()
            }
            every { edit() } returns editor
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        val context = mockk<Context> {
            every { getSharedPreferences(any(), any()) } returns preferences
        }

        assertEquals(RealtimeVoiceMode.Simple, RealtimeVoiceModePreferences(context).lastMode.value)
    }
}
