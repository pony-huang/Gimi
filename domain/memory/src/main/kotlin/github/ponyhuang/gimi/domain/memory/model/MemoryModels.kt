package github.ponyhuang.gimi.domain.memory.model

/**
 * 当前记忆后端配置。
 *
 * @property memoryEnabled 记忆总开关；关闭后不保存也不召回任何记忆。
 * @property mem0Enabled 是否使用 Mem0 Platform；关闭时使用设备本地记忆。
 * @property apiKey Mem0 API Key，仅允许在安全存储与网络边界内使用。
 */
data class MemoryConfiguration(
    val memoryEnabled: Boolean = true,
    val mem0Enabled: Boolean = false,
    val apiKey: String = "",
)

/** Mem0 运行期可能失败的操作类别，用于向用户提供脱敏反馈。 */
enum class MemoryOperation {
    SEARCH,
    WRITE,
}

/**
 * 一次需要展示给用户的记忆运行错误。
 *
 * @property operation 失败的操作类别；不携带服务端正文或凭据。
 */
data class MemoryRuntimeFailure(
    val operation: MemoryOperation,
)
