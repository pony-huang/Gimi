package github.ponyhuang.gimi.data.agent.tools.sandbox

import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.tools.Toolset
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionResult
import github.ponyhuang.gimi.domain.sandbox.SandboxOperation
import github.ponyhuang.gimi.domain.sandbox.SandboxOutput
import github.ponyhuang.gimi.domain.sandbox.SandboxSessionKey
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import github.ponyhuang.gimi.domain.sandbox.isSandboxEnvironmentName
import github.ponyhuang.gimi.domain.sandbox.truncateSandboxOutput
import javax.inject.Inject
import javax.inject.Singleton

/** 五个 E2B 工具的 ADK 声明；不会在枚举工具时创建沙盒或把凭据传给模型。 */
@Singleton
class SandboxTools @Inject constructor(
    private val settings: SandboxSettingsRepository,
    private val execution: SandboxExecutionRepository,
) : Toolset {
    private val envs = Schema(type = Type.OBJECT, description = "Optional string-to-string environment variables for this execution only.")
    private val timeout = Schema(type = Type.INTEGER, description = "Execution timeout in seconds, default 60; zero disables it. Independent of sandbox idle expiry.")
    private val tools: List<BaseTool> = listOf(
        tool("sandbox_run_command", "Run a shell command in the conversation's isolated E2B sandbox. Returns stdout, stderr and exit_code. cwd and per-call envs do not persist; files and installed packages do.",
            mapOf("command" to string("Shell command to run."), "cwd" to string("Optional working directory."), "envs" to envs, "timeout" to timeout), listOf("command")),
        tool("sandbox_run_code", "Run and verify Python or JavaScript in a stateful Code Interpreter. Variables persist in the same conversation and language. Returns stdout, stderr, the main expression text and any kernel error.",
            mapOf("code" to string("Source code to execute."), "language" to Schema(type = Type.STRING, enum = listOf("python", "javascript"), description = "Optional language; defaults to python."), "envs" to envs, "timeout" to timeout), listOf("code")),
        tool("sandbox_write_file", "Create or overwrite a UTF-8 text file in the remote sandbox, creating parent directories. Empty content is allowed; maximum file size is 1 MiB.",
            mapOf("path" to string("Destination path in the sandbox, never an Android device path."), "content" to string("UTF-8 text to write; may be empty.")), listOf("path", "content")),
        tool("sandbox_read_file", "Read a UTF-8 text file from the remote sandbox. Maximum file size is 1 MiB; returned content is limited to 10,000 UTF-8 bytes with a truncation marker.",
            mapOf("path" to string("File path in the sandbox.")), listOf("path")),
        tool("sandbox_list_files", "List the direct children of a directory in the remote sandbox, returning each entry's name and type. Defaults to the current directory (.).",
            mapOf("path" to string("Optional directory path in the sandbox; defaults to .")), emptyList()),
    )

    override suspend fun getTools(readonlyContext: ReadonlyContext?): List<BaseTool> =
        if (settings.configuration.value.isReady) tools else emptyList()

    private fun tool(name: String, description: String, properties: Map<String, Schema>, required: List<String>): BaseTool = object : BaseTool(
        name = name,
        description = "$description The success flag means the operation ran; inspect exit_code/error for command/code failures. environment_reset means previous files and interpreter state were lost. Do not automatically replay an operation after an unknown execution status.",
    ) {
        override fun declaration() = FunctionDeclaration(name = name, description = this.description,
            parameters = Schema(type = Type.OBJECT, properties = properties, required = required))

        override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
            // 已按需加载或缓存的旧工具仍需复查开关，禁止配置变化后继续使用旧环境。
            if (!settings.configuration.value.isReady) return mapOf("success" to false, "error" to "E2B sandbox is disabled or not configured.", "partial_output" to "")
            val operation = try { parseSandboxOperation(name, args) }
            catch (error: SandboxArgumentsException) { return mapOf("success" to false, "error" to error.message, "partial_output" to "") }
            val key = context.context.session.key
            val sessionId = key.id?.takeIf(String::isNotBlank)
                ?: return mapOf("success" to false, "error" to "A persistent conversation identity is required.", "partial_output" to "")
            return execution.execute(SandboxSessionKey(key.userId, sessionId), operation).toToolResponse(operation)
        }
    }

    private fun string(description: String) = Schema(type = Type.STRING, description = description)
}

