package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelLoadPhase
import github.ponyhuang.gimi.domain.modelcatalog.repository.LocalModelRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 根据前台会话模型预加载；快速切换取消旧任务，Ready 必须匹配当前模型才解锁输入。 */
internal class ChatLocalModelCoordinator(
    private val uiState: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val runtime: LocalModelRuntime,
) {
    private var preparation: Job? = null

    init {
        scope.launch {
            uiState.map { state ->
                val selection = state.currentModelSelection
                val localId = state.selectedLocalModelId
                localId to (localId != null && state.availableLLMModelSettings.any { service ->
                    service.id == selection?.serviceId && service.groups.any { group -> group.models.any { it.id == selection?.modelId } }
                })
            }.distinctUntilChanged().collect { (modelId, _) ->
                prepare(modelId)
            }
        }
        scope.launch {
            runtime.state.collect { state ->
                uiState.update { it.copy(localModelLoadState = state) }
                // 停止生成会回收该引擎；仍选中本地模型时重新加载后再开放输入。
                if ((state.phase == LocalModelLoadPhase.Unloaded ||
                        state.phase == LocalModelLoadPhase.Ready && state.modelId != uiState.value.selectedLocalModelId) &&
                    preparation?.isActive != true) {
                    uiState.value.selectedLocalModelId?.let(::prepare)
                }
            }
        }
    }

    fun retry() {
        val modelId = uiState.value.selectedLocalModelId ?: return
        if (preparation?.isActive != true) prepare(modelId)
    }

    private fun prepare(modelId: String?) {
        preparation?.cancel()
        // 先保存 Job 再启动，防止 Unloaded 事件启动第二个相同加载任务。
        val job = scope.launch(start = CoroutineStart.LAZY) {
            if (modelId == null) runtime.unload() else runtime.prepare(modelId)
        }
        preparation = job
        job.start()
    }

    fun close() {
        preparation?.cancel()
        runtime.release()
    }
}
