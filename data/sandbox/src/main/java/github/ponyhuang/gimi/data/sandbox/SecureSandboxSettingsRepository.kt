package github.ponyhuang.gimi.data.sandbox

import github.ponyhuang.gimi.domain.sandbox.SandboxConfiguration
import github.ponyhuang.gimi.domain.sandbox.SandboxConnection
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import github.ponyhuang.gimi.domain.sandbox.validateSandboxConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 配置提交成功后才发布状态和 revision，失败不影响已保存凭据。 */
@Singleton
class SecureSandboxSettingsRepository @Inject constructor(private val storage: SandboxSettingsStorage) : SandboxSettingsRepository {
    private val mutex = Mutex()
    private val mutableConfiguration = MutableStateFlow(storage.read() ?: SandboxConfiguration())
    override val configuration = mutableConfiguration.asStateFlow()
    private val mutableRevision = MutableStateFlow(0L)
    override val revision = mutableRevision.asStateFlow()

    override suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        persist(mutableConfiguration.value.copy(enabled = enabled))
    }

    override suspend fun saveConnection(connection: SandboxConnection) = mutex.withLock {
        require(connection.envs.keys.map(String::trim).distinct().size == connection.envs.size) { "Duplicate environment names" }
        val normalized = connection.copy(
            domain = connection.domain.trim().lowercase(),
            apiKey = connection.apiKey.trim(),
            template = connection.template.trim(),
            envs = connection.envs.mapKeys { it.key.trim() }.toMap(),
        )
        require(validateSandboxConnection(normalized).isEmpty()) { "Invalid sandbox settings" }
        persist(mutableConfiguration.value.copy(connection = normalized))
    }

    private suspend fun persist(configuration: SandboxConfiguration) {
        if (configuration == mutableConfiguration.value) return
        withContext(Dispatchers.IO) { storage.write(configuration) }
        mutableConfiguration.value = configuration
        mutableRevision.value += 1
    }
}
