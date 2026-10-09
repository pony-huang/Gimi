package github.ponyhuang.gimi.data.logging

import github.ponyhuang.gimi.domain.logging.LogSnapshot
import java.io.File
import java.io.IOException
import java.util.ArrayDeque

/** 单进程串行日志存储：分片轮转、逐条 24 小时过期、总字节数上限和有界查看。 */
internal class RollingLogStore(
    private val directory: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = 4L * 1024 * 1024,
    private val chunkBytes: Int = 256 * 1024,
    private val previewBytes: Int = 256 * 1024,
) {
    private var lastCleanup = Long.MIN_VALUE
    private var sequence = 0L

    init {
        require(chunkBytes >= 1024 && maxBytes >= chunkBytes && previewBytes >= chunkBytes)
    }

    @Synchronized
    fun append(message: String, timestamp: Long = now()) {
        val current = now()
        if (timestamp <= current - RETENTION_MILLIS || timestamp > current) return
        if (lastCleanup == Long.MIN_VALUE || current - lastCleanup >= 60_000 || current < lastCleanup) {
            cleanup()
        }
        ensureDirectory()
        // 单条截断避免异常堆栈/SDK 输出绕过分片限制；换行转义保证记录的过期边界可解析。
        val text = LogRedactor.redact(message.take(minOf(2_000, (chunkBytes - 40) / 4))).replace("\r", "\\r").replace("\n", "\\n")
        val record = "$timestamp\t$text\n".toByteArray(Charsets.UTF_8)
        val files = chunks()
        val last = files.lastOrNull()
        val file = if (last != null && last.length() + record.size <= chunkBytes) last else {
            sequence = maxOf(sequence, files.lastOrNull()?.nameWithoutExtension?.toLongOrNull() ?: 0L) + 1
            File(directory, "${sequence.toString().padStart(20, '0')}.log")
        }
        // 先删最旧分片再追加，磁盘写入完成后也不会超过总容量上限。
        var total = files.sumOf { it.length() }
        for (old in files) {
            if (total + record.size <= maxBytes) break
            total -= old.length()
            delete(old)
        }
        file.appendBytes(record)
    }

    @Synchronized
    fun cleanup() {
        ensureDirectory()
        directory.listFiles()?.filter { it.extension == "tmp" }?.forEach(::delete)
        val current = now()
        for (file in chunks()) {
            // 每次最多加载一个分片；时间戳损坏的记录直接丢弃。
            val original = file.readLines(Charsets.UTF_8)
            val retained = original.filter { line ->
                val timestamp = line.substringBefore('\t').toLongOrNull()
                timestamp != null && timestamp > current - RETENTION_MILLIS && timestamp <= current
            }
            if (retained.isEmpty()) {
                delete(file)
            } else if (retained.size != original.size) {
                val temporary = File(directory, "${file.name}.tmp")
                temporary.writeText(retained.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
                if (!temporary.renameTo(file)) throw IOException("Cannot replace log chunk")
            }
        }
        val files = chunks()
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            total -= file.length()
            delete(file)
        }
        lastCleanup = current
    }

    @Synchronized
    fun read(): LogSnapshot {
        cleanup()
        val recent = ArrayDeque<String>()
        var bytes = 0
        var truncated = false
        for (file in chunks()) {
            file.forEachLine(Charsets.UTF_8) { line ->
                val text = line.substringAfter('\t') + "\n"
                recent.addLast(text)
                bytes += text.toByteArray(Charsets.UTF_8).size
                while (bytes > previewBytes && recent.isNotEmpty()) {
                    bytes -= recent.removeFirst().toByteArray(Charsets.UTF_8).size
                    truncated = true
                }
            }
        }
        return LogSnapshot(recent.joinToString(""), truncated)
    }

    @Synchronized
    fun exportTo(writeLine: (String) -> Unit) {
        cleanup()
        for (file in chunks()) {
            file.forEachLine(Charsets.UTF_8) { writeLine(it.substringAfter('\t') + "\n") }
        }
    }

    private fun chunks(): List<File> = directory.listFiles()
        ?.filter { it.extension == "log" && it.nameWithoutExtension.toLongOrNull() != null }
        ?.sortedBy { it.name }.orEmpty()

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create log directory")
    }

    private fun delete(file: File) {
        if (!file.delete() && file.exists()) throw IOException("Cannot delete log chunk")
    }

    companion object {
        const val RETENTION_MILLIS = 24L * 60 * 60 * 1000
    }
}

/** 常见凭据在落盘前脱敏；不主动记录会话正文或网络请求体。 */
internal object LogRedactor {
    private val bearer = Regex("(?i)((?:Bearer|Basic)\\s+)[^\\s,;\"]+")
    private val secret = Regex(
        "(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|authorization|password)\"?\\s*[:=]\\s*\"?)[^\\s,;\"}]+",
    )

    fun redact(message: String): String = secret.replace(bearer.replace(message, "$1[REDACTED]"), "$1[REDACTED]")
}
