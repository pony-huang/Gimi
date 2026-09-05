package github.ponyhuang.gimi.plugin.spotify

import android.content.Context
import android.content.SharedPreferences
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPluginTest {

    @Test
    fun spotifyAuthorizationCompletesWithoutHostCallbackRequest() = runTest {
        val execution = SpotifyPlugin().runConfigAction(SpotifyPlugin.ACTION_LOGIN)
            as PluginConfigActionExecution.Completed

        assertFalse(execution.result.success)
    }

    @Test
    fun pluginPublishesToolsThroughToolsetAndAppendsUsageInstructions() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } returns mockk<SharedPreferences>(relaxed = true)
        val plugin = SpotifyPlugin().apply { onAttach(context) }

        assertTrue(plugin.tools().isEmpty())
        assertEquals(29, plugin.toolCount)
        val toolset = plugin.toolSets().single()
        assertEquals(29, toolset.getTools(null).size)
        assertEquals("spotify_login", toolset.getTools(null).first().name)

        val processed = toolset.processLlmRequest(mockk<ToolContext>(), requestWith("Base instruction"))
        val instructions = processed.systemText()

        assertTrue(instructions.contains("Base instruction"))
        assertTrue(instructions.contains("<spotify>"))
        assertTrue(instructions.contains("spotify_get_top_tracks"))
        assertTrue(instructions.contains("spotify_get_my_playlists"))
        assertTrue(instructions.contains("Spotify Premium"))
    }

    private fun requestWith(instruction: String): LlmRequest = LlmRequest(
        config = GenerateContentConfig(
            systemInstruction = Content(parts = listOf(Part(text = instruction))),
        ),
    )

    private fun LlmRequest.systemText(): String = config.systemInstruction
        ?.parts
        .orEmpty()
        .mapNotNull(Part::text)
        .joinToString("\n")
}
