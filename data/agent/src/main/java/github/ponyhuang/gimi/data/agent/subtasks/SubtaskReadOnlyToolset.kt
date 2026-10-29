package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.skills.SkillSource
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.tools.system.LocalToolset
import github.ponyhuang.gimi.domain.workfiles.repository.DocumentSearchRepository
import github.ponyhuang.gimi.domain.toolauthorization.repository.ToolAuthorizationRepository
import javax.inject.Inject

/** 只复用经宿主授权过滤后的本地读取与技能读取；不连接插件、MCP 或设备动作。 */
class SubtaskReadOnlyToolset @Inject constructor(
    private val local: LocalToolset,
    private val skills: SkillSource,
    private val documentSearch: DocumentSearchRepository,
    private val authorization: ToolAuthorizationRepository,
) : Toolset {
    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        (local.getTools(readonlyContext) + SkillToolset(skills).getTools(readonlyContext))
            .filter { SubtaskPolicy.permits(it.name) && it.name !in FILE_SEARCHES } +
            FILE_SEARCHES.filter { it in authorization.enabledToolIds() }.map(::searchTool) + ParentReadRequestTool

    /** 直接使用纯查询契约，避免复用会在权限变化时打开系统设置的原工具入口。 */
    private fun searchTool(toolName: String): BaseTool = object : BaseTool(
        toolName, "Search existing file names without opening apps or asking for permissions. Missing access must be handled by the parent.",
    ) {
        override fun declaration() = FunctionDeclaration(name, description, parameters = Schema(
            type = Type.OBJECT, properties = mapOf("query" to Schema(type = Type.STRING)), required = listOf("query"),
        ))

        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            // 权限与工具授权在执行时再次检查，不能依靠模型声明时的旧快照。
            if (name !in authorization.enabledToolIds()) return mapOf("status" to "not_authorized")
            if (!documentSearch.hasAccess()) return mapOf(
                "status" to "needs_parent_read",
                "instruction" to "Ask the parent to request file search access through its normal confirmation flow.",
            )
            val query = (args["query"] as? String)?.trim().orEmpty()
            require(query.isNotEmpty()) { "query must not be blank" }
            val results = documentSearch.search(query, 50).filter { file ->
                name != "search_media_files" || file.mimeType.substringBefore('/') in setOf("image", "video", "audio")
            }
            return mapOf("success" to true, "results" to results.map { file -> mapOf(
                "contentUri" to file.contentUri, "displayName" to file.displayName,
                "mimeType" to file.mimeType, "sizeBytes" to file.sizeBytes,
            ) })
        }
    }

    private companion object {
        val FILE_SEARCHES = setOf("search_documents", "search_media_files")
    }
}

/** 与真实读取完全分离的占位请求，不读取文件、不请求权限，供主助手后续走原确认链路。 */
private object ParentReadRequestTool : BaseTool(
    "read_local_file",
    "Cannot read new files inside a subtask. Return the contentUri to the parent assistant for approved reading.",
) {
    override fun declaration() = FunctionDeclaration(
        name, description, parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf("contentUri" to Schema(type = Type.STRING)),
            required = listOf("contentUri"),
        ),
    )

    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any = mapOf(
        "status" to "needs_parent_read",
        "contentUri" to (args["contentUri"] as? String).orEmpty(),
        "instruction" to "Stop reading. Include this URI in your final answer so the parent can request permission and read it.",
    )
}
