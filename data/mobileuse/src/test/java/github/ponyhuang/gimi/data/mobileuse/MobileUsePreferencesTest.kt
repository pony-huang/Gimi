package github.ponyhuang.gimi.data.mobileuse

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileUsePreferencesTest {
    private val values = mutableMapOf<String, Boolean>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val preferences = mockk<SharedPreferences> {
        every { getBoolean(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { edit() } returns editor
    }
    private val context = mockk<Context> {
        every { getSharedPreferences(any(), any()) } returns preferences
    }

    init {
        every { editor.putBoolean(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
    }

    @Test
    fun smallWindowDefaultsToOffAndRemembersChoiceSeparatelyFromFeatureSwitch() {
        val settings = MobileUsePreferences(context)
        assertFalse(settings.smallWindow.value)
        settings.setSmallWindow(true)
        assertTrue(MobileUsePreferences(context).smallWindow.value)
        assertFalse(settings.enabled.value)
    }

    @Test
    fun freshInstallIsOffAndBothExplicitChoicesSurviveRecreation() {
        val settings = MobileUsePreferences(context)
        assertFalse(settings.enabled.value)
        settings.setEnabled(true)
        assertTrue(settings.enabled.value)
        assertTrue(MobileUsePreferences(context).enabled.value)
        settings.setEnabled(false)
        assertFalse(settings.enabled.value)
        assertFalse(MobileUsePreferences(context).enabled.value)
    }

    @Test
    fun corruptValueDefaultsToOffAndCanBeReplaced() {
        every { preferences.getBoolean(any(), any()) } throws ClassCastException()
        val settings = MobileUsePreferences(context)
        assertFalse(settings.enabled.value)
        settings.setEnabled(true)
        assertTrue(settings.enabled.value)
    }
}
