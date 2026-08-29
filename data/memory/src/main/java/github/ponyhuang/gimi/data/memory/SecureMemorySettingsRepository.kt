package github.ponyhuang.gimi.data.memory

import github.ponyhuang.gimi.domain.memory.model.MemoryConfiguration
import github.ponyhuang.gimi.domain.memory.repository.MemorySettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class SecureMemorySettingsRepository @Inject constructor(
    private val storage: MemorySettingsStorage,
) : MemorySettingsRepository {
    private val mutex = Mutex()
    private val mutableConfiguration = MutableStateFlow(storage.read().toConfiguration())
    override val configuration: StateFlow<MemoryConfiguration> = mutableConfiguration.asStateFlow()

    override suspend fun save(memoryEnabled: Boolean, mem0Enabled: Boolean, apiKey: String?) {
        mutex.withLock {
            val token = apiKey?.trim()?.takeIf(String::isNotEmpty)
                ?: mutableConfiguration.value.apiKey
            require(!mem0Enabled || token.isNotEmpty()) { "Mem0 API key is required." }
            val stored = StoredMemorySettings(
                memoryEnabled = memoryEnabled,
                mem0Enabled = mem0Enabled,
                apiKey = token,
            )
            withContext(Dispatchers.IO) { storage.write(stored) }
            mutableConfiguration.value = stored.toConfiguration()
        }
    }
}

private fun StoredMemorySettings?.toConfiguration(): MemoryConfiguration = MemoryConfiguration(
    memoryEnabled = this?.memoryEnabled != false,
    mem0Enabled = this?.mem0Enabled == true && this.apiKey.isNotBlank(),
    apiKey = this?.apiKey.orEmpty(),
)
