package github.ponyhuang.gimi.data.a2a.repository

import github.ponyhuang.gimi.domain.a2a.model.RemoteAgentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 远程 A2A agent 配置仓库的持久化与 revision 行为；存储以内存假实现替换 Keystore 实现。 */
class SecureRemoteAgentRepositoryTest {

    private class InMemoryRemoteAgentStorage : RemoteAgentStorage {
        var content: String? = null

        override fun read(): String? = content

        override fun write(value: String) {
            content = value
        }
    }

    private val storage = InMemoryRemoteAgentStorage()

    @Test
    fun save_addsNewAgentAndBumpsRevision() {
        val repository = SecureRemoteAgentRepository(storage)
        assertEquals(0L, repository.revision.value)

        repository.save(RemoteAgentConfig(name = "weather", endpointUrl = "http://a.example.com"))

        assertEquals(1, repository.currentAgents().size)
        assertEquals(1L, repository.revision.value)
    }

    @Test
    fun save_replacesExistingAgentById() {
        val repository = SecureRemoteAgentRepository(storage)
        val original = RemoteAgentConfig(name = "weather", endpointUrl = "http://a.example.com")
        repository.save(original)

        repository.save(original.copy(endpointUrl = "http://b.example.com"))

        val agents = repository.currentAgents()
        assertEquals(1, agents.size)
        assertEquals("http://b.example.com", agents.single().endpointUrl)
        assertEquals(2L, repository.revision.value)
    }

    @Test
    fun persistedState_roundTripsAcrossInstances() {
        val writer = SecureRemoteAgentRepository(storage)
        writer.save(RemoteAgentConfig(name = "weather", endpointUrl = "http://a.example.com"))
        writer.save(
            RemoteAgentConfig(name = "calendar", endpointUrl = "http://b.example.com", isEnabled = false),
        )

        val reader = SecureRemoteAgentRepository(storage)

        assertEquals(writer.currentAgents(), reader.currentAgents())
    }

    @Test
    fun corruptStorage_loadsEmpty() {
        storage.content = "{ not json"

        val repository = SecureRemoteAgentRepository(storage)

        assertTrue(repository.currentAgents().isEmpty())
    }
}
