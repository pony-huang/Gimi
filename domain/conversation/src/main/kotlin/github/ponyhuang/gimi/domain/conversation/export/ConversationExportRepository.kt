package github.ponyhuang.gimi.domain.conversation.export

/**
 * 单个待导出会话的目标：userId 标识存储分区（助手/Chatbot），sessionId 为分区内的会话 ID。
 * @property userId 会话所属分区（ADK 用户 id），读取归档时必须与写入时的分区一致。
 * @property sessionId 分区内的会话 ID。
 */
data class ConversationExportSession(
    val userId: String,
    val sessionId: String,
)

/**
 * 用户选取保存位置前冻结的导出范围与文件命名，不携带 SDK 或 Android 类型。
 * 每次请求只导出一个会话；助手与 Chatbot 各自独立导出，不做跨分区打包。
 * @property userId 会话所属存储分区。
 * @property sessionId 要导出的会话 ID。
 * @property timestamp 导出请求创建时的本地时间，用于文件命名。
 * @property mimeType 导出文件类型（原始会话归档 JSONL）。
 */
data class ConversationExportRequest(
    val userId: String,
    val sessionId: String,
    val timestamp: String,
    val fileName: String,
    val mimeType: String,
)

/** 原始会话读取、JSONL 编码和文档写入的持久化边界；读取失败必须向调用方报告。 */
interface ConversationExportRepository {
    /** 冻结导出目标与文件命名。 */
    suspend fun prepare(target: ConversationExportSession): ConversationExportRequest

    /** 先生成完整归档再写入目标文档，取消与失败不得报告为成功。 */
    suspend fun export(request: ConversationExportRequest, destination: String)
}
