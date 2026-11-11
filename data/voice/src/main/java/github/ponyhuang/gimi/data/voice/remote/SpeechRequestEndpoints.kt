package github.ponyhuang.gimi.data.voice.remote

import github.ponyhuang.gimi.domain.modelcatalog.model.SpeechEndpoints

/**
 * 语音请求接入地址的统一解析点。
 *
 * 端点由厂商自有的服务在这里写死官方地址（业务空间 ID 参与专属域名改写），用户不再
 * 自定义；端点可配置的服务沿用用户配置的基址。适配器只接收解析好的具体地址并按
 * [EndpointStrategy.VendorOwned] 声明策略，测试直接传具体地址即可覆盖。
 */
internal fun speechRequestBaseUrl(
    serviceId: String,
    configuredBaseUrl: String,
    workspaceId: String?,
): String = when (serviceId) {
    DASHSCOPE_SERVICE_ID -> SpeechEndpoints.qwenInferenceUrl(workspaceId)
    OPENAI_SPEECH_SERVICE_ID -> SpeechEndpoints.OPENAI_SPEECH_BASE_URL
    else -> configuredBaseUrl
}
