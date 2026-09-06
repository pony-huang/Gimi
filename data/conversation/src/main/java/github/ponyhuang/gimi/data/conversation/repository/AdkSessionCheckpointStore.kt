package github.ponyhuang.gimi.data.conversation.repository

import com.google.adk.kt.events.Event
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.serialization.Json as AdkJson
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import javax.inject.Inject

/**
 * 用 [SessionService] 抓取/恢复会话检查点。
 *
 * ADK 0.8.0 的 [SessionService] 没有 rewind/删除单个事件的 API，因此“回到本轮发送前”
 * 的实现是：删除会话 → 用检查点重建同 id 会话 → 重放检查点内的事件。恢复只回写会话级
 * 状态（剔除 `app:`/`user:`/`temp:` 前缀键），不触碰跨会话共享资源。
 */
class AdkSessionCheckpointStore @Inject constructor(
    private val sessionService: SessionService,
) : ChatSessionCheckpointStore {

    override suspend fun capture(sessionId: String): ChatSessionCheckpoint {
        val key = SessionKey(APP_NAME, USER_ID, sessionId)
        val session = sessionService.getSession(key)
            ?: error("Cannot capture checkpoint for missing session $sessionId")
        val now = session.lastUpdateTime.toEpochMilliseconds()
        return ChatSessionCheckpoint(
            sessionId = sessionId,
            stateJson = AdkJson.Companion.toJsonString(session.state),
            createTime = now,
            updateTime = now,
            events = session.events.map { event ->
                CheckpointEvent(
                    id = event.id,
                    invocationId = event.invocationId,
                    timestamp = event.timestamp,
                    eventData = AdkEventCodec.encode(event),
                )
            },
        )
    }

    override suspend fun restore(checkpoint: ChatSessionCheckpoint) {
        val key = SessionKey(APP_NAME, USER_ID, checkpoint.sessionId)
        sessionService.deleteSession(key)
        val sessionState = AdkJson.Companion.fromJsonToMap(checkpoint.stateJson)
            .filterKeys { key ->
                !key.startsWith("app:") && !key.startsWith("user:") && !key.startsWith("temp:")
            }
        @Suppress("UNCHECKED_CAST")
        val session = sessionService.createSession(key, sessionState as Map<String, Any>)
        checkpoint.events.forEach { event ->
            val restored = AdkEventCodec.decode(event.eventData)
            sessionService.appendEvent(session, restored)
        }
    }

    private companion object {
        const val APP_NAME: String = AgentSessionIdentity.APP_NAME
        const val USER_ID: String = AgentSessionIdentity.DEFAULT_USER_ID
    }
}

/**
 * 桥接到 ADK 内部（Kotlin `internal`、但 JVM 为 public）的 [JsonConverters]，用于对
 * `Event` 做序列化/反序列化。ADK 事件携带 `Map<String, Any>`（自定义元数据、工具参数），
 * 用普通 kotlinx `Json` 会抛 “Serializer for class 'Any' is not found”；ADK 自己的编解码器
 * 已正确注册 `Any` 处理，因此这里直接调用它，保证与 ADK 持久化格式一致。
 */
internal object AdkEventCodec {
    private val instance: Any = Class.forName(JSON_CONVERTERS)
        .getField("INSTANCE")
        .get(null)
    private val encodeMethod = instance.javaClass.getMethod("eventToJson", Event::class.java)
    private val decodeMethod = instance.javaClass.getMethod("eventFromJson", String::class.java)

    fun encode(event: Event): String = encodeMethod.invoke(instance, event) as String

    fun decode(json: String): Event = decodeMethod.invoke(instance, json) as Event

    private const val JSON_CONVERTERS = "com.google.adk.kt.sessions.room.JsonConverters"
}
