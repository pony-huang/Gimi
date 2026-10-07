package github.ponyhuang.gimi.data.agent.model

import com.google.adk.kt.logging.LoggerFactory
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.LocalModelRuntimeConfig
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelLoadPhase
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelLoadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 已初始化模型及关闭动作；原生 SDK 对象仅保存在 data 层。 */
internal data class LocalModelHandle(val model: Model, val close: () -> Unit)

/** 单个常驻引擎槽位；加载、生成和释放使用同一把锁，避免关闭仍在推理的模型。 */
internal class LoadedLocalModelPool(
    private val open: (String, LocalModelRuntimeConfig) -> LocalModelHandle,
) {
    private val logger = LoggerFactory.getLogger(LoadedLocalModelPool::class)
    private val mutex = Mutex()
    private var handle: LocalModelHandle? = null
    private var config: LocalModelRuntimeConfig? = null
    private var preparedTarget: Pair<String, LocalModelRuntimeConfig>? = null
    private val mutableState = MutableStateFlow(LocalModelLoadState())
    val state = mutableState.asStateFlow()

    suspend fun prepare(name: String, target: LocalModelRuntimeConfig) = withContext(Dispatchers.IO) {
        mutex.withLock {
            preparedTarget = name to target
            load(name, target)
        }
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        mutex.withLock { preparedTarget = null; close(); mutableState.value = LocalModelLoadState() }
    }

    suspend fun remove(block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        mutex.withLock {
            preparedTarget = null
            close()
            try { block() } finally { mutableState.value = LocalModelLoadState() }
        }
    }

    suspend fun unavailable(name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            preparedTarget = null
            close()
            mutableState.value = LocalModelLoadState(name, LocalModelLoadPhase.Failed)
        }
    }

    fun model(name: String, target: LocalModelRuntimeConfig): Model = object : Model {
        override val name: String = name
        override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
            mutex.withLock {
                // 后台 Agent/语音入口仍可按需加载，但聊天预加载得到的相同引擎直接复用。
                try {
                    val loaded = load(name, target) ?: error("Local model initialization failed")
                    emitAll(loaded.model.generateContent(request, stream))
                } catch (cancelled: CancellationException) {
                    close()
                    mutableState.value = LocalModelLoadState()
                    throw cancelled
                } catch (error: Exception) {
                    close()
                    mutableState.value = LocalModelLoadState(name, LocalModelLoadPhase.Failed)
                    throw error
                } finally {
                    // 只有聊天选中并预加载的模型常驻；后台临时模型仍在请求结束后释放。
                    if (preparedTarget != (name to target)) {
                        close()
                        mutableState.value = LocalModelLoadState()
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun load(name: String, target: LocalModelRuntimeConfig): LocalModelHandle? {
        currentCoroutineContext().ensureActive()
        if (state.value.modelId == name && config == target && handle != null) return handle
        close()
        mutableState.value = LocalModelLoadState(name, LocalModelLoadPhase.Loading)
        try {
            val opened = open(name, target)
            handle = opened
            config = target
            // 原生初始化无法在中间抢占；取消后立即关闭，不发布过期的 Ready。
            currentCoroutineContext().ensureActive()
            mutableState.value = LocalModelLoadState(name, LocalModelLoadPhase.Ready)
            return opened
        } catch (cancelled: CancellationException) {
            close()
            mutableState.value = LocalModelLoadState()
            throw cancelled
        } catch (error: Exception) {
            // 原生初始化是异常边界；失败状态供 UI 重试，不能吞掉取消。
            initializationFailed(name, error)
            return null
        } catch (error: LinkageError) {
            initializationFailed(name, error)
            return null
        }
    }

    private fun initializationFailed(name: String, error: Throwable) {
        close()
        logger.warn(error) { "Local model initialization failed" }
        mutableState.value = LocalModelLoadState(name, LocalModelLoadPhase.Failed)
    }

    private fun close() {
        val previous = handle
        handle = null
        config = null
        previous?.close?.invoke()
    }
}
