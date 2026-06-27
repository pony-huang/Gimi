package github.ponyhuang.asssistantai.model

import java.util.UUID

/**
 * 会话数据模型。
 *
 * @param id          唯一标识
 * @param title       对话标题（显示在列表中）
 * @param lastMessage 最后一条消息预览
 * @param timestamp   最后更新时间（毫秒时间戳），用于排序
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastMessage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
