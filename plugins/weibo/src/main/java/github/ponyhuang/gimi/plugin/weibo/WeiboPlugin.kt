package github.ponyhuang.gimi.plugin.weibo

import android.content.Context
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginApi
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * 微博插件入口；实现 host 侧 [AgentPlugin] SPI。
 *
 * 真实业务逻辑都在 [WeiboApi] 与各 `WeiboTool` 类中，本类只负责：
 *  - 把宿主持久化的配置项写回 [WeiboApi]；
 *  - 把凭据变更同步到 token 缓存（凭据一变旧 token 立刻作废）；
 *  - 按 [PluginApi.VERSION] 版本暴露工具集。
 *
 * 配置项仅声明，不在 [configure] 抛错——配置错误时只清 token、下一次工具调用再回
 * [WeiboApi.MISSING_CREDENTIALS_MESSAGE]，让用户看见。
 */
class WeiboPlugin : AgentPlugin {

    override val pluginId: String = PLUGIN_ID

    override val displayName: String = DISPLAY_NAME

    override val version: Int = VERSION

    override val toolCount: Int = EXPECTED_TOOL_COUNT

    override val apiVersion: Int = PluginApi.VERSION

    private val api: WeiboApi = WeiboApi()

    override val config: PluginConfig = PluginConfig(
        fields = listOf(
            PluginConfigField.Text(
                key = KEY_BASE_URL,
                label = "接口基址",
                defaultValue = WeiboApi.DEFAULT_BASE_URL,
                secret = false,
            ),
            PluginConfigField.Text(
                key = KEY_APP_ID,
                label = "App ID",
                defaultValue = "",
                secret = false,
            ),
            // App Secret 是凭据，UI 端按 secret 控件渲染（密码样式 / 不回显）。
            PluginConfigField.Text(
                key = KEY_APP_SECRET,
                label = "App Secret",
                defaultValue = "",
                secret = true,
            ),
        ),
    )

    override fun toolSets(): List<Toolset> = listOf(WeiboToolset(api))

    /**
     * 宿主持久化配置变更后回调：把凭据重新写入 [WeiboApi]，必要时清掉旧 token。
     */
    override fun configure(values: Map<String, String>) {
        val baseUrl = values[KEY_BASE_URL]?.takeIf(String::isNotBlank) ?: WeiboApi.DEFAULT_BASE_URL
        val appId = values[KEY_APP_ID].orEmpty()
        val appSecret = values[KEY_APP_SECRET].orEmpty()
        api.configure(baseUrl, appId, appSecret)
    }

    override fun onAttach(context: Context) = Unit

    companion object {
        const val PLUGIN_ID: String = "weibo"

        const val DISPLAY_NAME: String = "微博"

        const val VERSION: Int = 1

        /** 与 [WeiboToolset] 注册顺序一致，供单元测试断言。 */
        const val EXPECTED_TOOL_COUNT: Int = 11

        const val KEY_BASE_URL: String = "base_url"
        const val KEY_APP_ID: String = "app_id"
        const val KEY_APP_SECRET: String = "app_secret"
    }
}