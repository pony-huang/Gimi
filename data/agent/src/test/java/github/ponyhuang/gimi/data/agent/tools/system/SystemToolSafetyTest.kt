package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemToolSafetyTest {
    @Test
    fun `intent launch reports start activity failure`() {
        val result = launchIntentSafely(
            summary = "Open target",
            canResolve = { true },
            launch = { error("launch rejected") },
        )

        assertEquals(false, result["success"])
        assertTrue(result["error"].toString().contains("launch rejected"))
    }

    @Test
    fun `setting write succeeds only when every write succeeds`() {
        assertTrue(allSettingsWritten(true, true))
        assertFalse(allSettingsWritten(true, false))
    }

    @Test
    fun `content media intent grants temporary read permission`() {
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, mediaIntentFlags("content"))
        assertEquals(0, mediaIntentFlags("https"))
    }

    @Test
    fun `media search escapes sql like wildcards`() {
        assertEquals("""50\%\_off\\today""", escapeLikePattern("""50%_off\today"""))
    }
}
