package github.ponyhuang.gimi.plugin.v2ex

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * V2EX 插件 — 接入 V2EX 公开 API（匿名只读），向 Agent 注入内容工具：
 * 热榜 / 最新 / 节点主题 / 主题详情 / 回复 / 节点 / 用户。
 *
 * 可选配置 [KEY_BASE_URL] 覆盖 API 基址（默认 [V2exApi.DEFAULT_BASE_URL]），
 * 用于受限网络下切换到镜像。
 */
class V2exPlugin(
    override val pluginId: String = "v2ex",
    override val version: Int = 1,
) : AgentPlugin {

    override val displayName: String = "V2EX"
    override val toolCount: Int = 7

    override val config: PluginConfig = PluginConfig(
        fields = listOf(
            PluginConfigField.Text(
                key = KEY_BASE_URL,
                label = "API 地址",
                defaultValue = V2exApi.DEFAULT_BASE_URL,
            ),
        ),
    )

    private val api = V2exApi()

    override fun configure(values: Map<String, String>) {
        api.baseUrl = values[KEY_BASE_URL]?.takeIf(String::isNotBlank) ?: V2exApi.DEFAULT_BASE_URL
    }

    /** 当前生效的 API 基址（供测试断言 configure 行为）。 */
    fun apiBaseUrl(): String = api.baseUrl

    override fun toolSets(): List<Toolset> = toolSets

    private val toolSets: List<Toolset> by lazy { listOf(V2exToolset { toolList }) }

    private val toolList: List<BaseTool> by lazy {
        listOf(
            V2exHotTopicsTool(api),
            V2exLatestTopicsTool(api),
            V2exNodeTopicsTool(api),
            V2exTopicTool(api),
            V2exTopicRepliesTool(api),
            V2exNodeInfoTool(api),
            V2exMemberInfoTool(api),
        )
    }

    companion object {
        const val KEY_BASE_URL: String = "base_url"
    }
}
