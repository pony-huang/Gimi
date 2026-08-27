package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * 知乎插件 — 接入知乎开放平台（developer.zhihu.com），向 Agent 注入内容工具：
 * 站内搜索 / 全网搜索 / 热榜 / 直答，以及额度查询 / 知识库 / 异步任务（PDF 解析、PPT 生成）。
 *
 * 鉴权凭据为个人中心的 `access_secret`，经 [configure] 注入；工具在每次执行时读取。
 */
class ZhihuPlugin(
    override val pluginId: String = "zhihu",
    override val version: Int = 2,
) : AgentPlugin {

    override val displayName: String = "知乎"
    override val toolCount: Int = 12

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

    /** 工具集与工具列表均复用实例；accessSecret 仍由工具在调用时通过闭包读取。 */
    override fun toolSets(): List<Toolset> = toolSets

    private val toolSets: List<Toolset> by lazy { listOf(ZhihuToolset { toolList }) }

    private val toolList: List<BaseTool> by lazy {
        listOf(
            ZhihuSearchTool(api) { accessSecret },
            ZhihuGlobalSearchTool(api) { accessSecret },
            ZhihuHotListTool(api) { accessSecret },
            ZhihuAskTool(api) { accessSecret },
            ZhihuQuotaTool(api) { accessSecret },
            ZhihuKnowledgeBasesTool(api) { accessSecret },
            ZhihuKnowledgeItemsTool(api) { accessSecret },
            ZhihuKnowledgeSearchTool(api) { accessSecret },
            ZhihuKnowledgeUploadTool(api) { accessSecret },
            ZhihuPdfParseTool(api) { accessSecret },
            ZhihuPptGenerateTool(api) { accessSecret },
            ZhihuTaskStatusTool(api) { accessSecret },
        )
    }

    companion object {
        const val KEY_ACCESS_SECRET: String = "access_secret"
    }
}