/** 仅指向模型调用参数错误，不捕获执行仓库中的编程错误。 */
internal class SandboxArgumentsException(message: String) : IllegalArgumentException(message)

internal fun parseSandboxOperation(name: String, args: Map<String, Any?>): SandboxOperation {
    fun required(key: String, allowEmpty: Boolean = false): String {
        val value = args[key] as? String ?: throw SandboxArgumentsException("Missing or invalid required argument: $key")
        if (!allowEmpty && value.isBlank()) throw SandboxArgumentsException("Missing or invalid required argument: $key")
        return value
    }
    fun optionalString(key: String): String? = args[key]?.let {
        it as? String ?: throw SandboxArgumentsException("$key must be a string")
    }
    fun envs(): Map<String, String> {
        val values = args["envs"] ?: return emptyMap()
        if (values !is Map<*, *>) throw SandboxArgumentsException("envs must be a string-to-string object")
        return values.entries.associate { (key, value) ->
            if (key !is String || value !is String || !isSandboxEnvironmentName(key) || '\u0000' in value) {
                throw SandboxArgumentsException("envs must contain valid names and string values")
            }
            key to value
        }
    }
    fun timeout(): Int {
        val value = args["timeout"] ?: return 60
        val number = (value as? Number)?.toDouble() ?: throw SandboxArgumentsException("timeout must be a non-negative integer")
        if (!number.isFinite() || number < 0 || number > Int.MAX_VALUE || number % 1.0 != 0.0) {
            throw SandboxArgumentsException("timeout must be a non-negative integer")
        }
        return number.toInt()
    }
    return when (name) {
        "sandbox_run_command" -> SandboxOperation.Command(required("command"), optionalString("cwd"), envs(), timeout())
        "sandbox_run_code" -> {
            val language = optionalString("language")?.lowercase() ?: "python"
            if (language !in setOf("python", "javascript")) throw SandboxArgumentsException("language must be python or javascript")
            SandboxOperation.Code(required("code"), language, envs(), timeout())
        }
        "sandbox_write_file" -> SandboxOperation.WriteFile(required("path"), required("content", allowEmpty = true))
        "sandbox_read_file" -> SandboxOperation.ReadFile(required("path"))
        "sandbox_list_files" -> SandboxOperation.ListFiles(optionalString("path")?.takeIf(String::isNotBlank) ?: ".")
        else -> throw SandboxArgumentsException("Unknown sandbox tool")
    }
}

/** 保持参考插件的 success/error/exit_code 语义，所有模型文本字段统一限长。 */
internal fun SandboxExecutionResult.toToolResponse(operation: SandboxOperation): Map<String, Any?> = buildMap {
    if (success) {
        when (val value = output) {
            is SandboxOutput.Command -> {
                put("command", truncateSandboxOutput(value.command)); put("stdout", truncateSandboxOutput(value.stdout))
                put("stderr", truncateSandboxOutput(value.stderr)); put("exit_code", value.exitCode)
            }
            is SandboxOutput.Code -> {
                put("stdout", truncateSandboxOutput(value.stdout)); put("stderr", truncateSandboxOutput(value.stderr))
                put("text", truncateSandboxOutput(value.text)); put("error", truncateSandboxOutput(value.error))
            }
            is SandboxOutput.WrittenFile -> put("path", truncateSandboxOutput(value.path))
            is SandboxOutput.ReadFile -> { put("path", truncateSandboxOutput(value.path)); put("content", truncateSandboxOutput(value.content)) }
            is SandboxOutput.Files -> {
                put("path", truncateSandboxOutput(value.path))
                put("entries", value.entries.map { mapOf("name" to truncateSandboxOutput(it.name), "type" to it.type) })
            }
            null -> Unit
        }
    } else {
        put("error", truncateSandboxOutput(error)); put("partial_output", truncateSandboxOutput(partialOutput))
        when (operation) {
            is SandboxOperation.Command -> put("command", truncateSandboxOutput(operation.command))
            is SandboxOperation.ReadFile -> put("path", truncateSandboxOutput(operation.path))
            is SandboxOperation.WriteFile -> put("path", truncateSandboxOutput(operation.path))
            is SandboxOperation.ListFiles -> put("path", truncateSandboxOutput(operation.path))
            is SandboxOperation.Code -> Unit
        }
    }
    if (environmentReset) {
        put("environment_reset", true)
        put("notice", "The previous sandbox expired. Its files and interpreter state were reset before this operation.")
    }
    put("success", success)
}
