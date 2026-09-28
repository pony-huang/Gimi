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
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MobileUseToolTest {
    private val repository = mockk<MobileUseRepository>()
    private val tool = MobileUseTool(repository)

    @Test
    fun screenshotIsOnlyAttachedToImmediateNextRequest() = runTest {
        val jpeg = byteArrayOf(1, 2, 3, 4)
        coEvery { repository.observe("turn-1") } returns
            MobileUseResult("ready", "screenshot", 290, jpeg)
        val payload = tool.execute(context("turn-1"), mapOf("action" to "observe")) as Map<*, *>
        assertEquals("ready", payload["status"])
        val request = LlmRequest(
            contents = listOf(
                Content(
                    role = Role.USER,
                    parts = listOf(
                        Part(functionResponse = FunctionResponse(
                            name = "mobile_use",
                            id = "call-1",
                            response = payload.entries.associate { it.key.toString() to it.value },
                        )),
                    ),
                ),
            ),
        )

        val enriched = tool.processLlmRequest(mockk(), request)
        assertArrayEquals(jpeg, enriched.contents.last().parts.last().inlineData?.data)
        assertEquals("image/jpeg", enriched.contents.last().parts.last().inlineData?.mimeType)
        assertEquals(Role.USER, enriched.contents.last().role)
        assertEquals(1, tool.processLlmRequest(mockk(), request).contents.size)
    }

    @Test
    fun missingTaskIdentityDoesNotOperateDisplay() = runTest {
        val payload = tool.execute(mockk { every { context } returns mockk(relaxed = true) },
            mapOf("action" to "observe")) as Map<*, *>
        assertEquals("unavailable", payload["status"])
        assertNull(payload["imageToken"])
    }

    private fun context(owner: String): ToolContext {
        val readonly = mockk<ReadonlyContext>()
        every { readonly.runConfig } returns RunConfig(
            customMetadata = ToolRunMetadata.of(
                ModelRuntimeMetadata("service", ApiProtocol.Standard, "vision", "https://example.com", true),
                null,
                true,
                owner,
            ),
        )
        return mockk { every { context } returns readonly }
    }
}
