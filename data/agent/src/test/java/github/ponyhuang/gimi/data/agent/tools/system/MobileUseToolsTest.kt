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
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.mobileuse.MobileUseResult
import github.ponyhuang.gimi.domain.mobileuse.MobileObservation
import github.ponyhuang.gimi.domain.mobileuse.MobileBounds
import github.ponyhuang.gimi.domain.mobileuse.MobileElement
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileUseToolsTest {
    private val repository = mockk<MobileUseRepository>()
    private val tools = MobileUseTools(repository).all().associateBy { it.name }

    @Test
    fun actionsAreSeparateTools() {
        assertEquals(
            setOf("mobile_observe", "mobile_open_app", "mobile_click", "mobile_set_text", "mobile_tap", "mobile_tap_relative", "mobile_swipe", "mobile_back", "mobile_type_text", "mobile_wait", "mobile_stop"),
            tools.keys,
        )
    }

    @Test
    fun screenshotIsOnlyAttachedToImmediateNextRequest() = runTest {
        val jpeg = byteArrayOf(1, 2, 3, 4)
        coEvery { repository.observe("turn-1") } returns
            MobileUseResult("ready", "screenshot", 290, jpeg, 1080, 2400)
        val tool = tools.getValue("mobile_observe")
        val payload = tool.execute(context("turn-1"), emptyMap()) as Map<*, *>
        assertEquals(1080, payload["width"])
        assertEquals(2400, payload["height"])
        val request = LlmRequest(
            contents = listOf(
                Content(
                    role = Role.USER,
                    parts = listOf(
                        Part(functionResponse = FunctionResponse(
                            name = tool.name,
                            id = "call-1",
                            response = payload.entries.associate { it.key.toString() to it.value },
                        )),
                    ),
                ),
            ),
        )
        val enriched = tool.processLlmRequest(mockk(), request)
        assertArrayEquals(jpeg, enriched.contents.last().parts.last().inlineData?.data)
        assertEquals(1, tool.processLlmRequest(mockk(), request).contents.size)
    }

    @Test
    fun typeTextTargetsCoordinatesOnOwnedDisplay() = runTest {
        coEvery { repository.typeText("turn-1", "screen-1", 200, 300, "Faded") } returns
            MobileUseResult("text_set", "done", 290, null, 1080, 2400)
        val payload = tools.getValue("mobile_type_text").execute(
            context("turn-1"), mapOf("observationId" to "screen-1", "x" to 200, "y" to 300, "text" to "Faded"),
        ) as Map<*, *>
        assertEquals("text_set", payload["status"])
        coVerify(exactly = 1) { repository.typeText("turn-1", "screen-1", 200, 300, "Faded") }
    }

    @Test
    fun relativeTapUsesNormalizedCoordinates() = runTest {
        coEvery { repository.tapRelative("turn-1", "screen-1", 500, 975) } returns
            MobileUseResult("tapped", "done", 290, null, 1080, 2400)
        val payload = tools.getValue("mobile_tap_relative").execute(
            context("turn-1"), mapOf("observationId" to "screen-1", "xPermille" to 500, "yPermille" to 975),
        ) as Map<*, *>
        assertEquals("tapped", payload["status"])
        coVerify(exactly = 1) { repository.tapRelative("turn-1", "screen-1", 500, 975) }
    }

    @Test
    fun waitDelaysWithoutCapturingOrChangingDisplay() = runTest {
        val waiting = async {
            tools.getValue("mobile_wait").execute(context("turn-1"), mapOf("seconds" to 3)) as Map<*, *>
        }
        runCurrent()
        assertFalse(waiting.isCompleted)
        advanceTimeBy(2_999)
        runCurrent()
        assertFalse(waiting.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertTrue(waiting.isCompleted)
        assertEquals("waited", waiting.await()["status"])
        confirmVerified(repository)
    }

    @Test
    fun waitRejectsOutOfRangeDurations() = runTest {
        val tool = tools.getValue("mobile_wait")
        for (seconds in listOf(0, 1.5, 61)) {
            val response = tool.execute(context("turn-1"), mapOf("seconds" to seconds)) as Map<*, *>
            assertEquals("invalid_argument", response["status"])
        }
    }

    @Test
    fun missingTaskIdentityDoesNotOperateDisplay() = runTest {
        val payload = tools.getValue("mobile_observe").execute(
            mockk { every { context } returns mockk(relaxed = true) }, emptyMap(),
        ) as Map<*, *>
        assertEquals("unavailable", payload["status"])
        assertNull(payload["imageToken"])
    }

    @Test
    fun deliveredClickAndObservationTimeoutAreReportedIndependentlyWithoutRetry() = runTest {
        coEvery { repository.click("turn-1", "screen-1", "a1") } returns MobileUseResult(
            "clicked", "sent", actionStatus = "delivered",
            observation = MobileObservation("screen-2", "timeout", "no_post_action_frame", 800, 1, "not_captured"),
        )
        val payload = tools.getValue("mobile_click").execute(
            context("turn-1"), mapOf("observationId" to "screen-1", "elementId" to "a1"),
        ) as Map<*, *>
        assertEquals("delivered", payload["actionStatus"])
        assertEquals("timeout", payload["observationStatus"])
        assertEquals("screen-2", payload["observationId"])
        coVerify(exactly = 1) { repository.click("turn-1", "screen-1", "a1") }
        confirmVerified(repository)
    }

    @Test
    fun screenshotAndElementsShareExplicitCoordinatesAndPreserveOcrUncertainty() = runTest {
        coEvery { repository.observe("turn-1") } returns MobileUseResult(
            "observed", "quiet", width = 1080, height = 2400,
            observation = MobileObservation("screen-1", "settled", "quiet", 100, 7, "available+ocr_available", listOf(
                MobileElement("a1", "accessibility", MobileBounds(10, 20, 30, 40), text = "确认", clickable = true, actions = listOf("click")),
                MobileElement("o1", "ocr", MobileBounds(50, 60, 70, 80), text = "确认", actions = listOf("tap")),
            )),
        )
        val payload = tools.getValue("mobile_observe").execute(context("turn-1"), emptyMap()) as Map<*, *>
        assertEquals("screenshot_pixels", payload["coordinateSystem"])
        val nodes = payload["nodes"] as List<Map<String, Any>>
        assertEquals(listOf(50, 60, 70, 80), nodes[1]["bounds"])
        assertEquals("ocr", nodes[1]["source"])
        assertFalse(nodes[1].containsKey("clickable"))
        assertEquals(listOf("tap"), nodes[1]["actions"])
        assertEquals(true, nodes[0]["clickable"])
    }

    @Test
    fun setTextUsesElementIdentityAndAcceptsEmptyReplacement() = runTest {
        coEvery { repository.setText("turn-1", "screen-1", "a1", "") } returns
            MobileUseResult("text_set", "done", actionStatus = "delivered")
        val payload = tools.getValue("mobile_set_text").execute(
            context("turn-1"), mapOf("observationId" to "screen-1", "elementId" to "a1", "text" to ""),
        ) as Map<*, *>
        assertEquals("text_set", payload["status"])
        coVerify(exactly = 1) { repository.setText("turn-1", "screen-1", "a1", "") }
    }

    @Test
    fun missingObservationAndFractionalCoordinatesDoNotSendActions() = runTest {
        val tool = tools.getValue("mobile_tap")
        for (arguments in listOf(
            mapOf("x" to 10, "y" to 20),
            mapOf("observationId" to "screen-1", "x" to 10.5, "y" to 20),
            mapOf("observationId" to "screen-1", "x" to Double.NaN, "y" to 20),
        )) {
            val payload = tool.execute(context("turn-1"), arguments) as Map<*, *>
            assertEquals("invalid_argument", payload["status"])
            assertEquals("not_sent", payload["actionStatus"])
        }
        confirmVerified(repository)
    }

    @Test
    fun staleObservationIsReturnedToAgentWithoutAutomaticActionRetry() = runTest {
        coEvery { repository.click("turn-1", "old", "a1") } returns
            MobileUseResult("stale_observation", "observe again", actionStatus = "not_sent")
        val payload = tools.getValue("mobile_click").execute(
            context("turn-1"), mapOf("observationId" to "old", "elementId" to "a1"),
        ) as Map<*, *>
        assertEquals("stale_observation", payload["status"])
        coVerify(exactly = 1) { repository.click("turn-1", "old", "a1") }
        confirmVerified(repository)
    }

    private fun context(owner: String): ToolContext {
        val readonly = mockk<ReadonlyContext>()
        every { readonly.runConfig } returns RunConfig(
            customMetadata = ToolRunMetadata.of(
                ModelRuntimeMetadata("service", ApiProtocol.Standard, "vision", "https://example.com", true),
                null, true, owner,
            ),
        )
        return mockk { every { context } returns readonly }
    }
}
