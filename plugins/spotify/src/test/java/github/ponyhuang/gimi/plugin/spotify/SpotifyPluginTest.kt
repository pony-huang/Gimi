package github.ponyhuang.gimi.plugin.spotify

import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpotifyPluginTest {

    @Test
    fun dashboardUsesGenericExternalUrlHandler() = runTest {
        val execution = SpotifyPlugin().runConfigAction(SpotifyPlugin.ACTION_DASHBOARD)
            as PluginConfigActionExecution.AwaitingCallback
        assertEquals("external_url", execution.request.handlerId)
        assertEquals("https://developer.spotify.com/dashboard", execution.request.parameters["url"])
    }

    @Test
    fun spotifyAuthorizationCompletesWithoutHostCallbackRequest() = runTest {
        val execution = SpotifyPlugin().runConfigAction(SpotifyPlugin.ACTION_LOGIN)
            as PluginConfigActionExecution.Completed

        assertFalse(execution.result.success)
    }
}
