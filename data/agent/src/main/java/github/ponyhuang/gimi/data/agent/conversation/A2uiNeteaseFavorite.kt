package github.ponyhuang.gimi.data.agent.conversation

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.State
import com.google.adk.kt.tools.ToolContext
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** 用户点击红心时，仅执行当前启用的网易云插件的固定收藏工具。 */
class A2uiNeteaseFavorite @Inject constructor(
    private val plugins: PluginRuntimeProvider<AgentPlugin>,
) {
    @OptIn(com.google.adk.kt.annotations.FrameworkInternalApi::class)
    suspend fun setLiked(sessionId: String, songId: String, liked: Boolean): Boolean {
        if (sessionId.isBlank() || !songId.matches(SONG_ID)) return false
        val plugin = plugins.runtime.value.enabledPlugins.singleOrNull { it.pluginId == "netease" }
            ?: return false
        return cancellationAwareRunCatching {
            val tool = plugin.toolSets().flatMap { it.getTools(null) }
                .singleOrNull { it.name == "netease_song_like" } ?: return false
            // 点击来自真实控件。插件照常检查账号权限，工具上下文不预填确认结果。
            val callId = UUID.randomUUID().toString()
            val invocation = InvocationContext(
                session = Session(
                    SessionKey(AgentSessionIdentity.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, sessionId),
                    State(),
                    mutableListOf(),
                ),
                agent = ToolHost,
                invocationId = callId,
            )
            val result = tool.run(
                ToolContext(invocation, EventActions(), callId, null, null),
                mapOf("song_id" to songId, "like" to liked),
            ) as? Map<*, *>
            // 网易云成功写入时可以省略 data 值，工具仍会返回 data 键。
            result != null && "error" !in result && "data" in result
        }.getOrDefault(false)
    }

    private object ToolHost : BaseAgent("a2ui-netease-tool-host", "Executes user-requested NetEase favorites") {
        override fun runAsyncImpl(context: InvocationContext): Flow<Event> = emptyFlow()
    }

    private companion object {
        val SONG_ID = Regex("[0-9a-fA-F]{32}")
    }
}
