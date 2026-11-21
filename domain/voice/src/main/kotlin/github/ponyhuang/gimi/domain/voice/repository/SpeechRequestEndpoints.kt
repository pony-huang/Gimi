package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints

/** 百炼（DashScope）语音服务的模板 ID；:data:modelaccess 的语音适配器共用同一标识。 */
const val QWEN_SPEECH_SERVICE_ID = "qwen"

/**
 * 语音请求接入地址的统一解析点。
 *
 * 端点由厂商自有的连接在模板里写死官方地址（保存时回贴），这里只负责百炼业务空间
 * ID 参与的专属域名改写；其余连接沿用用户/模板配置的端点。适配器只接收解析好的
 * 具体地址并按 [EndpointStrategy.VendorOwned] 声明策略，测试直接传具体地址即可覆盖。
 */
fun speechRequestBaseUrl(
    templateId: String?,
    configuredEndpoint: String,
    workspaceId: String?,
): String = when (templateId) {
    QWEN_SPEECH_SERVICE_ID -> SpeechEndpoints.qwenInferenceUrl(workspaceId)
    else -> configuredEndpoint
}
