package github.ponyhuang.gimi.plugin.netease

import android.content.Context
import com.google.adk.kt.tools.Toolset
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginActionCallbackRequest
import github.ponyhuang.gimi.pluginapi.PluginActionResult
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigAction
import github.ponyhuang.gimi.pluginapi.PluginConfigActionExecution
import github.ponyhuang.gimi.pluginapi.PluginConfigField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

/** 网易云开放平台插件；所有 API、登录和存储细节封装于独立插件 APK。 */
class NeteasePlugin : AgentPlugin {
    override val pluginId: String = "netease"
    override val displayName: String = "网易云音乐"
    override val version: Int = 1

    // 工具数从目录定义动态推导，避免目录增删后硬编码数字漂移。
    override val toolCount: Int get() = neteaseTools(account).size

    @Volatile private var context: Context? = null
    @Volatile private var resourcesContext: Context? = null
    @Volatile private var credentials = NeteaseCredentials("", "")
    private val account by lazy {
        NeteaseAccount(NeteaseApi(), AndroidNeteaseSessionStore(requireNotNull(context)), { credentials })
    }
    // 插件与宿主进程同生命周期，scope 不设独立关闭路径是有意的；
    // 凭证变更经 configure() → login.cancel() 收敛后台轮询。
    private val loginDelegate = lazy { NeteaseLogin(account, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
    private val login by loginDelegate
    private val toolsets by lazy { listOf(NeteaseToolset { neteaseTools(account) }) }

    private fun label(id: Int): String = requireNotNull(resourcesContext) { "网易云插件尚未初始化" }.getString(id)

    override val config: PluginConfig
        get() = PluginConfig(
            fields = listOf(
                PluginConfigField.Text("app_id", label(R.string.netease_app_id)),
                PluginConfigField.Text("client_secret", label(R.string.netease_client_secret), secret = true),
                PluginConfigField.Text("private_key", label(R.string.netease_private_key), secret = true),
            ),
            actions = listOf(
                PluginConfigAction("link:official", label(R.string.netease_official_site)),
                PluginConfigAction("oauth_status", label(R.string.netease_login_status)),
                PluginConfigAction("oauth_login", label(R.string.netease_login)),
            ),
        )

    override fun onAttach(context: Context) {
        // 动态加载时必须用插件包的 Resources，宿主 Context 不认识插件的资源 ID。
        this.context = context.applicationContext
        this.resourcesContext = context.createPackageContext("github.ponyhuang.gimi.plugin.netease", 0)
            .createConfigurationContext(context.resources.configuration)
    }

    override fun configure(values: Map<String, String>) {
        val next = NeteaseCredentials(
            values["app_id"].orEmpty().trim(),
            values["private_key"].orEmpty(),
            // 令牌刷新接口需要 client secret；历史配置可能没有该字段，留空由刷新时报错提示补全。
            values["client_secret"].orEmpty(),
        )
        if (loginDelegate.isInitialized() && (credentials.appId != next.appId || credentials.privateKey != next.privateKey)) login.cancel()
        credentials = next
    }

    override fun toolSets(): List<Toolset> = toolsets

    override suspend fun runConfigAction(actionId: String): PluginConfigActionExecution = withContext(Dispatchers.IO) {
        try {
            when (actionId) {
                "link:official" -> PluginConfigActionExecution.AwaitingCallback(PluginActionCallbackRequest(
                    handlerId = "external_url",
                    parameters = mapOf("url" to "https://music.163.com/st/ncmcli"),
                ))
                "oauth_status" -> {
                    val status = login.status()
                    if (status == "failed") return@withContext failureFor(login.failure)
                    PluginConfigActionExecution.Completed(PluginActionResult(message = status))
                }
                "oauth_login" -> {
                    val result = login.start()
                    PluginConfigActionExecution.AwaitingCallback(PluginActionCallbackRequest(
                        handlerId = "external_url",
                        parameters = mapOf("url" to result.getValue("qr_code_url").toString()),
                    ))
                }
                else -> super.runConfigAction(actionId)
            }
        } catch (cancelled: CancellationException) {
            // 取消必须传播（JVM 上 CancellationException 是 IllegalStateException 子类，
            // 不能被下面的登录失败 catch 吞成 {error}）。
            throw cancelled
        } catch (error: NeteaseApiException) {
            failureFor(error)
        } catch (error: NeteaseNetworkException) {
            failureFor(error)
        } catch (_: IOException) {
            failure(R.string.netease_network_error)
        } catch (_: IllegalArgumentException) {
            failure(R.string.netease_config_error)
        } catch (_: IllegalStateException) {
            failure(R.string.netease_login_error)
        } catch (_: JSONException) {
            failure(R.string.netease_response_error)
        }
    }

    private fun failureFor(error: Exception?): PluginConfigActionExecution.Completed = when (error) {
        is NeteaseApiException -> PluginConfigActionExecution.Completed(PluginActionResult(
            message = requireNotNull(resourcesContext).getString(
                when {
                    error.http -> R.string.netease_http_error
                    error.code == 301 -> R.string.netease_account_mismatch
                    else -> R.string.netease_api_error
                }, error.code,
            ),
            success = false,
        ))
        is NeteaseNetworkException -> failure(when (error.failure) {
            NeteaseNetworkFailure.PublicIp -> R.string.netease_public_ip_error
            NeteaseNetworkFailure.Dns -> R.string.netease_dns_error
            NeteaseNetworkFailure.Tls -> R.string.netease_tls_error
            NeteaseNetworkFailure.Timeout -> R.string.netease_timeout_error
            NeteaseNetworkFailure.Connection -> R.string.netease_network_error
        })
        else -> failure(R.string.netease_login_error)
    }

    private fun failure(message: Int) = PluginConfigActionExecution.Completed(
        PluginActionResult(message = label(message), success = false),
    )
}
