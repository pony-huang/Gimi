package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.agents.Context
import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.*
import github.ponyhuang.gimi.data.agent.tools.search.ToolCandidateSource
import github.ponyhuang.gimi.data.agent.tools.search.ToolsetCandidateSource
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.environment.EnvironmentRepository
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 本地 Debian 执行面：文件读写走 shell，只暴露 execute 与三个长任务小写工具。 */
@Singleton
class EnvironmentToolContribution @Inject constructor(private val repository: EnvironmentRepository, private val bridgeTools: EnvironmentBridgeTools) : AgentContribution {
    override val id = "environment"
    private val statusTool = EnvironmentStatusTool(repository)

    override fun tools(spec: AgentBuildSpec): List<BaseTool> = listOf(statusTool)

    /**
     * 启停是跨进程实时状态，无法进入同步 revision；工具集在每次 getTools 时
     * 重新检查启用状态，因此开关切换靠动态收敛而不是 Agent 重建。
     */
    override fun revision(): Any? = null

    private fun session(context: Context): String {
        val key = context.session.key
        val id = requireNotNull(key.id).also { require(it.isNotBlank()) }
        return "${key.userId.length}:${key.userId}$id"
    }

    private val executeTool = object : BaseTool(
        "execute",
        "Run a shell command in this conversation's persistent Debian workspace. " +
            "Relative paths resolve inside the workspace and files persist when the environment is disabled. " +
            "Chain sequential, dependent commands with && inside one call.",
    ) {
        override fun declaration() = FunctionDeclaration(
            name = name,
            description = description,
            parameters = Schema(
                type = Type.OBJECT,
                properties = mapOf(
                    "command" to Schema(type = Type.STRING, description = "The shell command to run. Chain dependent commands with &&."),
                ),
                required = listOf("command"),
            ),
        )
        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            if (!repository.status().let { it.enabled && it.runtimeUsable }) return mapOf("error" to "Local environment is disabled.")
            val command = args["command"] as? String
            if (command.isNullOrEmpty()) return mapOf("error" to "command is required.")
            return try {
                // timeout 0 交由引擎取 30 秒默认；更长的任务必须走 start_job。
                val result = repository.execute(session(context), command, 0)
                buildMap {
                    put("status", "ok")
                    if (result.stdout.isNotEmpty()) put("stdout", result.stdout)
                    if (result.stderr.isNotEmpty()) put("stderr", result.stderr)
                    if (result.exitCode != 0) { put("status", "error"); put("exit_code", result.exitCode) }
                    if (result.timedOut) { put("status", "error"); put("error", "Command timed out after 30 seconds. Use start_job for long-running commands.") }
                }
            } catch (error: IOException) {
                mapOf("status" to "error", "error" to NON_REPLAYABLE_ERROR)
            }
        }
    }

    private fun jobTool(operation: String): BaseTool = object : BaseTool(
        operation,
        when (operation) {
            "start_job" -> "Start a long-running command without waiting. Returns a job id. Set keep_alive=true for commands that launch a background login poller or daemon. Query live output and call stop_job after authorization or service use completes."
            "query_job" -> "Poll a job for running state and current stdout/stderr, including login links before completion. Completed jobs also return exit_code."
            else -> "Cancel a started job. Files and packages it already wrote persist."
        },
    ) {
        override fun declaration() = FunctionDeclaration(
            name = name,
            description = description,
            parameters = Schema(
                type = Type.OBJECT,
                properties = buildMap {
                    put(if (operation == "start_job") "command" else "id", Schema(type = Type.STRING))
                    if (operation == "start_job") put("keep_alive", Schema(type = Type.BOOLEAN,
                        description = "Default false. True retains a successfully started command's background processes until stop_job or environment shutdown."))
                },
                required = listOf(if (operation == "start_job") "command" else "id"),
            ),
        )
        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            if (!repository.status().let { it.enabled && it.runtimeUsable }) return mapOf("error" to "Local environment is disabled.")
            val key = session(context)
            return try {
                when (operation) {
                    "start_job" -> {
                        val command = args["command"] as? String ?: return mapOf("error" to "command required")
                        val keepAlive = if (args.containsKey("keep_alive")) args["keep_alive"] as? Boolean
                            ?: return mapOf("error" to "keep_alive must be boolean") else false
                        mapOf("id" to repository.startJob(key, command, keepAlive))
                    }
                    "query_job" -> repository.queryJob(key, args["id"] as? String ?: return mapOf("error" to "id required")).let { job ->
                        buildMap {
                            put("running", job.running)
                            if (job.stdout.isNotEmpty()) put("stdout", job.stdout)
                            if (job.stderr.isNotEmpty()) put("stderr", job.stderr)
                            if (!job.running) put("exit_code", job.exitCode)
                            if (job.timedOut) put("timed_out", true)
                        }
                    }
                    else -> { repository.stopJob(key, args["id"] as? String ?: return mapOf("error" to "id required")); mapOf("stopped" to true) }
                }
            } catch (error: IOException) { mapOf("error" to "Local job unavailable. Its execution status may be unknown.") }
        }
    }

    private val toolset = object : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
            if (!repository.status().let { it.enabled && it.runtimeUsable }) return emptyList()
            return listOf(executeTool, jobTool("start_job"), jobTool("query_job"), jobTool("stop_job")) + bridgeTools.tools()
        }
        override suspend fun processLlmRequest(toolContext: ToolContext, llmRequest: LlmRequest): LlmRequest =
            llmRequest.appendInstructions(Content(parts = listOf(Part(text = WORKSPACE_INSTRUCTION))))
    }

    override fun toolsets(spec: AgentBuildSpec): List<Toolset> = if (spec.toolAccessMode == ToolAccessMode.ALWAYS_AVAILABLE) listOf(toolset) else emptyList()
    override suspend fun candidateSources(spec: AgentBuildSpec): List<ToolCandidateSource> =
        if (spec.toolAccessMode == ToolAccessMode.ON_DEMAND && repository.status().let { it.enabled && it.runtimeUsable }) listOf(ToolsetCandidateSource("environment:local", "Local Debian environment", toolset)) else emptyList()
    override suspend fun toolCatalog(context: AgentToolCatalogContext): List<AgentToolCatalogEntry> =
        listOf(AgentToolCatalogEntry("environment:status", listOf(statusTool))) +
            if (repository.status().let { it.enabled && it.runtimeUsable }) listOf(AgentToolCatalogEntry("environment:local", toolset.getTools(null))) else emptyList()
}

private const val NON_REPLAYABLE_ERROR =
    "Local environment operation failed. Execution may have partially completed; do not replay automatically."

private val WORKSPACE_INSTRUCTION = """
You have a persistent Debian workspace for running commands.
Use install_skill to register downloaded skills in the App library before prepare_skill.
Use prepare_skill to copy an installed skill into Linux before running its scripts.
Use import_file_to_environment for App document inputs and publish_environment_file to return completed outputs to the App workspace.

DO:
- Chain sequential, dependent commands with `&&` in a single `execute` call.
- Read and write files with shell commands (`cat`, heredocs, redirection) inside `execute`.
- Use `start_job` for operations longer than 30 seconds. `query_job` returns live output while running; use `stop_job` when finished.
- For a CLI that starts its own background login poller or daemon, use `start_job` with `keep_alive=true`. Read its link with `query_job`, wait for user authorization, verify login and call `stop_job`.
- Let the host wait between status checks. Keep each `execute` below 30 seconds.
- Verify important command outcomes and business response fields. Bash pipelines use pipefail when available; explicitly check installation/version/login business results.

DON'T:
- Split dependent commands across multiple `execute` calls (they run in parallel).
- Use the same file in two calls of one response (parallel calls can lose updates).
""".trimIndent()
