package github.ponyhuang.gimi.data.memory

import com.google.adk.kt.events.Event
import com.google.adk.kt.memory.MemoryEntry
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.SearchMemoryResponse
import com.google.adk.kt.sessions.Session
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository

/** 每次调用读取最新设置，在本地 AppSearch 与 Mem0 之间动态路由；记忆总开关关闭时路由到空实现。 */
class RoutingMemoryService(
    private val settingsRepository: MemorySettingsRepository,
    private val localMemoryService: MemoryService,
    private val mem0MemoryService: MemoryService,
) : MemoryService {
    override suspend fun addSessionToMemory(session: Session) = selected().addSessionToMemory(session)

    override suspend fun addEventsToMemory(
        appName: String,
        userId: String,
        events: List<Event>,
        sessionId: String?,
        customMetadata: Map<String, Any?>?,
    ) = selected().addEventsToMemory(appName, userId, events, sessionId, customMetadata)

    override suspend fun addMemory(
        appName: String,
        userId: String,
        memories: List<MemoryEntry>,
        customMetadata: Map<String, Any?>?,
    ) = selected().addMemory(appName, userId, memories, customMetadata)

    override suspend fun searchMemory(
        appName: String,
        userId: String,
        query: String,
    ): SearchMemoryResponse = selected().searchMemory(appName, userId, query)

    private fun selected(): MemoryService {
        val configuration = settingsRepository.configuration.value
        return when {
            !configuration.memoryEnabled -> NoOpMemoryService
            configuration.mem0Enabled -> mem0MemoryService
            else -> localMemoryService
        }
    }
}

/** 记忆关闭时的空实现：不保存、不召回。 */
private object NoOpMemoryService : MemoryService {
    override suspend fun addSessionToMemory(session: Session) = Unit

    override suspend fun addEventsToMemory(
        appName: String,
        userId: String,
        events: List<Event>,
        sessionId: String?,
        customMetadata: Map<String, Any?>?,
    ) = Unit

    override suspend fun addMemory(
        appName: String,
        userId: String,
        memories: List<MemoryEntry>,
        customMetadata: Map<String, Any?>?,
    ) = Unit

    override suspend fun searchMemory(
        appName: String,
        userId: String,
        query: String,
    ): SearchMemoryResponse = SearchMemoryResponse(emptyList())
}
