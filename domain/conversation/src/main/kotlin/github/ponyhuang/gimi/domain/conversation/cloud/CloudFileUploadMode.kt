package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting

/** 上传偏好可跨协议保留，但仅 Chat Completions 且开关开启时进入云文件流程。 */
fun LLMModelSetting.usesCloudFileUpload(): Boolean =
    isFileUploadEnabled && apiProtocol == ApiProtocol.OpenaiChatcompletions

/** 云模式只接管文档和既有云引用；图片、音频保留模型的原生附件路径。 */
fun LLMModelSetting.usesCloudFileUploadFor(drafts: List<DraftAttachment>): Boolean =
    usesCloudFileUpload() && drafts.any { it.category == AttachmentCategory.DOCUMENT || it.cloudReference != null }
