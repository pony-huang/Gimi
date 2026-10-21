package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.*
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.data.agent.*
import github.ponyhuang.gimi.domain.appfunction.*
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 跨应用工具必须遵守开关，即使工具已缓存也不能越过关闭决定。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppFunctionToolContributionTest {
    private class Repository : AppFunctionRepository {
        override val catalog = MutableStateFlow(AppFunctionCatalog())
        override val enabledPackages = MutableStateFlow(setOf("notes"))
        override val revision = MutableStateFlow(0L)
        override suspend fun refresh() = Unit
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            enabledPackages.value = if (enabled) enabledPackages.value + packageName else enabledPackages.value - packageName
            revision.value++
        }
    }
    private class Tool : BaseTool("createNote", "Create note") {
        var calls = 0
        override fun declaration() = FunctionDeclaration(name = name, description = description)
        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any { calls++; return args }
    }
    private val repository = Repository()
    private val delegate = Tool()
    private val source = object : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?) = listOf(delegate)
    }
    private val factory = mockk<AppFunctionToolsetFactory> { every { create(any()) } returns source }
    private val contribution = AppFunctionToolContribution(repository, factory)
    private fun spec(mode: ToolAccessMode) = AgentBuildSpec(toolAccessMode = mode, pluginRuntime = mockk())

    @Test fun bothAccessModesAndCatalogOfferTheSameEnabledTools() = runTest {
        val always = contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).single().getTools(null)
        assertTrue(contribution.candidateSources(spec(ToolAccessMode.ALWAYS_AVAILABLE)).isEmpty())
        assertTrue(contribution.toolsets(spec(ToolAccessMode.ON_DEMAND)).isEmpty())
        val sources = contribution.candidateSources(spec(ToolAccessMode.ON_DEMAND))
        assertEquals("appfunction:notes", sources.single().id)
        assertEquals(always.map { it.name }, sources.single().loadEnabledTools(null).map { it.name })
        assertEquals(always.map { it.name }, contribution.toolCatalog(AgentToolCatalogContext(null)).single().tools.map { it.name })
    }

    @Test fun disablingBlocksAlreadyCachedToolsAndSearchSources() = runTest {
        val candidate = contribution.candidateSources(spec(ToolAccessMode.ON_DEMAND)).single()
        val tool = candidate.loadEnabledTools(null).single()
        val args = mapOf("title" to "test")
        assertEquals(args, tool.run(mockk(), args))
        val before = contribution.revision()
        repository.setEnabled("notes", false)
        assertNotEquals(before, contribution.revision())
        assertTrue((tool.run(mockk(), args) as Map<*, *>).containsKey("error"))
        assertEquals(1, delegate.calls)
        assertTrue(candidate.loadEnabledTools(null).isEmpty())
        assertTrue(contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).isEmpty())
        assertTrue(contribution.toolCatalog(AgentToolCatalogContext(null)).isEmpty())
    }

    @Test fun failedAppDoesNotRemoveOtherAppsAndCancellationPropagates() = runTest {
        repository.setEnabled("broken", true)
        val broken = mockk<Toolset> { coEvery { getTools(any()) } throws IllegalStateException(); every { close() } just Runs }
        every { factory.create("broken") } returns broken
        assertEquals(1, contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).flatMap { it.getTools(null) }.size)
        assertEquals(1, contribution.toolCatalog(AgentToolCatalogContext(null)).flatMap { it.tools }.size)
        coEvery { broken.getTools(any()) } throws CancellationException()
        try { contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).first().getTools(null); fail("must cancel") }
        catch (_: CancellationException) { }
    }

    @Test fun closingAnAppDuringDiscoveryDoesNotPublishItsTools() = runTest {
        val ready = CompletableDeferred<Unit>()
        val delayed = object : Toolset {
            override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
                ready.await()
                return listOf(delegate)
            }
        }
        val guarded = GuardedAppFunctionToolset("notes", delayed, repository)
        val pending = async { guarded.getTools(null) }
        runCurrent()
        repository.setEnabled("notes", false)
        ready.complete(Unit)
        assertTrue(pending.await().isEmpty())
    }

    @Test fun namesRemainStableDistinctAndMatchTheDeclaration() = runTest {
        val longName = "create".repeat(50)
        val first = appFunctionToolName("notes", longName)
        assertTrue(first.length <= 64)
        assertEquals(first, appFunctionToolName("notes", longName))
        assertNotEquals(first, appFunctionToolName("other.notes", longName))
        assertNotEquals(first, appFunctionToolName("notes", longName + "x"))
        assertTrue(first.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")))
        val tool = contribution.toolsets(spec(ToolAccessMode.ALWAYS_AVAILABLE)).single().getTools(null).single()
        assertEquals(tool.name, tool.declaration()!!.name)
    }
}
