package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.conversation.model.FileAttachment

/** 手动提交的一轮内容，附件已通过聊天附件仓储归档，可在失败后继续重试。 */
data class VoiceManualInput(
    val text: String,
    val attachments: List<FileAttachment> = emptyList(),
)

/** 提交是否被当前通话接管；未接管时调用方必须保留草稿。 */
enum class VoiceInputResult { Accepted, Unavailable, Unsupported, Failed }
