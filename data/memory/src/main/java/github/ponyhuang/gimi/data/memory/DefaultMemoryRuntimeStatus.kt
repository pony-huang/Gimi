package github.ponyhuang.gimi.data.memory

import github.ponyhuang.gimi.domain.memory.model.MemoryOperation
import github.ponyhuang.gimi.domain.memory.model.MemoryRuntimeFailure
import github.ponyhuang.gimi.domain.memory.repository.MemoryRuntimeStatus
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class DefaultMemoryRuntimeStatus @Inject constructor() : MemoryRuntimeStatus {
    private val failingOperations = ConcurrentHashMap.newKeySet<MemoryOperation>()
    private val mutableFailures = MutableSharedFlow<MemoryRuntimeFailure>(extraBufferCapacity = 4)
    override val failures: SharedFlow<MemoryRuntimeFailure> = mutableFailures.asSharedFlow()

    internal var lastFailure: MemoryOperation? = null
        private set

    override fun reportFailure(operation: MemoryOperation) {
        lastFailure = operation
        if (failingOperations.add(operation)) {
            mutableFailures.tryEmit(MemoryRuntimeFailure(operation))
        }
    }

    override fun reportSuccess(operation: MemoryOperation) {
        failingOperations.remove(operation)
    }
}
