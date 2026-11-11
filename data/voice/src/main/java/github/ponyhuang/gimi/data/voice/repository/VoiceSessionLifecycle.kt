package github.ponyhuang.gimi.data.voice.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 串行化通话收尾和关闭；重复关闭必须等首个关闭者释放资源后才能返回。 */
internal class VoiceSessionLifecycle {
    private val mutex = Mutex()
    @Volatile
    var closed: Boolean = false
        private set

    suspend fun finishPendingTurn(finish: suspend () -> Boolean): Boolean = mutex.withLock {
        if (closed) false else finish()
    }

    suspend fun close(release: suspend () -> Unit) = mutex.withLock {
        if (closed) return@withLock
        closed = true
        release()
    }
}
