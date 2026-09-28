package github.ponyhuang.gimi.data.agent.tools.system

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.agents.RunConfig
import github.ponyhuang.gimi.data.agent.LocalToolCatalog
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileUseAvailabilityTest {
    @Test
    fun toolIsOfferedOnlyToImageCapableModels() = runTest {
        val mobileTool = MobileUseTool(mockk<MobileUseRepository>())
        val catalog = mockk<LocalToolCatalog>()
        val authorization = mockk<ToolAuthorizationRepository>()
        every { catalog.tools() } returns listOf(mobileTool)
        coEvery { authorization.enabledToolIds() } returns setOf("mobile_use")
        val toolset = LocalToolset(catalog, authorization)

        assertEquals(emptyList<Any>(), toolset.getTools(context(supportsImages = false)))
        assertEquals(listOf(mobileTool), toolset.getTools(context(supportsImages = true)))
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
