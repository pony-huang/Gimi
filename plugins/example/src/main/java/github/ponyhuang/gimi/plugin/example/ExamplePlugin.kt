package github.ponyhuang.gimi.plugin.example

import android.util.Log
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.types.Content
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import github.ponyhuang.gimi.pluginapi.PluginConfig
import github.ponyhuang.gimi.pluginapi.PluginConfigField

/**
 * 示例插件 — 演示最小实现：一个 token 配置字段 + 一个开关，并记录每次用户消息。
 *
 * 构建/安装：`./gradlew.bat :plugins:example:assembleDebug` → 安装产物 APK → 重启宿主即被发现。
 *
 * 此模块同时作为第三方作者的模板：实现 [AgentPlugin]（[pluginId]/[version]/[config] + 需要的回调），
 * 在 manifest 声明发现 service 与 CLASS meta-data，`compileOnly` 依赖插件 API。
 */
class ExamplePlugin(
    override val pluginId: String = "example",
    override val version: Int = 1,
) : AgentPlugin {

    override val name: String = "example_plugin"

    override val config: PluginConfig = PluginConfig(
        fields = listOf(
            PluginConfigField.Text(
                key = "token",
                label = "API Token",
                secret = true,
            ),
            PluginConfigField.Toggle(
                key = "enabled",
                label = "启用日志",
                defaultValue = true,
            ),
        ),
    )

    override suspend fun onUserMessage(
        invocationContext: InvocationContext,
        userMessage: Content,
    ): Content {
        Log.d(TAG, "Example plugin received user message (session=${invocationContext.session.key.id})")
        return userMessage
    }

    private companion object {
        const val TAG: String = "ExamplePlugin"
    }
}
