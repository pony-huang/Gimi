package github.ponyhuang.gimi.data.agent.plugins

import android.util.Log
import com.google.adk.kt.agents.CallbackContext
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.types.Content
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import java.util.concurrent.ConcurrentHashMap

/** 在一轮 Agent 正常结束后，按 ADK MemoryService 增量契约保存本轮新事件。 */
class MemoryPersistencePlugin(
    override val name: String = "memory_persistence_plugin",
) : Plugin {
    // 每个会话已写入记忆的事件 id，用于每次 afterAgent 只提交增量，避免全量重发。
    private val ingestedEventIds = ConcurrentHashMap<String, MutableSet<String>>()

    override suspend fun afterAgent(context: CallbackContext): CallbackChoice<Unit, Content> {
        val sessionId = context.session.key.id
        val ingested = ingestedEventIds.getOrPut(sessionId) { HashSet() }
        val delta = context.session.events.filter { event -> !event.partial && event.id !in ingested }
        if (delta.isNotEmpty()) {
            cancellationAwareRunCatching { context.addEventsToMemory(delta) }
                .onSuccess { delta.forEach { ingested.add(it.id) } }
                .onFailure { error ->
                    // 写入失败不标记已写入，留待下一轮随新事件一并重试；本地 AppSearch 异常
                    // 也不再向上传播把已完成的回复判成失败（Mem0 侧由 recover 自行上报通知）。
                    Log.w(TAG, "Memory persistence failed for $sessionId", error)
                }
        }
        return CallbackChoice.Continue(Unit)
    }

    private companion object {
        const val TAG = "memory.plugin"
    }
}
