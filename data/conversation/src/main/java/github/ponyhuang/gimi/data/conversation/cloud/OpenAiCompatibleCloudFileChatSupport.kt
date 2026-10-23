package github.ponyhuang.gimi.data.conversation.cloud

import github.ponyhuang.gimi.domain.conversation.cloud.CloudFileChatSupport
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting

/** 用户指定按 OpenAI 协议默认支持 PDF 文件引用，不设厂商或模型白名单；服务拒绝由请求反馈。 */
internal class OpenAiCompatibleCloudFileChatSupport : CloudFileChatSupport {
    override fun supports(service: LLMModelSetting, modelId: String, mimeType: String): Boolean =
        service.apiProtocol == ApiProtocol.OpenaiChatcompletions && mimeType == "application/pdf"
}
