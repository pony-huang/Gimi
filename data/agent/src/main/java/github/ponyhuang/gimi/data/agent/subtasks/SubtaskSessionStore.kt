package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.events.Event
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import github.ponyhuang.gimi.domain.conversation.model.SubtaskBatch
import github.ponyhuang.gimi.domain.conversation.repository.SubtaskRunRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 子执行索引与工作流事件共用宿主 Room 服务，但使用独立 appName，避免进入普通会话列表。
 * 索引与工作流分别写会话，防止并行节点的事件写入和索引更新争用同一个乐观锁。
 */
@Singleton
class SubtaskSessionStore @Inject constructor(private val sessions: SessionService) : SubtaskRunRepository {
    private val mutex = Mutex()
    private val updates = MutableSharedFlow<SubtaskBatch>(extraBufferCapacity = 64)
    private val active = ConcurrentHashMap.newKeySet<String>()

    override fun observe(sessionId: String) = updates.filter { it.sessionId == sessionId }

    internal suspend fun open(batch: SubtaskBatch, fingerprint: String): SubtaskBatch = mutex.withLock {
        val key = indexKey(batch.id)
        val existing = sessions.getSession(key)
        if (existing != null) {
            require(existing.state[FINGERPRINT] == fingerprint) { "Delegation input changed during recovery" }
            decode(existing.state[SNAPSHOT])!!
        } else {
            sessions.createSession(key, mapOf(SNAPSHOT to Json.encodeToString(batch), FINGERPRINT to fingerprint))
            batch
        }
    }

    internal suspend fun read(id: String): SubtaskBatch =
        decode(requireNotNull(sessions.getSession(indexKey(id))).state[SNAPSHOT])!!

    internal suspend fun update(id: String, change: (SubtaskBatch) -> SubtaskBatch): SubtaskBatch {
        val batch = mutex.withLock {
            val session = requireNotNull(sessions.getSession(indexKey(id)))
            val next = change(requireNotNull(decode(session.state[SNAPSHOT])))
            sessions.appendEvent(session, Event(
                author = "subtasks",
                actions = EventActions(stateDelta = mutableMapOf(SNAPSHOT to Json.encodeToString(next))),
            ))
            next
        }
        updates.emit(batch)
        return batch
    }

    internal fun started(id: String) = active.add(id)
    internal fun finished(id: String) = active.remove(id)

    override suspend fun load(sessionId: String): List<SubtaskBatch> =
        sessions.listSessions(INDEX_APP, USER).sessions
            .filter { it.key.id?.startsWith(prefix(sessionId)) == true }
            .mapNotNull { listed -> sessions.getSession(listed.key)?.state?.get(SNAPSHOT)?.let(::decode) }
            .map { if (it.id in active) it else it.interrupted() }
            .sortedBy(SubtaskBatch::createdAt)

    override suspend fun delete(sessionId: String) {
        for (app in listOf(INDEX_APP, WORKFLOW_APP)) {
            sessions.listSessions(app, USER).sessions
                .filter { it.key.id?.startsWith(prefix(sessionId)) == true }
                .forEach { sessions.deleteSession(it.key) }
        }
    }

    private fun decode(value: Any?): SubtaskBatch? =
        (value as? String)?.let { Json.decodeFromString<SubtaskBatch>(it) }

    private fun indexKey(id: String) = SessionKey(INDEX_APP, USER, id)

    companion object {
        const val WORKFLOW_APP = "gimi_subtask_workflows"
        const val INDEX_APP = "gimi_subtask_index"
        private const val USER = AgentSessionIdentity.DEFAULT_USER_ID
        private const val SNAPSHOT = "subtask_snapshot"
        private const val FINGERPRINT = "input_fingerprint"

        fun batchId(sessionId: String, invocationId: String, callId: String): String =
            prefix(sessionId) + digest("$invocationId\u0000$callId")

        private fun prefix(sessionId: String) = digest(sessionId) + "_"

        internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
