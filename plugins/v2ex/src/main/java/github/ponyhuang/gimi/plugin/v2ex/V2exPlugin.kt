package github.ponyhuang.gimi.plugin.v2ex

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField
import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import github.ponyhuang.gimi.pluginapi.PluginActionCallbackRequest

/**
 * V2EX 插件 — 接入 V2EX API 2.0（Beta），全部工具经 Personal Access Token 认证。
 *
 * 配置字段：token（PAT，secret）+ 可选 base_url（默认 [V2exApi.DEFAULT_BASE_URL]，
 * 用于受限网络下切换到镜像）。工具面覆盖 v2 全部接口：提醒/删除提醒、自己的
 * Profile、令牌查询/创建、节点/节点主题/主题详情/回复（读），置顶/boost（写）。
 */
class V2exPlugin(
    override val pluginId: String = "v2ex",
    override val version: Int = 1,
) : AgentPlugin {

    override val displayName: String = "V2EX"
    override val toolCount: Int = 11

    override val config: PluginConfig = PluginConfig(
        fields = listOf(
            PluginConfigField.Text(
                key = KEY_TOKEN,
                label = "Personal Access Token",
                secret = true,
            ),
            PluginConfigField.Text(
                key = KEY_BASE_URL,
                label = "API 地址",
                defaultValue = V2exApi.DEFAULT_BASE_URL,
            ),
        ),
        actions = listOf(
            PluginConfigAction(id = ACTION_TOKEN_SETTINGS, label = "前往 V2EX 令牌设置 ↗"),
        ),
    )

    override suspend fun runConfigAction(actionId: String): PluginConfigActionExecution = when (actionId) {
        ACTION_TOKEN_SETTINGS -> PluginConfigActionExecution.AwaitingCallback(
            PluginActionCallbackRequest(
                handlerId = "external_url",
                parameters = mapOf("url" to "https://www.v2ex.com/settings/tokens"),
            ),
        )
        else -> super.runConfigAction(actionId)
    }

    private val api = V2exApi()

    override fun configure(values: Map<String, String>) {
        api.token = values[KEY_TOKEN].orEmpty()
        api.baseUrl = values[KEY_BASE_URL]?.takeIf(String::isNotBlank)
            ?.takeIf(::isAllowedBaseUrl)
            ?: V2exApi.DEFAULT_BASE_URL
    }

    /**
     * 基址白名单：Bearer token 会发往该主机，只允许官方域的 https 地址，
     * 防止配置把 token 引到任意主机。
     */
    private fun isAllowedBaseUrl(value: String): Boolean {
        // java.net.URI 而非 android.net.Uri：JVM 单测环境可直接解析。
        val uri = try {
            java.net.URI(value)
        } catch (_: java.net.URISyntaxException) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme == "https" && host in ALLOWED_BASE_HOSTS
    }

    /** 当前生效的 token（供测试断言 configure 行为）。 */
    fun apiToken(): String = api.token

    /** 当前生效的 API 基址（供测试断言 configure 行为）。 */
    fun apiBaseUrl(): String = api.baseUrl

    override fun toolSets(): List<Toolset> = toolSets

    private val toolSets: List<Toolset> by lazy { listOf(V2exToolset { toolList }) }

    private val toolList: List<BaseTool> by lazy {
        listOf(
            V2exNotificationsTool(api),
            V2exNotificationDeleteTool(api),
            V2exMeTool(api),
            V2exTokenTool(api),
            V2exTokenCreateTool(api),
            V2exNodeTool(api),
            V2exNodeTopicsTool(api),
            V2exTopicTool(api),
            V2exTopicRepliesTool(api),
            V2exTopicSetStickyTool(api),
            V2exTopicBoostTool(api),
        )
    }

    companion object {
        const val KEY_TOKEN: String = "token"
        const val KEY_BASE_URL: String = "base_url"
        const val ACTION_TOKEN_SETTINGS: String = "link:token_settings"

        /** token 只发往官方域。 */
        val ALLOWED_BASE_HOSTS: Set<String> = setOf("v2ex.com", "www.v2ex.com")
    }
}
