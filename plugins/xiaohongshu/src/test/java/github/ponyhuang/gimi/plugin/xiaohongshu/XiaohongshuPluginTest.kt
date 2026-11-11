package github.ponyhuang.gimi.plugin.xiaohongshu

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.Part
import github.ponyhuang.gimi.pluginapi.PluginActionCallback
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuPluginTest {

    @Test
    fun pluginToolsetAppendsUsageInstructions() = runTest {
        val plugin = XiaohongshuPlugin(injectedService = FakeXiaohongshuService())

        assertEquals("xiaohongshu", plugin.pluginId)
        val toolset = plugin.toolSets().single()
        assertTrue(toolset.getTools(null).isNotEmpty())

        val request = LlmRequest(
            config = GenerateContentConfig(
                systemInstruction = Content(parts = listOf(Part(text = "Base instruction"))),
            ),
        )
        val processed = toolset.processLlmRequest(mockk<ToolContext>(), request)
        val instructions = processed.config.systemInstruction
            ?.parts
            .orEmpty()
            .mapNotNull(Part::text)
            .joinToString("\n")

        assertTrue(instructions.contains("Base instruction"))
        assertTrue(instructions.contains("<xiaohongshu>"))
        assertTrue(instructions.contains("feed_id"))
        assertTrue(instructions.contains("xsec_token"))
        assertTrue(instructions.contains("notification"))
    }

    @Test
    fun loginRequestsHostCallbackInteractionWithPluginOwnedParameters() = runTest {
        val execution = XiaohongshuPlugin(injectedService = FakeXiaohongshuService())
            .runConfigAction("login") as PluginConfigActionExecution.AwaitingCallback
        val request = execution.request

        assertEquals("web", request.handlerId)
        assertEquals("https://www.xiaohongshu.com/explore", request.parameters["authorize_url"])
        assertEquals("https://www.xiaohongshu.com", request.parameters["capture_cookies_for_url"])
        assertTrue(request.parameters["completion_script"].orEmpty().contains(".main-container .user"))
        assertEquals("true", request.parameters["desktop_mode"])
    }

    @Test
    fun loginCallbackInterpretsGenericValues() = runTest {
        val result = XiaohongshuPlugin(injectedService = FakeXiaohongshuService())
            .onConfigActionCallback(
                actionId = "login",
                callback = PluginActionCallback(mapOf("cookies" to "session=abc")),
            )

        assertTrue(result.success)
        assertEquals("小红书登录成功", result.message)
    }
}

private class FakeXiaohongshuService : XiaohongshuService
