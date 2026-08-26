package github.ponyhuang.gimi.plugin.xiaohongshu

import android.content.Context
import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.BrowserAuthRequest
import github.ponyhuang.gimi.pluginapi.PluginActionResult
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigAction

/** 直接通过 Android 浏览器会话操作小红书网页的第一方插件。 */
class XiaohongshuPlugin internal constructor(
    injectedService: XiaohongshuService? = null,
) : AgentPlugin {

    constructor() : this(null)

    override val pluginId: String = "xiaohongshu"
    override val displayName: String = "小红书"
    override val version: Int = 1
    override val config: PluginConfig = PluginConfig(
        actions = listOf(
            PluginConfigAction(id = ACTION_LOGIN, label = "登录小红书"),
            PluginConfigAction(id = ACTION_LOGOUT, label = "退出登录"),
        ),
    )

    @Volatile
    private var service: XiaohongshuService = injectedService ?: object : XiaohongshuService {}
    private val hasInjectedService: Boolean = injectedService != null

    /** 工具目录一次构建并缓存；service 经闭包按调用时读取，onAttach 注入后无需重建。 */
    override fun tools(): List<BaseTool> = toolList

    private val toolList: List<BaseTool> by lazy { XiaohongshuToolCatalog.create { service } }

    override fun onAttach(context: Context) {
        if (!hasInjectedService) {
            service = DirectXiaohongshuService(AndroidXiaohongshuBrowserGateway(context.applicationContext))
        }
    }

    override fun configActionBrowserRequest(actionId: String): BrowserAuthRequest? = when (actionId) {
        ACTION_LOGIN -> BrowserAuthRequest(
            authorizeUrl = "https://www.xiaohongshu.com/explore",
            redirectBase = "gimi-plugin-capture://complete",
            completionScript =
                "document.querySelector('.main-container .user .link-wrapper .channel') !== null",
            captureCookiesForUrl = "https://www.xiaohongshu.com",
            desktopMode = true,
        )
        else -> null
    }

    override suspend fun completeConfigAction(actionId: String, redirectUrl: String): PluginActionResult =
        if (actionId == ACTION_LOGIN && redirectUrl.startsWith("gimi-plugin-capture://complete")) {
            PluginActionResult(message = "小红书登录成功")
        } else {
            PluginActionResult(message = "无法完成小红书登录", success = false)
        }

    override suspend fun runConfigAction(actionId: String): PluginActionResult = when (actionId) {
        ACTION_LOGOUT -> runCatching { service.invoke("delete_cookies", emptyMap()) }
            .fold(
                onSuccess = { PluginActionResult(message = "已退出小红书登录") },
                onFailure = { PluginActionResult(it.message ?: "退出登录失败", success = false) },
            )
        else -> PluginActionResult(message = "不支持的操作：$actionId", success = false)
    }

    companion object {
        const val ACTION_LOGIN: String = "login"
        const val ACTION_LOGOUT: String = "logout"
    }
}
