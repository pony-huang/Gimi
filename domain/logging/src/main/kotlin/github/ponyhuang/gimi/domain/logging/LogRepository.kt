package github.ponyhuang.gimi.domain.logging

/** 日志查看快照；content 为最近记录，truncated 表示更早的保留记录仅在导出中提供。 */
data class LogSnapshot(val content: String, val truncated: Boolean = false)

/** 应用诊断日志的读取和导出边界；实现负责过滤过期记录和切换 IO 线程。 */
interface LogRepository {
    suspend fun read(): LogSnapshot

    /** 将仍保留的完整日志写入用户选择的文档；destination 为系统文档 URI 字符串。 */
    suspend fun export(destination: String)
}
