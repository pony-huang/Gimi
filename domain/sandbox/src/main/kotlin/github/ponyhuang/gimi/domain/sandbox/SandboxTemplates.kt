package github.ponyhuang.gimi.domain.sandbox

/**
 * 当前凭据所属项目的模板摘要，不携带 SDK 类型或连接凭据。
 * @property templateId 创建沙盒时使用的模板 ID。
 * @property names 模板名称，保留命名空间；为空时以 ID 展示。
 * @property buildStatus 最近构建状态，缺失或新状态均允许展示且不限制选择。
 */
data class SandboxTemplate(val templateId: String, val names: List<String>, val buildStatus: String?)

/** 使用设置草稿查询模板，不提交设置、不创建远端执行环境。 */
interface SandboxTemplateRepository {
    suspend fun listTemplates(domain: String, apiKey: String): List<SandboxTemplate>
}

/** 供页面映射本地化提示的查询失败类型。 */
enum class SandboxTemplateFailure { AUTHENTICATION, RATE_LIMIT, NETWORK, PROTOCOL, UNAVAILABLE }

/** 模板查询边界错误，不保留服务器正文、请求 URL 或凭据。 */
class SandboxTemplateException(val failure: SandboxTemplateFailure) : Exception("Template query failed: $failure")
