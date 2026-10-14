package github.ponyhuang.gimi.data.agent

import github.ponyhuang.gimi.data.agent.plugins.AgentToolRetryPolicy
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证模型修正仅用于无副作用读取，取消与执行类工具不能被重新尝试。 */
class AgentToolRetryPolicyTest {
    @Test
    fun retryGuidanceStopsAfterTwoFailuresAndSuccessResetsBudget() = runTest {
        val plugin = AgentToolRetryPolicy.plugin()
        val context = mockk<ToolContext> { every { invocationId } returns "turn-a" }
        val tool = mockk<BaseTool> { every { name } returns "load_skill" }
        val first = plugin.afterTool(context, tool, emptyMap(), mapOf("error" to "Missing skill"))
        val second = plugin.afterTool(context, tool, emptyMap(), mapOf("error" to "Missing skill"))
        val third = plugin.afterTool(context, tool, emptyMap(), mapOf("error" to "Missing skill"))
        assertEquals(1, first["retry_count"])
        assertEquals(2, second["retry_count"])
        assertTrue((third["reflection_guidance"] as String).contains("Do not attempt"))
        plugin.afterTool(context, tool, emptyMap(), mapOf("content" to "ok"))
        assertEquals(1, plugin.afterTool(context, tool, emptyMap(), mapOf("error" to "Missing skill"))["retry_count"])
    }

    @Test
    fun unsafeErrorsAndBusinessResultsRemainUntouched() = runTest {
        val plugin = AgentToolRetryPolicy.plugin()
        val context = mockk<ToolContext>()
        val tool = mockk<BaseTool> { every { name } returns "sandbox_run_command" }
        val result = mapOf("error" to "Unknown execution status")
        assertEquals(result, plugin.afterTool(context, tool, emptyMap(), result))
        assertTrue(plugin.onToolError(context, tool, emptyMap(), IOException()) is CallbackChoice.Continue)
        val readTool = mockk<BaseTool> { every { name } returns "load_skill" }
        assertTrue(plugin.onToolError(context, readTool, emptyMap(), CancellationException()) is CallbackChoice.Continue)
    }

    @Test
    fun correctableSkillReadFailuresCanBeRetried() {
        assertTrue(AgentToolRetryPolicy.isRetryable("load_skill", IllegalArgumentException("Unknown skill")))
        assertTrue(AgentToolRetryPolicy.isRetryable("load_skill_resource", IOException("Missing resource")))
        assertTrue(AgentToolRetryPolicy.isRetryable("tool_search", "Invalid search query"))
    }

    @Test
    fun cancellationAndSideEffectsAreNeverRetried() {
        assertFalse(AgentToolRetryPolicy.isRetryable("load_skill", CancellationException()))
        assertFalse(AgentToolRetryPolicy.isRetryable("sandbox_run_command", IOException()))
        assertFalse(AgentToolRetryPolicy.isRetryable("run_skill_script", IOException()))
        assertFalse(AgentToolRetryPolicy.isRetryable("Execute", IOException()))
        assertFalse(AgentToolRetryPolicy.isRetryable("send_message", IllegalArgumentException()))
        assertFalse(AgentToolRetryPolicy.isRetryable("adk_request_confirmation", "Rejected"))
    }

    @Test
    fun programmingAndAuthorizationFailuresAreNotRecoverable() {
        assertFalse(AgentToolRetryPolicy.isRetryable("load_skill", IllegalStateException("Bug")))
        assertFalse(AgentToolRetryPolicy.isRetryable("load_skill", SecurityException("Denied")))
    }
}
