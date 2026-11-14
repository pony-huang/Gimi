package github.ponyhuang.gimi.data.modelaccess.shared

import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints
import github.ponyhuang.gimi.data.modelaccess.qwen.speech.DASHSCOPE_SERVICE_ID

/**
 * 语音请求接入地址的统一解析点。
 *
 * 端点由厂商自有的连接在模板里写死官方地址（保存时回贴），这里只负责百炼业务空间
 * ID 参与的专属域名改写；其余连接沿用用户/模板配置的端点。适配器只接收解析好的
 * 具体地址并按 [github.ponyhuang.gimi.domain.voice.repository.EndpointStrategy.VendorOwned]
 * 声明策略，测试直接传具体地址即可覆盖。
 */
fun speechRequestBaseUrl(
    templateId: String?,
    configuredEndpoint: String,
    workspaceId: String?,
): String = when (templateId) {
    DASHSCOPE_SERVICE_ID -> SpeechEndpoints.qwenInferenceUrl(workspaceId)
    else -> configuredEndpoint
}
