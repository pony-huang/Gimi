package github.ponyhuang.gimi.feature.chat.realtime

import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.modelcatalog.model.MultimodalCapabilities
import github.ponyhuang.gimi.feature.chat.DraftUploadState
import github.ponyhuang.gimi.feature.chat.MessageData

/** 通话输入草稿与提交快照；挂断仅关闭编辑，成功接管后才清理草稿。 */
data class VoiceComposerUiState(
    val editing: Boolean = false,
    val draft: MessageData = MessageData(),
    val submitting: Boolean = false,
    val error: VoiceComposerError? = null,
    /** 每次操作提示递增，连续发生相同错误时也能分别展示 Toast。 */
    val noticeRevision: Long = 0,
    val capabilities: MultimodalCapabilities = MultimodalCapabilities(null, null, null),
    val fileUploadEnabled: Boolean = false,
    /** 当前输入模型所属厂商模板，供共享附件抽屉显示品牌图标。 */
    val vendorTemplateId: String? = null,
    val uploadedFiles: List<UploadedFileEntry> = emptyList(),
    val uploads: Map<String, DraftUploadState> = emptyMap(),
)

/** 输入区可翻译的操作反馈，底层异常不直接展示。 */
enum class VoiceComposerError { StartCall, Unavailable, Unsupported, Attachment, Submit, UploadedFilesRefresh, UploadedFileDelete }
