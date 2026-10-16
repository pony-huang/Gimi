package github.ponyhuang.gimi.domain.sandbox

import java.net.URI
import java.net.URISyntaxException

/**
 * 沙盒连接和创建参数；敏感字段只由安全存储及执行适配器消费。
 * @property apiUrl E2B 管理 API 的完整 HTTP/HTTPS 地址，可包含端口和路径前缀。
 * @property sandboxUrl 可选沙箱代理地址；非空时同时路由命令、文件和代码解释器请求。
 * @property apiKey 管理 API 密钥，不传给模型或远端执行进程。
 * @property template 支持代码解释器的模板名称或 ID。
 * @property idleTimeoutSeconds 操作完成后的环境存活窗口，单位秒。
 * @property envs 创建沙盒时设置的环境变量。
 */
data class SandboxConnection(
    val apiUrl: String = "https://api.e2b.app",
    val apiKey: String = "",
    val template: String = "code-interpreter-v1",
    val idleTimeoutSeconds: Int = 300,
    val envs: Map<String, String> = emptyMap(),
    val sandboxUrl: String = "",
) {
    override fun toString(): String = "SandboxConnection(apiUrl=$apiUrl, template=$template, credentials=<redacted>)"
}

/**
 * 用户保存的沙盒设置；开关与连接草稿分开提交。
 * @property enabled 用户是否启用 E2B；缺少连接凭据时仍可保持开启以完成配置。
 * @property connection 最近成功保存的连接参数。
 */
data class SandboxConfiguration(
    val enabled: Boolean = false,
    val connection: SandboxConnection = SandboxConnection(),
) {
    val isConfigured: Boolean get() = validateSandboxConnection(connection).isEmpty()
    val isReady: Boolean get() = enabled && isConfigured
}

/** 可在设置页定位的配置校验字段。 */
enum class SandboxSettingField { API_URL, SANDBOX_URL, API_KEY, TEMPLATE, IDLE_TIMEOUT, ENVIRONMENT }

/** 对连接设置执行纯领域校验；不检查联网状态或付费账户限制。 */
fun validateSandboxConnection(connection: SandboxConnection): Set<SandboxSettingField> = buildSet {
    if (!isSandboxEndpoint(connection.apiUrl)) add(SandboxSettingField.API_URL)
    if (connection.sandboxUrl.isNotEmpty() && !isSandboxEndpoint(connection.sandboxUrl)) add(SandboxSettingField.SANDBOX_URL)
    if (connection.apiKey.isBlank()) add(SandboxSettingField.API_KEY)
    if (connection.template.isBlank()) add(SandboxSettingField.TEMPLATE)
    if (connection.idleTimeoutSeconds <= 0) add(SandboxSettingField.IDLE_TIMEOUT)
    if (connection.envs.any { (name, value) -> !isSandboxEnvironmentName(name) || '\u0000' in value }) {
        add(SandboxSettingField.ENVIRONMENT)
    }
}

/** 地址必须显式声明协议；凭据只通过独立密钥字段传递，不接受 URL 内的认证、查询或片段。 */
fun isSandboxEndpoint(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() &&
        (uri.port == -1 || uri.port in 1..65535) && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && !value.contains('\\')
} catch (_: URISyntaxException) {
    false
}

/** Linux 环境变量名称不能留空、包含赋值分隔符或 NUL；大小写敏感。 */
fun isSandboxEnvironmentName(name: String): Boolean = name.isNotBlank() && '=' !in name && '\u0000' !in name
