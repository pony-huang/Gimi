package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.cloud.usesCloudFileUpload
import github.ponyhuang.gimi.domain.conversation.cloud.LoadCloudFileChoicesUseCase
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 连接快照仅留在内存，凭据或模型变化后不发布旧连接的列表。 */
internal data class CloudFilePickerContext(val sessionId: String, val selection: ModelSelection, val service: LLMModelSetting)

internal fun ChatUiState.cloudFilePickerContext(): CloudFilePickerContext? {
    val selection = currentModelSelection ?: return null
    val service = availableLLMModelSettings.firstOrNull { it.id == selection.serviceId } ?: return null
    if (!service.isEnabled || !service.usesCloudFileUpload() || service.apiKey.isBlank()) return null
    return CloudFilePickerContext(sessionId, selection, service)
}

/** ViewModel 持有的列表加载与临时选择生命周期；Composable 不启动业务协程。 */
internal class ChatCloudFilesCoordinator(
    private val scope: CoroutineScope,
    private val loadChoices: LoadCloudFileChoicesUseCase,
    private val context: () -> CloudFilePickerContext?,
    private val state: () -> CloudFilePickerState,
    private val update: (CloudFilePickerState) -> Unit,
) {
    private var job: Job? = null
    private var loadToken: Any? = null
    private var activeContext: CloudFilePickerContext? = null

    fun contextChanged() {
        if (activeContext != null && activeContext != context()) close()
    }

    fun onAction(action: CloudFilePickerAction) {
        contextChanged()
        when (action) {
            is CloudFilePickerAction.LoadLocal -> Unit // 本地恢复由 ViewModel 的独立协调器处理。
            CloudFilePickerAction.Open -> open()
            CloudFilePickerAction.Close -> close()
            is CloudFilePickerAction.QueryChanged -> if (state().isOpen) update(state().copy(query = action.value))
            is CloudFilePickerAction.Toggle -> {
                val current = state()
                val choice = current.choices.firstOrNull { it.file.id == action.id } ?: return
                if (current.isOpen && current.canToggle(choice)) {
                    update(current.copy(selectedIds = if (action.id in current.selectedIds)
                        current.selectedIds - action.id else current.selectedIds + action.id))
                }
            }
        }
    }

    private fun open() {
        job?.cancel()
        val snapshot = context() ?: return close()
        val token = Any()
        loadToken = token
        activeContext = snapshot
        update(CloudFilePickerState(isOpen = true, isLoading = true, serviceName = snapshot.service.name))
        job = scope.launch {
            try {
                val choices = loadChoices(snapshot.service, snapshot.selection.modelId, System.currentTimeMillis())
                if (loadToken === token && activeContext == snapshot && context() == snapshot) {
                    update(state().copy(choices = choices, isLoading = false))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (loadToken === token && activeContext == snapshot && context() == snapshot) {
                    update(state().copy(isLoading = false, hasError = true))
                }
            }
        }
    }

    private fun close() {
        job?.cancel()
        job = null
        activeContext = null
        loadToken = null
        update(CloudFilePickerState())
    }
}
