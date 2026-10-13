package github.ponyhuang.gimi.domain.sandbox

/**
 * 隔离环境的聊天归属，不与跨聊天共享的 Agent 缓存绑定。
 * @property userId 当前 ADK 用户标识。
 * @property sessionId 当前持久化聊天标识。
 */
data class SandboxSessionKey(val userId: String, val sessionId: String)

/** 第一版允许的远端执行操作。 */
sealed interface SandboxOperation {
    /** Shell 操作；cwd/envs 仅用于本次进程，timeoutSeconds=0 表示不设执行期限。 */
    data class Command(val command: String, val cwd: String? = null, val envs: Map<String, String> = emptyMap(), val timeoutSeconds: Int = 60) : SandboxOperation
    /**
     * 启动后立即返回的 Shell 操作，不等待进程退出或服务就绪。
     * @property command 要启动的命令。
     * @property port 可选服务端口，仅用于生成预览地址，不检查监听状态。
     * @property cwd 本次进程的工作目录。
     * @property envs 仅注入本次进程的环境变量。
     * @property timeoutSeconds 启动流超时，默认 60 秒，0 表示不设期限；沙盒仍受闲置超时约束。
     */
    data class BackgroundCommand(
        val command: String,
        val port: Int? = null,
        val cwd: String? = null,
        val envs: Map<String, String> = emptyMap(),
        val timeoutSeconds: Int = 60,
    ) : SandboxOperation {
        init { require(port == null || port in 1..65535) }
    }
    /** 解释器操作；同一聊天的同一语言保留变量，envs 仅用于本次执行。 */
    data class Code(val code: String, val language: String = "python", val envs: Map<String, String> = emptyMap(), val timeoutSeconds: Int = 60) : SandboxOperation
    /** 创建或覆盖 UTF-8 文本文件，content 允许为空。 */
    data class WriteFile(val path: String, val content: String) : SandboxOperation
    /** 读取远端沙盒的 UTF-8 文本文件。 */
    data class ReadFile(val path: String) : SandboxOperation
    /** 列出目录的直接子项，path 缺省为当前目录。 */
    data class ListFiles(val path: String = ".") : SandboxOperation
}

/** 领域执行结果，不暴露 SDK、HTTP 或 ADK 类型。 */
sealed interface SandboxOutput {
    /** 已运行命令的完整状态，非零退出仍是完成的工具调用。 */
    data class Command(val command: String, val stdout: String, val stderr: String, val exitCode: Int) : SandboxOutput
    /**
     * 已收到远端进程 PID 的启动回执，不表示进程仍在运行或服务已就绪。
     * @property command 已请求启动的命令。
     * @property pid 当前沙盒内的进程标识，环境重建后失效。
     * @property previewUrl 可选 HTTPS 预览地址，仅根据沙盒和端口生成。
     */
    data class BackgroundCommand(val command: String, val pid: Int, val previewUrl: String? = null) : SandboxOutput
    /** 已运行代码的文本结果，error 表示内核运行异常。 */
    data class Code(val stdout: String, val stderr: String, val text: String, val error: String) : SandboxOutput
    /** 文本写入回执，仅包含沙盒路径。 */
    data class WrittenFile(val path: String) : SandboxOutput
    /** 读取到的 UTF-8 文本内容。 */
    data class ReadFile(val path: String, val content: String) : SandboxOutput
    /** 目录内容及其请求路径。 */
    data class Files(val path: String, val entries: List<SandboxFileEntry>) : SandboxOutput
}

/** 目录子项的显示信息，type 为 file、dir 或 unknown。 */
data class SandboxFileEntry(val name: String, val type: String)

/**
 * 工具运行状态；success 表示操作执行完成，不表示代码没有运行异常。
 * @property output 完成的操作结果，失败时为空。
 * @property error 不含凭据和服务端正文的失败说明。
 * @property partialOutput 无法完成时已经取得的输出。
 * @property environmentReset 是否在执行前重建了过期环境。
 */
data class SandboxExecutionResult(
    val success: Boolean,
    val output: SandboxOutput? = null,
    val error: String = "",
    val partialOutput: String = "",
    val environmentReset: Boolean = false,
)
