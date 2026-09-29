package github.ponyhuang.gimi.data.conversation

import android.content.Context
import android.content.SharedPreferences
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolAccessPreferencesTest {
    @Test
    fun freshInstallLoadsAllAndPreservesExplicitChoice() {
        val values = mutableMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val preferences = mockk<SharedPreferences> {
            every { getString(any(), any()) } answers {
                values[firstArg()] ?: secondArg()
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

        val initial = ToolAccessPreferences(context)
        assertEquals(ToolAccessMode.ALWAYS_AVAILABLE, initial.defaultToolAccessMode.value)

        initial.setDefaultToolAccessMode(ToolAccessMode.ON_DEMAND)
        assertEquals(
            ToolAccessMode.ON_DEMAND,
            ToolAccessPreferences(context).defaultToolAccessMode.value,
        )
    }
}
