package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.MobileUseAvailability
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileUseAvailabilityTest {
    @Test
    fun toolsetFollowsShizukuRuntimeAndVisionCapability() = runTest {
        val repository = mockk<MobileUseRepository>()
        val toolset = MobileUseTools(repository)
        every { repository.availability() } returns MobileUseAvailability.DISABLED
        assertEquals(emptyList<Any>(), toolset.getTools(context(supportsImages = true)))
        every { repository.availability() } returns MobileUseAvailability.SHIZUKU_STOPPED
        assertEquals(emptyList<Any>(), toolset.getTools(context(supportsImages = true)))
        every { repository.availability() } returns MobileUseAvailability.READY
        assertEquals(emptyList<Any>(), toolset.getTools(context(supportsImages = false)))
        assertEquals(toolset.all(), toolset.getTools(context(supportsImages = true)))
        every { repository.availability() } returns MobileUseAvailability.PERMISSION_REQUIRED
        assertEquals(toolset.all(), toolset.getTools(context(supportsImages = true)))
    }

    @Test
    fun instructionsAreIncludedOnlyWhenMobileUseIsOffered() = runTest {
        val repository = mockk<MobileUseRepository>()
        val toolset = MobileUseTools(repository)
        val readonly = context(supportsImages = true)
        val toolContext = mockk<ToolContext> { every { context } returns readonly }
        val request = LlmRequest()
        every { repository.availability() } returns MobileUseAvailability.DISABLED
        assertEquals(request, toolset.processLlmRequest(toolContext, request))
        every { repository.availability() } returns MobileUseAvailability.SHIZUKU_STOPPED
        assertEquals(request, toolset.processLlmRequest(toolContext, request))
        every { repository.availability() } returns MobileUseAvailability.READY
        val processed = toolset.processLlmRequest(toolContext, request)
        assertTrue(processed.config.systemInstruction?.parts.orEmpty()
            .mapNotNull { it.text }.joinToString("\n").contains("isolated secondary display"))
    }

    private fun context(supportsImages: Boolean): ReadonlyContext = mockk {
        every { runConfig } returns RunConfig(
            customMetadata = ToolRunMetadata.of(
                ModelRuntimeMetadata(
                    "service", ApiProtocol.Standard, "model", "https://example.com", supportsImages,
                ),
                null,
                true,
            ),
        )
    }
}
