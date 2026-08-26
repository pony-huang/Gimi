package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * 知乎插件 — 接入知乎开放平台（developer.zhihu.com），向 Agent 注入内容工具：
 * 站内搜索 / 全网搜索 / 热榜 / 直答。
 *
 * 鉴权凭据为个人中心的 `access_secret`，经 [configure] 注入；工具在每次执行时读取。
 */
class ZhihuPlugin(
    override val pluginId: String = "zhihu",
    override val version: Int = 1,
) : AgentPlugin {

    override val displayName: String = "知乎"

    override val config: PluginConfig = PluginConfig(
        fields = listOf(
            PluginConfigField.Text(key = KEY_ACCESS_SECRET, label = "access_secret", secret = true),
        ),
    )

    @Volatile private var accessSecret: String = ""

    private val api = ZhihuApi()

    override fun configure(values: Map<String, String>) {
        accessSecret = values[KEY_ACCESS_SECRET].orEmpty()
    }

    /** 工具列表一次构建并缓存；工具内的 accessSecret 经闭包按调用时读取，配置变更无需重建。 */
    override fun tools(): List<BaseTool> = toolList

    private val toolList: List<BaseTool> by lazy {
        listOf(
            ZhihuSearchTool(api) { accessSecret },
            ZhihuGlobalSearchTool(api) { accessSecret },
            ZhihuHotListTool(api) { accessSecret },
            ZhihuAskTool(api) { accessSecret },
        )
    }

    companion object {
        const val KEY_ACCESS_SECRET: String = "access_secret"
    }
}
