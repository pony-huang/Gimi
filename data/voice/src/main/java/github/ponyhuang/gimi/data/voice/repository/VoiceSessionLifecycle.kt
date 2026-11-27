package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceDuplexSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 串行化通话收尾和关闭；重复关闭必须等首个关闭者释放资源后才能返回。 */
internal class VoiceSessionLifecycle {
    private val mutex = Mutex()
    private var failedDuplexCleanup: Job? = null
    @Volatile
    var closed: Boolean = false
        private set

    suspend fun finishPendingTurn(finish: suspend () -> Boolean): Boolean = mutex.withLock {
        if (closed) false else finish()
    }

    fun closeFailedDuplex(scope: CoroutineScope, session: RealtimeVoiceDuplexSession?, events: Job?, uplink: Job?) {
        events?.cancel()
        uplink?.cancel()
        if (session == null) return
        // 失败可能由事件或上行协程自身报告，交给同级协程等它们结束后再关闭后端。
        failedDuplexCleanup = scope.launch(Dispatchers.IO) {
            events?.join()
            uplink?.join()
            runCatching { session.close() }
        }
    }

    suspend fun awaitFailedDuplexCleanup() {
        failedDuplexCleanup?.join()
    }

    suspend fun close(release: suspend () -> Unit) = mutex.withLock {
        if (closed) return@withLock
        closed = true
        awaitFailedDuplexCleanup()
        release()
    }
}
