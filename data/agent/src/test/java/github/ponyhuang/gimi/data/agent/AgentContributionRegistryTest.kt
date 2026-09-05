package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeSnapshot
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * 贡献方注册表行为验证：确定性排序、id 去重、revision 组合与各类聚合。
 */
class AgentContributionRegistryTest {

    @Test
    fun revisionComposesEveryContributionInStableIdOrder() {
        val registry = registry(
            fakeContribution(id = "plugin", revision = 7L),
            fakeContribution(id = "local", revision = 3L),
            fakeContribution(id = "skill", revision = null),
        )

        // id 排序保证组合值跨进程稳定；静态贡献方以 null 参与组合。
        assertEquals(
            listOf(
                "local" to 3L,
                "plugin" to 7L,
                "skill" to null,
            ),
            registry.revision(),
        )
    }

    @Test
    fun duplicateContributionIdsFailFast() {
        assertThrows(IllegalArgumentException::class.java) {
            registry(
                fakeContribution(id = "local", revision = 1L),
                fakeContribution(id = "local", revision = 2L),
            )
        }
    }

    @Test
    fun aggregatesToolsetsToolsAndCandidateSourcesInIdOrder() = runTest {
        val toolsetA = FakeToolset("toolset-a")
        val toolsetB = FakeToolset("toolset-b")
        val toolA = declarationTool("tool-a")
        val toolB = declarationTool("tool-b")
        val sourceA = FakeToolCandidateSource("source-a")
        val sourceB = FakeToolCandidateSource("source-b")
        val registry = registry(
            fakeContribution(
                id = "b",
                revision = null,
                toolsets = listOf(toolsetB),
                tools = listOf(toolB),
                candidateSources = listOf(sourceB),
            ),
            fakeContribution(
                id = "a",
                revision = null,
                toolsets = listOf(toolsetA),
                tools = listOf(toolA),
                candidateSources = listOf(sourceA),
            ),
        )
        val spec = AgentBuildSpec(pluginRuntime = PluginRuntimeSnapshot(0L, emptyList()))

        assertEquals(listOf(toolsetA, toolsetB), registry.toolsets(spec))
        assertEquals(listOf(toolA, toolB), registry.tools(spec))
        assertEquals(listOf(sourceA, sourceB), registry.candidateSources(spec))
    }

    @Test
    fun toolCatalogAggregatesEntriesFromEveryContribution() = runTest {
        val registry = registry(
            fakeContribution(
                id = "b",
                revision = null,
                catalog = listOf(AgentToolCatalogEntry("plugin", listOf(declarationTool("tool-b")))),
            ),
            fakeContribution(
                id = "a",
                revision = null,
                catalog = listOf(AgentToolCatalogEntry("local", listOf(declarationTool("tool-a")))),
            ),
        )

        assertEquals(
            listOf("local", "plugin"),
            registry.toolCatalog(AgentToolCatalogContext(modelRuntime = null)).map { it.source },
        )
    }

    private fun registry(vararg contributions: AgentContribution) =
        AgentContributionRegistry(contributions.toSet())

    private fun fakeContribution(
        id: String,
        revision: Any?,
        toolsets: List<Toolset> = emptyList(),
        tools: List<BaseTool> = emptyList(),
        candidateSources: List<ToolCandidateSource> = emptyList(),
        catalog: List<AgentToolCatalogEntry> = emptyList(),
    ): AgentContribution = object : AgentContribution {
        override val id: String = id
        override fun revision(): Any? = revision
        override fun toolsets(spec: AgentBuildSpec): List<Toolset> = toolsets
        override fun tools(spec: AgentBuildSpec): List<BaseTool> = tools
        override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> = candidateSources
        override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> = catalog
    }

    private class FakeToolset(
        private val name: String,
    ) : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> = emptyList()
    }

    private class FakeToolCandidateSource(
        override val id: String,
    ) : ToolCandidateSource {
        override val displayName: String = id
        override suspend fun loadAllTools(readonlyContext: ReadonlyContext?): List<BaseTool> = emptyList()
        override suspend fun loadEnabledTools(readonlyContext: ReadonlyContext?): List<BaseTool> = emptyList()
    }

    private companion object {
        fun declarationTool(name: String): BaseTool =
            object : BaseTool(name, name) {
                override fun declaration(): FunctionDeclaration =
                    FunctionDeclaration(name = name, description = description)

                override suspend fun run(
                    context: ToolContext,
                    args: Map<String, Any?>,
                ): Any = emptyMap<String, Any>()
            }
    }
}
