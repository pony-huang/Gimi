package github.ponyhuang.gimi.domain.a2ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** 进程内调试计时，最多保留近期请求，不把日志或时间戳写进协议消息。 */
object A2uiTiming {
    private class Submission(val started: Long, val report: (String) -> Unit)
    private class Trace(val submission: Submission) {
        val stages = mutableSetOf<String>()
        val finalUpdate = CompletableDeferred<Unit>()

        /** 释放等待方；trace 被替换或淘汰时调用，避免 await 永久挂起。 */
        fun release() {
            finalUpdate.complete(Unit)
        }
    }
    private val submissions = linkedMapOf<String, Submission>()
    private val surfaces = linkedMapOf<String, Trace>()

    @Synchronized
    fun submitted(sessionId: String, report: (String) -> Unit) {
        submissions.putRecent(sessionId, Submission(System.nanoTime(), report))
    }

    fun start(sessionId: String?, surfaceId: String) {
        synchronized(this) {
            val submission = submissions[sessionId] ?: return
            val previous = surfaces[surfaceId]
            if (previous?.submission !== submission) {
                // 同 surface 被新一轮复用时，释放上一轮未完成的等待方。
                previous?.release()
                surfaces.putRecent(surfaceId, Trace(submission))
            }
        }
        mark(surfaceId, "tool_entered")
    }

    fun mark(surfaceId: String, stage: String, repeatable: Boolean = false) {
        val submission = synchronized(this) {
            val trace = surfaces[surfaceId] ?: return
            if (!repeatable && !trace.stages.add(stage)) return
            trace.submission
        }
        val elapsed = (System.nanoTime() - submission.started) / 1_000_000
        // 标识是随机 surface 的短后缀，用于关联阶段；不包含会话、提示词或卡片数据。
        submission.report("trace=${surfaceId.takeLast(8)} stage=$stage elapsed_ms=$elapsed")
    }

    fun finalUpdateSubmitted(surfaceId: String) {
        synchronized(this) { surfaces[surfaceId] }?.release()
        mark(surfaceId, "final_update_submitted")
    }

    suspend fun awaitFinalUpdate(surfaceId: String): Boolean {
        val update = synchronized(this) { surfaces[surfaceId]?.finalUpdate } ?: return false
        // 生成失败 / 会话切换等异常路径可能永远等不到 finalUpdate；加超时兜底，
        // 避免调试钩子把调用方的 LaunchedEffect 永久挂起。
        return withTimeoutOrNull(FINAL_UPDATE_TIMEOUT_MS) {
            update.await()
            true
        } == true
    }

    private fun <T> LinkedHashMap<String, T>.putRecent(key: String, value: T) {
        remove(key)
        put(key, value)
        if (size > MAX_ENTRIES) {
            val evicted = remove(keys.first())
            // surfaces 淘汰最旧 trace 时释放其等待方；submissions 存的是 Submission 无此字段。
            (evicted as? Trace)?.release()
        }
    }

    private const val MAX_ENTRIES = 64
    private const val FINAL_UPDATE_TIMEOUT_MS = 30_000L
}
