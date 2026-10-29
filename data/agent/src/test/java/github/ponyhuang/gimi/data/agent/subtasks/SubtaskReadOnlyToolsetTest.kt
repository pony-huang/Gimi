package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.data.agent.tools.system.LocalToolset
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 搜索权限缺失和授权撤销均不得让子任务启动系统授权页面。 */
class SubtaskReadOnlyToolsetTest {
    @Test
    fun missingAccessAndRevocationNeverInvokeThePermissionRequestingSearchTool() = runTest {
        val local = mockk<LocalToolset>()
        coEvery { local.getTools(any()) } returns emptyList()
        val access = mockk<DocumentSearchRepository>()
        val authorization = mockk<ToolAuthorizationRepository>()
        coEvery { authorization.enabledToolIds() } returns setOf("search_documents")
        coEvery { access.hasAccess() } returns false
        val toolset = SubtaskReadOnlyToolset(local, mockk<SkillSource>(relaxed = true), access, authorization)
        val tools = toolset.getTools(null)
        assertFalse(tools.any { it.name == "search_media_files" || it.name == "delegate_tasks" })
        val search = tools.single { it.name == "search_documents" }
        val result = search.run(mockk<ToolContext>(), mapOf("query" to "报告")) as Map<*, *>
        assertEquals("needs_parent_read", result["status"])
        coVerify(exactly = 0) { access.search(any(), any()) }
        coEvery { authorization.enabledToolIds() } returns emptySet()
        coEvery { access.hasAccess() } returns true
        assertEquals("not_authorized", (search.run(mockk<ToolContext>(), mapOf("query" to "报告")) as Map<*, *>)["status"])
        coVerify(exactly = 0) { access.search(any(), any()) }
    }
}
