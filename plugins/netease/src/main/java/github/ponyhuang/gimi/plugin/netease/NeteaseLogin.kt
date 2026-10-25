package github.ponyhuang.gimi.plugin.netease

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONException

/** 单次扫码登录的后台控制器，打开链接后最多轮询五分钟；新登录和凭证修改取消旧任务。 */
internal class NeteaseLogin(
    private val account: NeteaseAccount,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val generation = AtomicLong()
    private var job: Job? = null
    @Volatile private var phase = "unauthorized"
    @Volatile var failure: Exception? = null
        private set

    suspend fun start(): Map<String, Any?> = mutex.withLock {
        val current = generation.incrementAndGet()
        job?.cancelAndJoin()
        failure = null
        phase = "unauthorized"
        val qr = account.createQr()
        if (generation.get() != current) throw CancellationException("NetEase credentials changed")
        phase = "pending"
        job = scope.launch {
            try {
                withTimeout(300_000) {
                    while (true) {
                        val status = account.pollQr()["status"]
                        if (generation.get() != current) return@withTimeout
                        when (status) {
                            803 -> { phase = "authorized"; return@withTimeout }
                            800 -> { phase = "expired"; return@withTimeout }
                        }
                        delay(3_000)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (generation.get() == current) phase = "expired"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IOException) {
                fail(current, error)
            } catch (error: IllegalStateException) {
                fail(current, error)
            } catch (error: IllegalArgumentException) {
                fail(current, error)
            } catch (error: JSONException) {
                fail(current, error)
            }
        }
        qr
    }

    suspend fun status(): String = mutex.withLock {
        if (phase == "pending") return@withLock "pending"
        val session = account.status()
        // 重试读取当前会话；新扫码失败不能覆盖原有授权，也不能遮住工具已保存的新会话。
        if (session["authorized"] == true && session["anonymous"] == false) {
            phase = "authorized"
            failure = null
            return@withLock "authorized"
        }
        if (phase in setOf("expired", "failed")) phase else "unauthorized"
    }

    fun cancel() {
        generation.incrementAndGet()
        job?.cancel()
        phase = "unauthorized"
        failure = null
    }

    private fun fail(current: Long, error: Exception) {
        if (generation.get() == current) {
            failure = error
            phase = "failed"
        }
    }
}
