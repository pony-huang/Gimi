package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 一次本地推理拥有的模型和关闭动作，便于在 JVM 验证原生资源释放。 */
internal data class LocalModelHandle(val model: Model, val close: () -> Unit)

/** 缓存的 Agent 不持有闲置原生引擎；每次推理独占内存并在成功、失败、取消后释放。 */
internal class RequestScopedLocalModel(
    override val name: String,
    private val inferenceMutex: Mutex,
    private val open: () -> LocalModelHandle,
) : Model {
    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        inferenceMutex.withLock {
            val handle = open()
            try { emitAll(handle.model.generateContent(request, stream)) }
            finally { handle.close() }
        }
    }.flowOn(Dispatchers.IO)
}
