package github.ponyhuang.gimi.pluginapi

import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.Toolset

/**
 * 第一方插件契约 — 在 ADK [Plugin] 之上封装的一层，是宿主运行时动态加载的对象。
 *
 * 插件作者实现本接口（[pluginId] / [version] / [config] + 需要的 ADK 回调），宿主经
 * DexClassLoader 实例化后直接当作 ADK [Plugin] 注入 Agent 运行。
 *
 * 内置浏览器（WebView）是面向**所有**插件的通用授权/抓包通道，不限于某个插件（如
 * Spotify、知乎）：当前用于配置页 OAuth 授权（[configActionBrowserRequest] /
 * [completeConfigAction]）；后续扩展为「抓包」式拦截 —— 截获浏览器内用户以身份访问
 * Web 服务时产生的 token/session/cookie，供插件凭此调用对应的 Web API。
 *
 * 预留扩展点（后续填充默认实现）：
 * - MCP 注入：插件声明要注入会话的 MCP 服务器；
 * - 工具注入配置：插件声明要注册的工具。
 */
interface AgentPlugin : Plugin {

    /** 稳定唯一 id，如 `"example"`。 */
    val pluginId: String

    /** 展示给用户的名称（如 `"知乎"`）；默认取 [pluginId]，作者可按需覆盖。 */
    val displayName: String get() = pluginId

    /** ADK [Plugin] 要求的唯一名称；默认取 [pluginId]，作者通常无需覆写。 */
    override val name: String get() = pluginId

    /** 插件自身版本，供宿主做启用/升级判断。 */
    val version: Int

    /** 插件向 Agent 声明的工具总数；动态 Toolset 插件应覆盖此值供宿主展示。 */
    val toolCount: Int get() = tools().size

    /**
     * 插件编译时固化的协议版本，默认取 [PluginApi.VERSION]。
     *
     * 未来协议破坏性变更递增 [PluginApi.VERSION] 后，旧插件（编译时固化的旧值）
     * 会被宿主识别并跳过，避免 ABI 不匹配。作者通常无需覆写。
     */
    val apiVersion: Int get() = PluginApi.VERSION

    /** 配置描述，宿主未来据此动态渲染配置页（如 token 认证）。 */
    val config: PluginConfig

    /**
     * 插件注入到 Agent 的工具；默认无。
     *
     * 宿主在构建 Agent 时，把每个插件返回的工具一并挂到 `LlmAgent.tools`。
     */
    fun tools(): List<BaseTool> = emptyList()

    /**
     * 插件注入 Agent 的 Toolset（动态工具集）；默认无。
     *
     * 与 [tools] 的区别：ADK 会在每次模型请求时调用
     * [com.google.adk.kt.tools.Toolset.getTools]，并按请求期上下文（ReadonlyContext）
     * 返回工具，适合需要按会话或授权状态动态裁剪的工具组。宿主构建 Agent 时把每个
     * 插件返回的 Toolset 一并挂到 `LlmAgent.toolsets`。
     */
    fun toolSets(): List<Toolset> = emptyList()

    /**
     * 接收宿主持久化的配置值（未来配置页回填，键对应 [PluginConfig.fields] 的 key）。
     *
     * 插件据此更新自身状态（如保存 access_token）；默认无操作。
     */
    fun configure(values: Map<String, String>) {}

    /**
     * 宿主加载实例后注入 Android [android.content.Context]（applicationContext）。
     *
     * 需要 Android 能力的插件（开浏览器、起本地回调服务、持久化 token 等）在此保存
     * context 供 [tools] 使用。默认无操作。
     */
    fun onAttach(context: android.content.Context) {}

    /**
     * 执行配置页动作（对应 [PluginConfig.actions] 里的 [PluginConfigAction]），如「授权登录」。
     *
     * 宿主在配置页点按按钮后经 [PluginManager.runConfigAction] 转调；可挂起（如等待 OAuth 回调）。
     * 需要内置浏览器授权时见 [configActionBrowserRequest]。默认返回不支持。
     */
    suspend fun runConfigAction(actionId: String): PluginActionResult =
        PluginActionResult(message = "Plugin does not support action: $actionId", success = false)

    /**
     * 配置页动作若需宿主用内置浏览器（WebView）完成授权，返回浏览器请求；
     * 宿主据此打开 WebView 加载 [BrowserAuthRequest.authorizeUrl]，并在导航到
     * [BrowserAuthRequest.redirectBase] 前缀时截获完整重定向 URL 交给 [completeConfigAction]。
     * 返回 null 表示无需浏览器。
     *
     * 该通道是通用能力：任何需要以用户身份调 Web API 的插件（Spotify、知乎等）都可复用；
     * 后续在此基础上扩展为抓包式拦截 —— 直接把浏览器内访问产生的 token/session/cookie
     * 捕获下来，供插件调用对应 Web API。
     */
    fun configActionBrowserRequest(actionId: String): BrowserAuthRequest? = null

    /**
     * 内置浏览器授权回调：宿主把截获的重定向 URL（含 code/state）交给插件完成动作（如换 token）。
     *
     * 这是抓包式取凭证的基础：宿主只负责把浏览器里截获的 URL/请求交给插件，插件自行解析出
     * token/session/cookie 并保存（如 [configure]），之后据此调用 Web API。通用机制，不限插件。
     * 默认返回不支持。
     */
    suspend fun completeConfigAction(actionId: String, redirectUrl: String): PluginActionResult =
        PluginActionResult(message = "Plugin does not support in-app browser callback: $actionId", success = false)
}

/**
 * 内置浏览器授权请求。
 *
 * 浏览器通道不只服务 OAuth：后续用作通用抓包入口 —— 插件通过 WebView 以用户身份访问服务，
 * 宿主截获其中的 token/session/cookie 交还插件，据此调用对应 Web API（对 Spotify、知乎等
 * 所有插件通用）。
 *
 * @property authorizeUrl WebView 加载的授权 URL。
 * @property redirectBase WebView 应拦截的重定向 URL 前缀（如 `http://127.0.0.1:8888/callback`）。
 * @property completionScript 可选的网页完成条件；脚本返回 true 时结束浏览器动作。
 * @property captureCookiesForUrl 完成时要读取 Cookie 的站点；Cookie 只经内存回调交给插件。
 * @property desktopMode 是否使用桌面浏览器 UA 与宽视口加载网页。
 */
data class BrowserAuthRequest(
    val authorizeUrl: String,
    val redirectBase: String,
    val completionScript: String? = null,
    val captureCookiesForUrl: String? = null,
    val desktopMode: Boolean = false,
)

/** 配置页动作的执行结果。 */
data class PluginActionResult(
    val message: String,
    val success: Boolean = true,
)
