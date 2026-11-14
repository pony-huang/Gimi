package github.ponyhuang.gimi.domain.environment

/**
 * 运行组件 APK 的安装版本与通信接口版本。
 * @property code APK 安装版本号，用于判断更新方向。
 * @property name 面向用户显示的版本名称。
 * @property apiVersion 组件声明的通信接口版本；0 表示尚未声明接口版本。
 */
data class RuntimeVersion(val code: Long, val name: String, val apiVersion: Int)

/** 本地运行组件的持久状态与安装进度；错误仅使用稳定代码，避免泄漏路径与凭据。 */
data class EnvironmentStatus(
    val available: Boolean = false,
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val installing: Boolean = false,
    val progress: Int = 0,
    val failed: Boolean = false,
    val runtimeVersion: RuntimeVersion? = null,
    val bundledRuntimeVersion: RuntimeVersion? = null,
    /** false 表示已发现组件，但无法读取其环境状态，安装情况仍待确认。 */
    val connectionAvailable: Boolean = true,
    /** 组件自身报告的后台限制状态，null 表示尚无法读取。 */
    val backgroundRestricted: Boolean? = null,
) {
    val updateRequired: Boolean
        get() = runtimeVersion != null && bundledRuntimeVersion != null && runtimeVersion.apiVersion != bundledRuntimeVersion.apiVersion
    val updateAvailable: Boolean
        get() = runtimeVersion != null && bundledRuntimeVersion != null &&
            (runtimeVersion.code < bundledRuntimeVersion.code || (updateRequired && runtimeVersion.code == bundledRuntimeVersion.code))
    val runtimeUsable: Boolean
        get() = available && connectionAvailable && !updateRequired
}

/** 环境目录的直接子项；path 是 Linux 环境内的绝对路径。 */
data class EnvironmentDirectoryEntry(val name: String, val path: String, val directory: Boolean)

/** 命令结果；超时可能产生部分副作用，调用方必须避免自动重放。 */
data class EnvironmentCommandResult(val stdout: String, val stderr: String, val exitCode: Int, val timedOut: Boolean = false)

/**
 * 后台任务的实时快照。
 * @property running 任务或托管进程树仍在运行。
 * @property stdout 当前已捕获的标准输出，运行中也可读取。
 * @property stderr 当前已捕获的错误输出。
 * @property exitCode 结束后的退出码，运行时为 null。
 * @property timedOut 是否因执行时限结束。
 */
data class EnvironmentJobStatus(
    val running: Boolean,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int? = null,
    val timedOut: Boolean = false,
)

/** 共享环境管理及按聊天定位的执行契约；实现负责重新检查启用状态。 */
interface EnvironmentRepository {
    suspend fun companionApk(): String
    suspend fun status(): EnvironmentStatus
    suspend fun install()
    suspend fun setEnabled(enabled: Boolean)
    suspend fun variables(): Map<String, String>
    suspend fun saveVariables(values: Map<String, String>)
    suspend fun listDirectory(path: String): List<EnvironmentDirectoryEntry>
    suspend fun execute(session: String, command: String, timeoutMillis: Long): EnvironmentCommandResult
    suspend fun readFile(session: String, path: String): ByteArray
    suspend fun writeFile(session: String, path: String, content: ByteArray)
    /** keepAlive 保持成功启动命令的进程树存活，调用方须在授权完成或服务结束后 stopJob。 */
    suspend fun startJob(session: String, command: String, keepAlive: Boolean = false): String
    suspend fun queryJob(session: String, id: String): EnvironmentJobStatus
    suspend fun stopJob(session: String, id: String)
}

/** 校验变量名与 NUL，允许空值，持久配置与每次执行使用同一规则。 */
fun validEnvironmentVariables(values: Map<String, String>): Boolean = values.all { (name, value) ->
    name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && '\u0000' !in value
}
