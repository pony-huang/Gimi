package github.ponyhuang.gimi.environment.runtime

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue

/** 有界的输入队列保持写入顺序，避免不读取 stdin 的命令阻塞 Binder 调用。 */
internal class RuntimeJobInput {
    private val queue = ArrayBlockingQueue<ByteArray>(16)
    private var closed = false

    @Synchronized
    fun enqueue(chars: String) {
        require(chars.length in 1..4096)
        val bytes = chars.toByteArray()
        require(bytes.size <= 4096)
        check(!closed && queue.offer(bytes)) { "Job input is closed or full" }
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        queue.clear()
        queue.offer(byteArrayOf())
    }

    fun writeTo(output: OutputStream) {
        try {
            output.use {
                while (true) {
                    val bytes = queue.take()
                    if (bytes.isEmpty()) break
                    it.write(bytes)
                    it.flush()
                }
            }
        } catch (_: IOException) {
            // 命令可以在继续运行时主动关闭 stdin。
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            close()
        }
    }
}
