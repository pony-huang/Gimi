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
import github.ponyhuang.gimi.domain.environment.EnvironmentJobStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** 本地 Debian 执行面：文件读写走 shell，只暴露 execute、write_stdin 和 terminate。 */
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

    private val executeTool = commandTool(
        "execute",
        "Run a shell command in this conversation's persistent Debian workspace. " +
            "Chain dependent commands with &&. If still running after the wait window, returns an id and current output.",
        mapOf(
            "command" to Schema(type = Type.STRING, description = "Shell command to run in the workspace."),
            "yield_time_ms" to Schema(type = Type.INTEGER, description = "How long to wait before returning, 0 to 30000 ms. Default 1000. Zero returns immediately."),
            "keep_alive" to Schema(type = Type.BOOLEAN, description = "Default false. Retain background processes after successful command exit until terminate or environment shutdown."),
        ),
        required = "command",
    ) { session, args ->
        val command = (args["command"] as? String)?.takeIf { it.isNotBlank() }
            ?: return@commandTool mapOf("error" to "command is required.")
        val keepAlive = if ("keep_alive" in args) {
            args["keep_alive"] as? Boolean ?: return@commandTool mapOf("error" to "keep_alive must be boolean.")
        } else false
        val waitMillis = if ("yield_time_ms" in args) {
            val number = (args["yield_time_ms"] as? Number)?.toDouble()
            if (number == null || number !in 0.0..30_000.0 || number != number.toLong().toDouble()) {
                return@commandTool mapOf("error" to "yield_time_ms must be an integer from 0 to 30000.")
            }
            number.toLong()
        } else 1_000L
        var id: String? = null
        try {
            currentCoroutineContext().ensureActive()
            // 拿到 ID 后才响应取消，避免 Binder 已启动任务但调用方丢失回收句柄。
            withContext(NonCancellable) { id = repository.startJob(session, command, keepAlive) }
            currentCoroutineContext().ensureActive()
            val jobId = checkNotNull(id)
            var snapshot = EnvironmentJobStatus(running = true)
            if (waitMillis > 0) {
                withTimeoutOrNull(waitMillis) {
                    do {
                        snapshot = repository.queryJob(session, jobId)
                        if (snapshot.running) delay(200)
                    } while (snapshot.running)
                }
            }
            snapshot.toResponse(jobId.takeIf { snapshot.running })
        } catch (error: CancellationException) {
            id?.let { jobId ->
                withContext(NonCancellable) {
                    try { repository.stopJob(session, jobId) } catch (_: IOException) { }
                }
            }
            throw error
        } catch (_: IOException) {
            buildMap {
                put("status", "error")
                put("error", NON_REPLAYABLE_ERROR)
                id?.let { put("id", it) }
            }
        }
    }

    private val writeStdinTool = commandTool(
        "write_stdin",
        "Poll a running or completed command for its status and output. With non-empty chars, queue UTF-8 input for the process first. Include a newline for line-oriented input.",
        mapOf(
            "id" to Schema(type = Type.STRING, description = "Command id returned by execute."),
            "chars" to Schema(type = Type.STRING, description = "Text to send, up to 4096 UTF-8 bytes. Omit or use an empty string to poll."),
        ),
        required = "id",
    ) { session, args ->
        val id = (args["id"] as? String)?.takeIf { it.isNotBlank() }
            ?: return@commandTool mapOf("error" to "id required.")
        val chars = if ("chars" in args) {
            args["chars"] as? String ?: return@commandTool mapOf("error" to "chars must be a string.")
        } else ""
        if (chars.length > 4096 || chars.toByteArray().size > 4096) {
            return@commandTool mapOf("error" to "chars must be at most 4096 UTF-8 bytes.")
        }
        if (chars.isNotEmpty()) {
            try { repository.writeJobInput(session, id, chars) }
            catch (_: IOException) {
                return@commandTool mapOf("error" to "Could not confirm input was queued. The task may be unavailable, its input closed, or its queue full. Poll before retrying.")
            }
        }
        try { repository.queryJob(session, id).toResponse() }
        catch (_: IOException) {
            mapOf("error" to if (chars.isEmpty()) "Local job unavailable." else "Input queued, but job status is unavailable. Poll without chars.")
        }
    }

    private val terminateTool = commandTool(
        "terminate",
        "Stop a managed local command and its background processes. Files and packages already written persist.",
        mapOf("id" to Schema(type = Type.STRING, description = "Command id returned by execute.")),
        required = "id",
    ) { session, args ->
        val id = (args["id"] as? String)?.takeIf { it.isNotBlank() }
            ?: return@commandTool mapOf("error" to "id required.")
        try {
            repository.stopJob(session, id)
            mapOf("stopped" to true)
        } catch (_: IOException) {
            mapOf("error" to "Local job unavailable. Its execution status may be unknown.")
        }
    }

    private fun commandTool(
        name: String,
        description: String,
        properties: Map<String, Schema>,
        required: String,
        invoke: suspend (String, Map<String, Any?>) -> Map<String, Any>,
    ): BaseTool = object : BaseTool(name, description) {
        override fun declaration() = FunctionDeclaration(
            name = name,
            description = description,
            parameters = Schema(type = Type.OBJECT, properties = properties, required = listOf(required)),
        )

        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            if (!repository.status().let { it.enabled && it.runtimeUsable }) return mapOf("error" to "Local environment is disabled.")
            return invoke(session(context), args)
        }
    }

    private fun EnvironmentJobStatus.toResponse(id: String? = null): Map<String, Any> = buildMap {
        put("status", when {
            running -> "running"
            exitCode == 0 && !timedOut -> "ok"
            else -> "error"
        })
        put("running", running)
        id?.let { put("id", it) }
        if (stdout.isNotEmpty()) put("stdout", stdout)
        if (stderr.isNotEmpty()) put("stderr", stderr)
        exitCode?.let { if (!running) put("exit_code", it) }
        if (timedOut) put("timed_out", true)
    }

    private val toolset = object : Toolset {
        override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> {
            if (!repository.status().let { it.enabled && it.runtimeUsable }) return emptyList()
            return listOf(executeTool, writeStdinTool, terminateTool) + bridgeTools.tools()
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
- When `execute` returns running=true, use `write_stdin` with the returned id to check the same command. Omit chars to poll, and wait between checks.
- For a CLI that starts a background login poller or daemon, use `execute` with keep_alive=true. Read the link with `write_stdin`, wait for user authorization, verify login, then call `terminate`.
- Verify important command outcomes and business response fields. Bash pipelines use pipefail when available; explicitly check installation/version/login business results.

DON'T:
- Split dependent commands across multiple `execute` calls (they run in parallel).
- Use the same file in two calls of one response (parallel calls can lose updates).
""".trimIndent()
