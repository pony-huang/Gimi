package github.ponyhuang.gimi.agent

import github.ponyhuang.gimi.agent.tools.mcp.ConversationMcpToolset
import github.ponyhuang.gimi.agent.tools.mcp.McpToolException.McpToolLoadingException
import github.ponyhuang.gimi.agent.tools.mcp.McpToolset
import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.domain.mcp.model.McpSkippedServer
import github.ponyhuang.gimi.domain.mcp.repository.McpSkipReporter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ConversationMcpToolset] 的失败降级契约：
 * 单个 server 加载失败只丢弃该 server（不抛异常、不影响其它 server），
 * 失败在同一 resolution 内被缓存，resolution 重建后重新探测。
 */
class ConversationMcpToolsetProbeTest {

    @Test
    fun failingServerIsSkippedWithoutThrowingAndReported() = runTest {
        val goodTool = mockk<BaseTool>()
        val goodToolset = toolsetReturning(listOf(goodTool))
        val badToolset = toolsetThrowing(McpToolLoadingException("Failed to load tools.", null))
        val skipReporter = RecordingSkipReporter()
        val resolution = McpToolsetResolution(
            listOf(
                McpToolsetHandle("good", "Good", isGloballyEnabled = true, goodToolset),
                McpToolsetHandle("bad", "Bad", isGloballyEnabled = true, badToolset),
            ),
        )
        val toolset = ConversationMcpToolset(registryReturning(resolution), skipReporter)

        val tools = toolset.getTools(null)

        assertEquals(listOf(goodTool), tools)
        assertEquals(
            listOf(McpSkippedServer("bad", "Bad", "Failed to load tools.")),
            skipReporter.published.single(),
        )
    }

    @Test
    fun failureIsCachedWithinSameResolutionAndReprobedOnNewResolution() = runTest {
        val badToolset = toolsetThrowing(McpToolLoadingException("Failed to load tools.", null))
        val handle = McpToolsetHandle("bad", "Bad", isGloballyEnabled = true, badToolset)
        val registry = mockk<McpToolsetRegistry>()
        val firstResolution = McpToolsetResolution(listOf(handle))
        coEvery { registry.resolve(null) } returns firstResolution
        val toolset = ConversationMcpToolset(registry, RecordingSkipReporter())

        toolset.getTools(null)
        toolset.getTools(null)
        // 第二次调用命中失败缓存，不再触碰坏 server。
        coVerify(exactly = 1) { badToolset.getTools(any()) }

        // registry 缓存重建（新 handles 列表实例）后重新探测。
        coEvery { registry.resolve(null) } returns McpToolsetResolution(listOf(handle))
        toolset.getTools(null)
        coVerify(exactly = 2) { badToolset.getTools(any()) }
    }

    @Test
    fun healthyServerStillResolvesWhenOthersFail() = runTest {
        val goodTool = mockk<BaseTool>()
        val resolution = McpToolsetResolution(
            listOf(
                McpToolsetHandle(
                    "bad",
                    "Bad",
                    isGloballyEnabled = true,
                    toolsetThrowing(RuntimeException("connection refused")),
                ),
                McpToolsetHandle("good", "Good", isGloballyEnabled = true, toolsetReturning(listOf(goodTool))),
            ),
        )
        val toolset = ConversationMcpToolset(registryReturning(resolution), RecordingSkipReporter())

        assertEquals(listOf(goodTool), toolset.getTools(null))
    }

    private fun toolsetReturning(tools: List<BaseTool>): McpToolset = mockk {
        coEvery { getTools(any()) } returns tools
    }

    private fun toolsetThrowing(error: Throwable): McpToolset = mockk {
        coEvery { getTools(any()) } throws error
    }

    private fun registryReturning(resolution: McpToolsetResolution): McpToolsetRegistry = mockk {
        coEvery { resolve(null) } returns resolution
    }

    private class RecordingSkipReporter : McpSkipReporter {
        val published = mutableListOf<List<McpSkippedServer>>()
        private val _skipped = MutableStateFlow<List<McpSkippedServer>>(emptyList())
        override val skipped: StateFlow<List<McpSkippedServer>> = _skipped

        override fun publish(skipped: List<McpSkippedServer>) {
            published.add(skipped)
            _skipped.value = skipped
        }
    }
}
