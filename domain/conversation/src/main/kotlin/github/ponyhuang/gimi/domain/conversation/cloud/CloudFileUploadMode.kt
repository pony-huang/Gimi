package github.ponyhuang.gimi.domain.conversation.cloud

import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting

/** 上传偏好可跨协议保留，但仅 Chat Completions 且开关开启时进入云文件流程。 */
fun LLMModelSetting.usesCloudFileUpload(): Boolean =
    isFileUploadEnabled && apiProtocol == ApiProtocol.OpenaiChatcompletions
