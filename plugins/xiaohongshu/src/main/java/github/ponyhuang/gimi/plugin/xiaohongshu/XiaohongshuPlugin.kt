package github.ponyhuang.gimi.plugin.xiaohongshu

import android.content.Context
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginActionCallback
import github.ponyhuang.gimi.pluginapi.PluginActionCallbackRequest
import github.ponyhuang.gimi.pluginapi.PluginActionResult
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution

/** 直接通过 Android 浏览器会话操作小红书网页的第一方插件。 */
class XiaohongshuPlugin internal constructor(
    injectedService: XiaohongshuService? = null,
) : AgentPlugin {

    constructor() : this(null)

    override val pluginId: String = "xiaohongshu"
    override val displayName: String = "小红书"
    override val version: Int = 1
    override val toolCount: Int = 16
    override val config: PluginConfig = PluginConfig(
        actions = listOf(
            PluginConfigAction(id = ACTION_LOGIN, label = "登录小红书"),
            PluginConfigAction(id = ACTION_LOGOUT, label = "退出登录"),
        ),
    )

    @Volatile
    private var service: XiaohongshuService = injectedService ?: object : XiaohongshuService {}
    private val hasInjectedService: Boolean = injectedService != null

    /** 工具集与工具目录均复用实例；service 经闭包读取，onAttach 注入后无需重建。 */
    override fun toolSets(): List<Toolset> = toolSets

    private val toolSets: List<Toolset> by lazy { listOf(XiaohongshuToolset { toolList }) }

    private val toolList: List<BaseTool> by lazy { XiaohongshuToolCatalog.create { service } }

    override fun onAttach(context: Context) {
        if (!hasInjectedService) {
            service = DirectXiaohongshuService(AndroidXiaohongshuBrowserGateway(context.applicationContext))
        }
    }

    override suspend fun onConfigActionCallback(
        actionId: String,
        callback: PluginActionCallback,
    ): PluginActionResult =
        if (actionId == ACTION_LOGIN && callback.values.containsKey(CALLBACK_COOKIES)) {
            PluginActionResult(message = "小红书登录成功")
        } else {
            PluginActionResult(message = "无法完成小红书登录", success = false)
        }

    override suspend fun runConfigAction(actionId: String): PluginConfigActionExecution = when (actionId) {
        ACTION_LOGIN -> PluginConfigActionExecution.AwaitingCallback(
            PluginActionCallbackRequest(
                handlerId = HANDLER_WEB,
                parameters = mapOf(
                    PARAM_AUTHORIZE_URL to "https://www.xiaohongshu.com/explore",
                    PARAM_COMPLETION_SCRIPT to
                        "document.querySelector('.main-container .user .link-wrapper .channel') !== null",
                    PARAM_CAPTURE_COOKIES_FOR_URL to "https://www.xiaohongshu.com",
                    PARAM_DESKTOP_MODE to true.toString(),
                ),
            ),
        )
        ACTION_LOGOUT -> PluginConfigActionExecution.Completed(
            runCatching { service.invoke("delete_cookies", emptyMap()) }
                .fold(
                    onSuccess = { PluginActionResult(message = "已退出小红书登录") },
                    onFailure = { PluginActionResult(it.message ?: "退出登录失败", success = false) },
                ),
        )
        else -> PluginConfigActionExecution.Completed(
            PluginActionResult(message = "不支持的操作：$actionId", success = false),
        )
    }

    companion object {
        const val ACTION_LOGIN: String = "login"
        const val ACTION_LOGOUT: String = "logout"
        private const val HANDLER_WEB: String = "web"
        private const val PARAM_AUTHORIZE_URL: String = "authorize_url"
        private const val PARAM_COMPLETION_SCRIPT: String = "completion_script"
        private const val PARAM_CAPTURE_COOKIES_FOR_URL: String = "capture_cookies_for_url"
        private const val PARAM_DESKTOP_MODE: String = "desktop_mode"
        private const val CALLBACK_COOKIES: String = "cookies"
    }
}
