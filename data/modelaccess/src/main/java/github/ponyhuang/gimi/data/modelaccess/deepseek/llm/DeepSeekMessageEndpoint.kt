package github.ponyhuang.gimi.data.modelaccess.deepseek.llm

import github.ponyhuang.gimi.data.modelaccess.ModelRuntimeMetadata
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import java.net.URI

/**
 * 官方 DeepSeek /anthropic 端点判定；全部 DeepSeek Messages 协议特例
 * （工具引用加载、会话标题、latest_reminder）共用此门控。
 *
 * 第三方代理可能不识别厂商扩展字段，因此仅官方端点启用特例，代理回落通用协议。
 */
fun isOfficialDeepSeekAnthropicEndpoint(runtime: ModelRuntimeMetadata?): Boolean {
    if (runtime?.baseType != ApiProtocol.Anthropic || runtime.templateId != "deepseek") return false
    val endpoint = try { URI(runtime.fullBaseUrl) } catch (_: java.net.URISyntaxException) { return false }
    return endpoint.scheme == "https" && endpoint.host == "api.deepseek.com" &&
        endpoint.port in listOf(-1, 443) && endpoint.userInfo == null && endpoint.query == null &&
        endpoint.fragment == null && endpoint.path.trimEnd('/') in listOf("/anthropic", "/anthropic/v1")
}
