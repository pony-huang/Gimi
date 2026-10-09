package github.ponyhuang.gimi.data.logging

/** 保留同一时间戳的所有日志，同时跳过 Logcat 重启时回放的已采集记录。 */
internal class LogcatCursor(startMillis: Long) {
    private var latestMillis = startMillis
    private val counts = mutableMapOf<String, Int>()
    private var replay = mutableMapOf<String, Int>()

    fun since(): String = "${latestMillis / 1000}.${(latestMillis % 1000).toString().padStart(3, '0')}"

    fun beginSession() {
        replay = counts.toMutableMap()
    }

    fun timestamp(line: String): Long? {
        // 十进制定点转换避免浮点舍入把 1.001 秒错误截断为 1000 毫秒。
        val timestamp = line.trimStart().substringBefore(' ').toBigDecimalOrNull()
            ?.movePointRight(3)?.toLong() ?: return null
        if (timestamp < latestMillis) return null
        if (timestamp > latestMillis) {
            latestMillis = timestamp
            counts.clear()
            replay.clear()
        }
        val duplicates = replay[line] ?: 0
        if (duplicates > 0) {
            replay[line] = duplicates - 1
            return null
        }
        // 日志风暴时也限制游标内存；超限边界的少量回放由磁盘容量上限兜底。
        if (counts.size < 256 || line in counts) counts[line] = (counts[line] ?: 0) + 1
        return timestamp
    }
}
