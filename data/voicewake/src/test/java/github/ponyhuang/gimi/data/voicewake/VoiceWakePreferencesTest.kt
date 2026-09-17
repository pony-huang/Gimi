package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWakePreferencesTest {
    @Test
    fun defaultsToDisabledWithChinesePhrase() {
        val fixture = fixture()

        assertFalse(fixture.preferences.enabled.value)
        assertEquals(listOf("吉米"), fixture.preferences.triggerPhrases.value)
    }

    @Test
    fun enabledIntentAndOrderedPhrasesArePersisted() {
        val fixture = fixture()

        fixture.preferences.setEnabled(true)
        fixture.preferences.setTriggerPhrases(listOf("吉米", "Hey Gimi"))

        assertTrue(fixture.preferences.enabled.value)
        assertEquals(listOf("吉米", "Hey Gimi"), fixture.preferences.triggerPhrases.value)
        assertEquals(true, fixture.values["enabled"])
        assertEquals("吉米\u001FHey Gimi", fixture.values["trigger_phrases"])
    }

    private fun fixture(): Fixture {
        val values = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val storage = mockk<SharedPreferences>()
        every { storage.getBoolean(any(), any()) } answers {
            values[firstArg()] as? Boolean ?: secondArg()
        }
        every { storage.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg()
        }
        every { storage.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.commit() } returns true
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns storage
        return Fixture(VoiceWakePreferences(context), values)
    }

    /** 测试偏好及其内存持久化值。 */
    private data class Fixture(
        val preferences: VoiceWakePreferences,
        val values: MutableMap<String, Any?>,
    )
}
