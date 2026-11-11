package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.MobileUseController
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import github.ponyhuang.gimi.domain.mobileuse.MobileShellResult
import github.ponyhuang.gimi.domain.mobileuse.MobileObservation
import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileUseToolsTest {
    private val repository = mockk<MobileUseController>()
    private val tools = MobileUseTools(repository).all().associateBy { it.name }

    @Test
    fun screenshotIsOnlyAttachedToImmediateNextRequest() = runTest {
        val jpeg = byteArrayOf(1, 2, 3, 4)
        coEvery { repository.takeScreenshot("turn-1") } returns
            MobileUseResult("ready", "screenshot", 290, jpeg, 1080, 2400)
        val tool = tools.getValue("mobile_take_screenshot")
        val payload = tool.execute(context("turn-1"), mapOf("intent" to "Inspect the screen")) as Map<*, *>
        assertEquals(1080, payload["width"])
        assertEquals(2400, payload["height"])
        val request = LlmRequest(contents = listOf(Content(role = Role.USER, parts = listOf(
            Part(functionResponse = FunctionResponse(name = tool.name, id = "call-1",
                response = payload.entries.associate { it.key.toString() to it.value })),
        ))))
        val enriched = tool.processLlmRequest(mockk(), request)
        assertArrayEquals(jpeg, enriched.contents.last().parts.last().inlineData?.data)
        assertEquals(1, tool.processLlmRequest(mockk(), request).contents.size)
    }

    @Test
    fun missingTaskIdentityDoesNotOperateScreen() = runTest {
        val payload = tools.getValue("mobile_take_screenshot").execute(
            mockk { every { context } returns mockk(relaxed = true) }, mapOf("intent" to "Inspect"),
        ) as Map<*, *>
        assertEquals("unavailable", payload["status"])
        assertNull(payload["imageToken"])
        confirmVerified(repository)
    }

    @Test
    fun deliveredClickAndObservationTimeoutAreReportedIndependentlyWithoutRetry() = runTest {
        coEvery { repository.click("turn-1", 500, 600) } returns MobileUseResult(
            "clicked", "sent", actionStatus = "delivered",
            observation = MobileObservation("screen-2", "timeout", "no_post_action_frame", 800, 1, "not_captured"),
        )
        val payload = execute("mobile_click", mapOf("x" to 500, "y" to 600))
        assertEquals("delivered", payload["actionStatus"])
        assertEquals("timeout", payload["observationStatus"])
        assertFalse(payload.containsKey("observationId"))
        coVerify(exactly = 1) { repository.click("turn-1", 500, 600) }
        confirmVerified(repository)
    }

    @Test
    fun screenshotDoesNotExposeInternalElementsOrDisplayIdentity() = runTest {
        coEvery { repository.takeScreenshot("turn-1") } returns MobileUseResult(
            "observed", "quiet", displayId = 290, width = 1080, height = 2400,
            observation = MobileObservation("screen-1", "settled", "quiet", 100, 7, "available", listOf(
                MobileElement("a1", "accessibility", MobileBounds(10, 20, 30, 40), text = "Confirm"),
            )),
        )
        val payload = execute("mobile_take_screenshot")
        assertFalse(payload.containsKey("nodes"))
        assertFalse(payload.containsKey("displayId"))
        assertFalse(payload.containsKey("observationId"))
        assertEquals("normalized_0_999", payload["coordinateSystem"])
    }

    @Test
    fun invalidCoordinatesAndMissingIntentDoNotSendActions() = runTest {
        for (arguments in listOf(
            mapOf("y" to 20), mapOf("x" to 10.5, "y" to 20),
            mapOf("x" to Double.NaN, "y" to 20), mapOf("x" to 1000, "y" to 20),
            mapOf("x" to 0, "y" to -1),
        )) assertEquals("invalid_argument", execute("mobile_click", arguments)["status"])
        assertEquals("invalid_argument", (tools.getValue("mobile_click").execute(
            context("turn-1"), mapOf("x" to 1, "y" to 1),
        ) as Map<*, *>)["status"])
        confirmVerified(repository)
    }

    @Test
    fun staleObservationIsReturnedWithoutAutomaticActionRetry() = runTest {
        coEvery { repository.click("turn-1", 0, 999) } returns
            MobileUseResult("stale_observation", "observe again", actionStatus = "not_sent")
        assertEquals("stale_observation", execute("mobile_click", mapOf("x" to 0, "y" to 999))["status"])
        coVerify(exactly = 1) { repository.click("turn-1", 0, 999) }
        confirmVerified(repository)
    }

    @Test
    fun optionalArgumentsAreMappedAndMalformedValuesAreRejected() = runTest {
        coEvery { repository.type("turn-1", any(), any()) } returns MobileUseResult("text_typed", "done")
        execute("mobile_type", mapOf("text" to "hello"))
        execute("mobile_type", mapOf("text" to "world", "press_enter" to true))
        assertEquals("invalid_argument", execute("mobile_type", mapOf("text" to "bad", "press_enter" to "true"))["status"])
        coVerify(exactly = 1) { repository.type("turn-1", "hello", false) }
        coVerify(exactly = 1) { repository.type("turn-1", "world", true) }
        coEvery { repository.dragAndDrop("turn-1", 0, 999, 999, 0, 0) } returns MobileUseResult("dragged", "done")
        execute("mobile_drag_and_drop", mapOf("start_x" to 0, "start_y" to 999, "end_x" to 999, "end_y" to 0))
        assertEquals("invalid_argument", execute("mobile_drag_and_drop", mapOf(
            "start_x" to 0, "start_y" to 999, "end_x" to 999, "end_y" to 0, "hold_ms" to -1,
        ))["status"])
        coVerify(exactly = 1) { repository.dragAndDrop("turn-1", 0, 999, 999, 0, 0) }
        assertEquals("invalid_argument", execute("mobile_wait", mapOf("seconds" to 1.5))["status"])
        assertEquals("invalid_argument", execute("mobile_press_key", mapOf("key" to "ENTER; HOME"))["status"])
        confirmVerified(repository)
    }

    @Test
    fun shellTimeoutAndPartialOutputAreReturnedWithoutRetry() = runTest {
        coEvery { repository.runShell("turn-1", "some-command") } returns MobileUseResult(
            "shell_timeout", "Inspect before retrying", actionStatus = "delivered",
            shellResult = MobileShellResult(null, "partial result", timedOut = true, outputTruncated = true),
        )
        val payload = execute("mobile_run_shell", mapOf("command" to "some-command"))
        assertEquals("shell_timeout", payload["status"])
        assertEquals("partial result", payload["output"])
        assertEquals(true, payload["timedOut"])
        assertEquals(true, payload["outputTruncated"])
        assertFalse(payload.containsKey("exitCode"))
        coVerify(exactly = 1) { repository.runShell("turn-1", "some-command") }
        confirmVerified(repository)
    }

    @Test
    fun malformedShellArgumentsNeverReachThePrivilegedBackend() = runTest {
        for (args in listOf(emptyMap(), mapOf("command" to 1), mapOf("command" to " "),
            mapOf("command" to "x".repeat(4_001)), mapOf("command" to "echo\u0000bad"))) {
            assertEquals("invalid_argument", execute("mobile_run_shell", args)["status"])
        }
        confirmVerified(repository)
    }

    private suspend fun execute(name: String, args: Map<String, Any?> = emptyMap()): Map<*, *> =
        tools.getValue(name).execute(context("turn-1"), mapOf("intent" to "Perform the requested action") + args) as Map<*, *>

    private fun context(owner: String): ToolContext {
        val readonly = mockk<ReadonlyContext>()
        every { readonly.runConfig } returns RunConfig(
            customMetadata = ToolRunMetadata.of(
                ModelRuntimeMetadata("service", ApiProtocol.OpenaiChatcompletions, "vision", "https://example.com", true),
                null, true, owner,
            ),
        )
        return mockk { every { context } returns readonly }
    }
}
