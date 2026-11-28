package github.ponyhuang.gimi.data.agent.a2ui

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.data.agent.conversation.A2uiNeteaseFavorite
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class A2uiNeteaseFavoriteTest {
    private val song = "1234567890abcdef1234567890abcdef"
    private val tool = mockk<BaseTool> {
        every { name } returns "netease_song_like"
    }
    private val toolset = mockk<Toolset> {
        coEvery { getTools(null) } returns listOf(tool)
    }
    private val plugin = mockk<AgentPlugin> {
        every { pluginId } returns "netease"
        every { toolSets() } returns listOf(toolset)
    }
    private val provider = object : PluginRuntimeProvider<AgentPlugin> {
        override val runtime = MutableStateFlow(PluginRuntimeSnapshot(0L, listOf(plugin)))
    }
    private val favorite = A2uiNeteaseFavorite(provider)

    @Test
    fun sendsTheRequestedStateAndOnlyReportsAConfirmedResult() = runTest {
        coEvery { tool.run(any(), mapOf("song_id" to song, "like" to true)) } returns mapOf("data" to null)
        coEvery { tool.run(any(), mapOf("song_id" to song, "like" to false)) } returns mapOf("error" to "denied")

        assertTrue(favorite.setLiked("chat", song, true))
        assertFalse(favorite.setLiked("chat", song, false))
        coVerify(exactly = 2) { tool.run(any(), any()) }
    }

    @Test
    fun rejectsInvalidSongIdsAndDisabledPluginsBeforeCallingTheTool() = runTest {
        assertFalse(favorite.setLiked("chat", "1234", true))
        provider.runtime.value = PluginRuntimeSnapshot(1L, emptyList())
        assertFalse(favorite.setLiked("chat", song, true))
        coVerify(exactly = 0) { tool.run(any(), any()) }
    }
}
