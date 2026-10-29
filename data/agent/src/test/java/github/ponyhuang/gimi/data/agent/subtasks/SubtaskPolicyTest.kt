package github.ponyhuang.gimi.data.agent.subtasks

import org.junit.Assert.*
import org.junit.Test

/** 子任务的输入与工具权限是执行风险边界，保留最小回归覆盖。 */
class SubtaskPolicyTest {
    @Test
    fun rejectsEmptyOversizedAndBlankBatchesBeforeAnyExecution() {
        val valid = mapOf("title" to "分析", "instruction" to "提取结论", "materials" to "资料")
        listOf(emptyList(), List(7) { valid }, listOf(valid + ("instruction" to " "))).forEach { tasks ->
            assertThrows(IllegalArgumentException::class.java) { SubtaskPolicy.parse(mapOf("tasks" to tasks)) }
        }
        assertEquals(6, SubtaskPolicy.parse(mapOf("tasks" to List(6) { valid })).size)
    }

    @Test
    fun onlyReviewedReadsAreAvailableEvenIfOtherToolsNeedNoConfirmation() {
        assertTrue(SubtaskPolicy.permits("search_documents"))
        assertTrue(SubtaskPolicy.permits("load_skill_resource"))
        listOf("delegate_tasks", "search_web", "open_url", "install_skill", "run_script", "read_local_file").forEach {
            assertFalse(it, SubtaskPolicy.permits(it))
        }
    }
}
