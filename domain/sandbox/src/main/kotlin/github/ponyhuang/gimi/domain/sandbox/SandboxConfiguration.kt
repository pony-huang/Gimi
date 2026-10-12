package github.ponyhuang.gimi.domain.sandbox

/**
 * 沙盒连接和创建参数；敏感字段只由安全存储及执行适配器消费。
 * @property domain E2B 基础域名，不包含协议、路径或端口。
 * @property apiKey 管理 API 密钥，不传给模型或远端执行进程。
 * @property template 支持代码解释器的模板名称或 ID。
 * @property idleTimeoutSeconds 操作完成后的环境存活窗口，单位秒。
 * @property envs 创建沙盒时设置的环境变量。
 */
data class SandboxConnection(
    val domain: String = "e2b.app",
    val apiKey: String = "",
    val template: String = "code-interpreter-v1",
    val idleTimeoutSeconds: Int = 300,
    val envs: Map<String, String> = emptyMap(),
) {
    override fun toString(): String = "SandboxConnection(domain=$domain, template=$template, credentials=<redacted>)"
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
enum class SandboxSettingField { DOMAIN, API_KEY, TEMPLATE, IDLE_TIMEOUT, ENVIRONMENT }

/** 对连接设置执行纯领域校验；不检查联网状态或付费账户限制。 */
fun validateSandboxConnection(connection: SandboxConnection): Set<SandboxSettingField> = buildSet {
    val label = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?")
    if (connection.domain.length !in 1..253 || !connection.domain.split('.').all(label::matches)) {
        add(SandboxSettingField.DOMAIN)
    }
    if (connection.apiKey.isBlank()) add(SandboxSettingField.API_KEY)
    if (connection.template.isBlank()) add(SandboxSettingField.TEMPLATE)
    if (connection.idleTimeoutSeconds <= 0) add(SandboxSettingField.IDLE_TIMEOUT)
    if (connection.envs.any { (name, value) -> !isSandboxEnvironmentName(name) || '\u0000' in value }) {
        add(SandboxSettingField.ENVIRONMENT)
    }
}

/** Linux 环境变量名称不能留空、包含赋值分隔符或 NUL；大小写敏感。 */
fun isSandboxEnvironmentName(name: String): Boolean = name.isNotBlank() && '=' !in name && '\u0000' !in name
