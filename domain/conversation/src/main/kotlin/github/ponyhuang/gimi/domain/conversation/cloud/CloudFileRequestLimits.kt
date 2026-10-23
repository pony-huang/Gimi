package github.ponyhuang.gimi.domain.conversation.cloud

/** Chat Completions 的文件请求额度，发送前和历史重放共用，计算时避免溢出。 */
object CloudFileRequestLimits {
    const val MAX_BYTES: Long = 50L * 1024 * 1024
    fun allows(sizes: Iterable<Long>): Boolean {
        var total = 0L
        for (size in sizes) {
            if (size <= 0 || size > MAX_BYTES - total) return false
            total += size
        }
        return true
    }
}
