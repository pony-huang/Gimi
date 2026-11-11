package github.ponyhuang.gimi.data.agent.plugins

import android.util.Log
import com.google.adk.kt.agents.CallbackContext
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.plugins.Plugin
import com.google.adk.kt.types.Content
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 在一轮 Agent 正常结束后，按 ADK MemoryService 增量契约保存本轮新事件。 */
class MemoryPersistencePlugin(
    override val name: String = "memory_persistence_plugin",
) : Plugin {
    /**
     * 每个会话的增量记账状态。同一会话的并发回调可能来自多个 runner，
     * [SessionIngestion.mutex] 保证「计算增量 → 写入 → 标记」不被并发交叉成重复提交。
     */
    private val sessions = ConcurrentHashMap<String, SessionIngestion>()

    private class SessionIngestion {
        val mutex = Mutex()
        val ingested = IngestedEventIds()
    }

    /**
     * 每个会话已写入记忆的事件 id，用于每次 afterAgent 只提交增量，避免全量重发。
     * 超过 [IngestedEventIds.MAX_TRACKED_IDS] 时淘汰最早记录：最旧事件早已持久化成功，
     * 被重复提交的概率与代价（MemoryService 去重/幂等）都可接受，换来记账集合有界。
     */
    private class IngestedEventIds {
        private val ids = LinkedHashSet<String>()

        @Synchronized
        fun contains(id: String?): Boolean = id != null && id in ids

        @Synchronized
        fun addAll(newIds: Collection<String>) {
            newIds.forEach { id ->
                if (ids.size >= MAX_TRACKED_IDS) {
                    val oldest = ids.firstOrNull() ?: return@forEach
                    ids.remove(oldest)
                }
                ids.add(id)
            }
        }

        private companion object {
            const val MAX_TRACKED_IDS = 4_000
        }
    }

    override suspend fun afterAgent(context: CallbackContext): CallbackChoice<Unit, Content> {
        // 无会话 id 无法记账，直接放行；正常会话 id 恒非空。
        val sessionId = context.session.key.id ?: return CallbackChoice.Continue(Unit)
        val ingestion = sessions.computeIfAbsent(sessionId) { SessionIngestion() }
        ingestion.mutex.withLock {
            val ingested = ingestion.ingested
            val delta = context.session.events.filter { event -> !event.partial && !ingested.contains(event.id) }
            if (delta.isNotEmpty()) {
                cancellationAwareRunCatching { context.addEventsToMemory(delta) }
                    .onSuccess { ingested.addAll(delta.mapNotNull { it.id }) }
                    .onFailure { error ->
                        // 写入失败不标记已写入，留待下一轮随新事件一并重试；本地 AppSearch 异常
                        // 也不再向上传播把已完成的回复判成失败（Mem0 侧由 recover 自行上报通知）。
                        Log.w(TAG, "Memory persistence failed for $sessionId", error)
                    }
            }
        }
        return CallbackChoice.Continue(Unit)
    }

    private companion object {
        const val TAG = "memory.plugin"
    }
}
