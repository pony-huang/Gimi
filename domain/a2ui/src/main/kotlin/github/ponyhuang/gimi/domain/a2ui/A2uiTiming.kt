package github.ponyhuang.gimi.domain.a2ui

import kotlinx.coroutines.CompletableDeferred

/** 进程内调试计时，最多保留近期请求，不把日志或时间戳写进协议消息。 */
object A2uiTiming {
    private class Submission(val started: Long, val report: (String) -> Unit)
    private class Trace(val submission: Submission) {
        val stages = mutableSetOf<String>()
        val finalUpdate = CompletableDeferred<Unit>()
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
            if (surfaces[surfaceId]?.submission !== submission) surfaces.putRecent(surfaceId, Trace(submission))
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
        synchronized(this) { surfaces[surfaceId]?.finalUpdate }?.complete(Unit)
        mark(surfaceId, "final_update_submitted")
    }

    suspend fun awaitFinalUpdate(surfaceId: String): Boolean {
        val update = synchronized(this) { surfaces[surfaceId]?.finalUpdate } ?: return false
        update.await()
        return true
    }

    private fun <T> LinkedHashMap<String, T>.putRecent(key: String, value: T) {
        remove(key)
        put(key, value)
        if (size > 64) remove(keys.first())
    }
}
