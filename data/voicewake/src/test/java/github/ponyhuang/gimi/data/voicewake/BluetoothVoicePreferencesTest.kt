package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.speech.model.WakeModelCatalog
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BluetoothVoicePreferencesTest {
    @Test
    fun wakeWordsArePersistedIndependentlyByModel() {
        val values = mutableMapOf<String, Any?>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val storage = mockk<SharedPreferences>()
        every { storage.getString(any(), any()) } answers {
            values[firstArg()] as? String ?: secondArg()
        }
        every { storage.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg<String>()
            editor
        }
        every { editor.commit() } returns true
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns storage
        val preferences = BluetoothVoicePreferences(context)

        preferences.setWakeWord(WakeModelCatalog.Chinese.id, "小助手")
        preferences.setActiveModel(WakeModelCatalog.English.id)
        preferences.setWakeWord(WakeModelCatalog.English.id, "Hey Assistant")
        preferences.setActiveModel(WakeModelCatalog.Chinese.id)

        assertEquals("小助手", preferences.wakeWord.value)
        preferences.setActiveModel(WakeModelCatalog.English.id)
        assertEquals("Hey Assistant", preferences.wakeWord.value)
    }

    @Test
    fun missingSavedWakeWordFallsBackToModelDefault() {
        val storage = mockk<SharedPreferences>(relaxed = true)
        every { storage.getString(any(), any()) } answers { secondArg() }
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns storage

        val preferences = BluetoothVoicePreferences(context)

        assertEquals(WakeModelCatalog.Chinese.defaultWakeWord, preferences.wakeWord.value)
    }

    @Test
    fun failedCommitDoesNotPublishNewEffectiveWakeWord() {
        val storage = mockk<SharedPreferences>(relaxed = true)
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { storage.getString(any(), any()) } answers { secondArg() }
        every { storage.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.commit() } returns false
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns storage
        val preferences = BluetoothVoicePreferences(context)

        assertThrows(IllegalStateException::class.java) {
            preferences.setWakeWord(WakeModelCatalog.Chinese.id, "小助手")
        }
        assertEquals(WakeModelCatalog.Chinese.defaultWakeWord, preferences.wakeWord.value)
    }
}
