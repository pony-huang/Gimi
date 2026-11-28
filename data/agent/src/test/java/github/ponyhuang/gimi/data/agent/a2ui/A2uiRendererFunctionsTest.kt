package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiRendererFunctions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiRendererFunctionsTest {
    @Test
    fun seeksOnlyTheMountedPlayerInTheCallingSession() = runTest {
        val functions = A2uiRendererFunctions()
        val positions = mutableListOf<Long>()
        val detach = functions.registerMusicPlayer("chat-a", "card") {
            positions += it
            true
        }

        assertFalse(functions.seekMusicPlayer("chat-b", "card", 30_000))
        assertFalse(functions.seekMusicPlayer("chat-a", "card", -1))
        assertTrue(functions.seekMusicPlayer("chat-a", "card", 30_000))
        detach()
        assertFalse(functions.seekMusicPlayer("chat-a", "card", 40_000))
        assertEquals(listOf(30_000L), positions)
    }

    @Test
    fun rejectsAnAmbiguousSurfaceUntilOnlyOnePlayerRemains() = runTest {
        val functions = A2uiRendererFunctions()
        functions.registerMusicPlayer("chat", "card") { true }
        val detach = functions.registerMusicPlayer("chat", "card") { error("Wrong player") }

        assertFalse(functions.seekMusicPlayer("chat", "card", 1_000))
        detach()
        assertTrue(functions.seekMusicPlayer("chat", "card", 1_000))
    }
}
