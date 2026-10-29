package github.ponyhuang.gimi.data.agent.subtasks

/** 主助手显式传入的一项分析任务；材料是文本，不自动携带附件或主会话历史。 */
internal data class SubtaskRequest(val id: String, val title: String, val instruction: String, val materials: String)

/** 输入与只读工具的封闭策略；新增工具必须在此逐项审核，不能依据名称猜测安全性。 */
internal object SubtaskPolicy {
    private val reads = setOf(
        "search_documents", "search_media_files", "get_current_time",
        "list_skills", "load_skill", "load_skill_resource",
    )

    fun permits(name: String): Boolean = name in reads

    fun parse(args: Map<String, Any?>): List<SubtaskRequest> {
        val tasks = args["tasks"] as? List<*> ?: throw IllegalArgumentException("tasks must be a list")
        require(tasks.size in 1..6) { "Delegate between 1 and 6 tasks" }
        return tasks.mapIndexed { index, raw ->
            val task = raw as? Map<*, *> ?: throw IllegalArgumentException("Invalid task")
            val title = (task["title"] as? String)?.trim().orEmpty()
            val instruction = (task["instruction"] as? String)?.trim().orEmpty()
            require(title.isNotEmpty() && instruction.isNotEmpty()) { "Task title and instruction are required" }
            val materials = task["materials"]
            require(materials == null || materials is String) { "Task materials must be text" }
            SubtaskRequest("task-${index + 1}", title, instruction, materials as? String ?: "")
        }
    }
}
