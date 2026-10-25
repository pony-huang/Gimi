package github.ponyhuang.gimi.data.a2a.repository

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import github.ponyhuang.gimi.domain.a2a.repository.RemoteAgentRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/** 基于 Keystore 加密存储的远程 A2A agent 配置仓库。 */
@Singleton
class SecureRemoteAgentRepository @Inject constructor(
    private val storage: RemoteAgentStorage,
) : RemoteAgentRepository {
    /** 字段名 = Kotlin 属性名、默认值全量写出、null 省略、忽略未知键，为后续字段演进留余量。 */
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val agents = MutableStateFlow(load())
    private val _revision = MutableStateFlow(0L)

    override val revision = _revision.asStateFlow()

    override fun observeAgents() = agents.asStateFlow()

    override fun currentAgents(): List<RemoteAgentConfig> = agents.value

    override fun agent(id: String): RemoteAgentConfig? = agents.value.firstOrNull { it.id == id }

    @Synchronized
    override fun save(agent: RemoteAgentConfig) {
        val updated = agents.value.toMutableList().apply {
            val index = indexOfFirst { it.id == agent.id }
            if (index < 0) add(agent) else set(index, agent)
        }
        persist(updated)
    }

    @Synchronized
    override fun delete(id: String) = persist(agents.value.filterNot { it.id == id })

    private fun load(): List<RemoteAgentConfig> = runCatching {
        json.decodeFromString<List<RemoteAgentConfig>>(storage.read() ?: "[]")
    }.getOrDefault(emptyList())

    private fun persist(value: List<RemoteAgentConfig>) {
        if (value == agents.value) return
        storage.write(json.encodeToString(value))
        agents.value = value
        _revision.update { it + 1 }
    }
}
