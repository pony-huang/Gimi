package github.ponyhuang.gimi.domain.conversation.export

/**
 * 用户选取保存位置前冻结的导出范围与文件命名，不携带 SDK 或 Android 类型。
 * @property sessionIds 本次要读取的会话 ID；每个会话生成一个 JSONL 文件。
 * @property timestamp 导出请求创建时的本地时间，用于文件与 ZIP 条目命名。
 * @property allSessions 是否由全部导出发起；即使只有一个会话也需检查全局任务。
 */
data class ConversationExportRequest(
    val sessionIds: List<String>,
    val timestamp: String,
    val fileName: String,
    val mimeType: String,
    val allSessions: Boolean,
)

/** 原始会话读取、JSONL/ZIP 编码和文档写入的持久化边界；读取失败必须向调用方报告。 */
interface ConversationExportRepository {
    /** null 表示没有可导出的会话，非空 ID 表示只导出指定会话。 */
    suspend fun prepare(sessionId: String?): ConversationExportRequest?

    /** 先生成完整归档再写入目标文档，取消与失败不得报告为成功。 */
    suspend fun export(request: ConversationExportRequest, destination: String)
}
