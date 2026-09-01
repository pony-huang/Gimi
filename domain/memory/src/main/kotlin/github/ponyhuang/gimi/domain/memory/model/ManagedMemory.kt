package github.ponyhuang.gimi.domain.memory.model

import java.time.Instant

/** 客户端可展示和管理的一条 Mem0 云端记忆。 */
data class ManagedMemory(
    /** Mem0 分配的唯一记忆标识。 */
    val id: String,
    /** 用户可阅读的记忆内容。 */
    val text: String,
    /** 记忆创建时间。 */
    val createdAt: Instant?,
    /** 记忆最近更新时间。 */
    val updatedAt: Instant?,
)

/** 一页 Mem0 云端记忆查询结果。 */
data class ManagedMemoryPage(
    /** 当前页的记忆。 */
    val memories: List<ManagedMemory>,
    /** 是否还有后续页可加载。 */
    val hasNextPage: Boolean,
)

/** 用户提交给 Mem0 的记忆质量反馈。 */
enum class ManagedMemoryFeedback {
    POSITIVE,
    NEGATIVE,
}
