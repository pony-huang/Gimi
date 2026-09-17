package github.ponyhuang.gimi.domain.speech.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseMatcherTest {
    @Test
    fun `matches a leading Chinese wake phrase and returns the command`() {
        assertEquals(
            WakePhraseMatch(trigger = "吉米", command = "打开地图"),
            WakePhraseMatcher.match("吉米，打开地图", listOf("吉米")),
        )
    }

    @Test
    fun `allows a spoken filler before the wake phrase`() {
        assertEquals(
            WakePhraseMatch(trigger = "Gimi", command = "show status"),
            WakePhraseMatcher.match("Hey Gimi, show status", listOf("Gimi")),
        )
    }

    @Test
    fun `does not trigger without a command or from a word suffix`() {
        assertNull(WakePhraseMatcher.match("吉米", listOf("吉米")))
        assertNull(WakePhraseMatcher.match("agimi show status", listOf("gimi")))
    }
}
