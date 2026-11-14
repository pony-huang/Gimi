package github.ponyhuang.gimi.environment.runtime

/** 任务流式输出的线程安全、有界快照；持续消费管道以避免子进程因输出过多阻塞。 */
internal class RuntimeJobOutput {
    private val content = StringBuilder()

    @Synchronized
    fun append(buffer: CharArray, count: Int) {
        val remaining = 30_000 - content.length
        if (remaining > 0) content.appendRange(buffer, 0, minOf(count, remaining))
    }

    @Synchronized
    fun snapshot(): String = content.toString()
}
