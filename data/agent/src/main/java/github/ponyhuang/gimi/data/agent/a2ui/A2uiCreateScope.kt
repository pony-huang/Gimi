package github.ponyhuang.gimi.data.agent.a2ui

import github.ponyhuang.gimi.domain.a2ui.A2uiDocument
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationTask
import github.ponyhuang.gimi.domain.a2ui.A2uiToolResult
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationTaskCodec
import github.ponyhuang.gimi.domain.a2ui.A2uiGenerationStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 单轮创建任务的结构化作用域。任务属于聊天执行而不是工具调用，工具返回后可继续生成。
 * 完成状态实时投递给聊天，历史由宿主在 Runner 退出后顺序写入。
 */
internal class A2uiCreateScope(
    private val scope: CoroutineScope,
    private val publish: suspend (Map<String, Any>) -> Unit = {},
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<A2uiCreateScope>

    /** 一个创建输入及其计算结果；写入完成之前一直保留，以便取消时结束占位。 */
    class Entry(
        val task: A2uiGenerationTask,
        val result: Deferred<Map<String, Any>>,
        val pendingSent: CompletableDeferred<Unit>,
        val liveDelivery: Deferred<Map<String, Any>>,
    ) {
        var delivered = false
        var replacement: Map<String, Any>? = null
    }

    private val entries = java.util.concurrent.CopyOnWriteArrayList<Entry>()
    fun snapshot(): List<Entry> = entries.toList()

    fun markPendingSent(surfaceId: String) {
        entries.firstOrNull { it.task.surfaceId == surfaceId }?.pendingSent?.complete(Unit)
    }

    fun submit(task: A2uiGenerationTask, generate: suspend (suspend (A2uiDocument) -> Unit) -> Map<String, Any>): Map<String, Any> {
        val pendingSent = CompletableDeferred<Unit>()
        val previewReady = CompletableDeferred<A2uiDocument?>()
        val result = scope.async {
            try {
                val generated = withTimeoutOrNull(45.seconds) {
                    generate { previewReady.complete(it) }
                }
                if (generated == null) taskResult(task.copy(status = A2uiGenerationStatus.FAILED))
                else generated + taskResult(task.copy(status = A2uiGenerationStatus.COMPLETED))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // 生成是独立展示边界：失败保留重试入口，不让主 Agent 的正文一并失败。
                taskResult(task.copy(status = A2uiGenerationStatus.FAILED))
            } finally {
                previewReady.complete(null)
            }
        }
        val liveDelivery = scope.async {
            // 等占位只阻塞界面投递；模型可以继续生成，同轮更新也可以等待结果。
            pendingSent.await()
            previewReady.await()?.let { preview ->
                publish(taskResult(task) + (A2uiToolResult.DOCUMENT_KEY to A2uiToolResult.encode(preview)))
            }
            result.await().also { publish(it) }
        }
        entries += Entry(task, result, pendingSent, liveDelivery)
        return taskResult(task)
    }
}

internal fun taskResult(task: A2uiGenerationTask): Map<String, Any> = mapOf(
    "success" to (task.status != A2uiGenerationStatus.FAILED && task.status != A2uiGenerationStatus.CANCELLED),
    "status" to task.status.name.lowercase(),
    "surfaceId" to task.surfaceId,
    A2uiGenerationTaskCodec.KEY to A2uiGenerationTaskCodec.encode(task),
)
