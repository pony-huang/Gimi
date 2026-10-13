package dev.e2b.sdk

/** Public sandbox details; timestamps preserve the API's RFC 3339 representation. */
data class SandboxInfo(
    val sandboxId: String,
    val templateId: String,
    val state: String,
    val startedAt: String?,
    val endAt: String?,
    val cpuCount: Int,
    val memoryMB: Int,
    val diskSizeMB: Long,
    val metadata: Map<String, String>,
    val envdVersion: String?,
)

/** A single list page; null nextToken means there are no further pages. */
data class SandboxPage(val items: List<SandboxInfo>, val nextToken: String?) {
    val hasNext: Boolean get() = nextToken != null
}

/** Template listing metadata; names retain namespace-qualified names and status describes the latest build. */
data class TemplateInfo(val templateId: String, val names: List<String>, val buildStatus: String?)

/** One template page; a null nextToken marks the end of the listing. */
data class TemplatePage(val items: List<TemplateInfo>, val nextToken: String?)

/** File metadata from envd; type and modification time use protobuf JSON representations. */
data class EntryInfo(
    val name: String,
    val path: String,
    val type: String,
    val size: Long,
    val mode: Int,
    val permissions: String?,
    val owner: String?,
    val group: String?,
    val modifiedTime: String?,
    val symlinkTarget: String?,
    val isSymlink: Boolean,
    val metadata: Map<String, String>,
)

/** File-upload acknowledgement. */
data class WriteInfo(val name: String, val path: String, val type: String)

/** Completed command, including captured UTF-8 output and process exit status. */
data class CommandResult(val pid: Int, val stdout: String, val stderr: String, val exitCode: Int)

/** A currently running process and its launch configuration. */
data class ProcessInfo(val pid: Int, val command: String, val args: List<String>, val cwd: String?, val envs: Map<String, String>)

/** HTTP/RPC failure. Response bodies and credentials are intentionally omitted. */
open class E2BException(
    message: String,
    val statusCode: Int? = null,
    val rpcCode: String? = null,
    val retryAfter: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class NotFoundException internal constructor(status: Int? = null) : E2BException("Resource not found", status, "not_found")
class AuthenticationException internal constructor(status: Int? = null) : E2BException("Authentication or permission denied", status)
class RateLimitException internal constructor(status: Int?, retryAfter: String?) : E2BException("Rate limit exceeded", status, "resource_exhausted", retryAfter)
class CommandExitException(val result: CommandResult) : E2BException("Command exited with code ${result.exitCode}")
class ProtocolException internal constructor(message: String) : E2BException(message)

/** A bounded file read exceeded its configured byte limit. */
class FileSizeLimitException internal constructor() : E2BException("File exceeds configured size limit")

/** Execution failed after output was captured; failure retains the typed underlying SDK error. */
class PartialOutputException(val failure: E2BException, val partialOutput: String) : E2BException(
    failure.message ?: "Execution failed", failure.statusCode, failure.rpcCode, failure.retryAfter, failure,
)

/** Network I/O failure, including a local request deadline or cancelled stream. */
class TransportException internal constructor(cause: java.io.IOException) : E2BException("E2B transport failed", cause = cause)
