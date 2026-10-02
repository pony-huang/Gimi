package github.ponyhuang.gimi.feature.mobileuse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.domain.mobileuse.MobileDisplaySession
import github.ponyhuang.gimi.domain.mobileuse.MobileTouch
import github.ponyhuang.gimi.domain.mobileuse.MobileTouchAction
import github.ponyhuang.gimi.domain.mobileuse.MobileUseRepository
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 后台应用窗口状态；不包含 AI 占用或人工接管标记，也不保存输入内容。 */
data class BackgroundAppUiState(
    val session: MobileDisplaySession? = null,
    val smallWindowEnabled: Boolean = false,
    val menuExpanded: Boolean = false,
    val settingsVisible: Boolean = false,
    val inputUnavailable: Boolean = false,
    val previewUnavailable: Boolean = false,
)

/** 本地窗口意图，与 Agent 的 observationId 无关。 */
sealed interface BackgroundAppAction {
    /** 切换更多菜单。 */
    data class Menu(val expanded: Boolean) : BackgroundAppAction
    /** 显示或隐藏窗口设置。 */
    data class Settings(val visible: Boolean) : BackgroundAppAction
    /** 保存下次打开窗口的模式。 */
    data class SmallWindow(val enabled: Boolean) : BackgroundAppAction
    /** 单指输入；保留源窗口会话身份，拒绝旧窗口事件。 */
    data class Touch(val sessionId: String, val event: MobileTouch) : BackgroundAppAction
    /** 返回当前目标 App。 */
    data object Back : BackgroundAppAction
    /** 用户主动关闭后台应用。 */
    data object Close : BackgroundAppAction
    /** 预览宿主报告绑定失败或恢复。 */
    data class PreviewFailure(val failed: Boolean) : BackgroundAppAction
    /** 关闭人工输入错误提示。 */
    data object DismissError : BackgroundAppAction
}

/**
 * 连续 MOVE 只保留最新位置，DOWN/UP/CANCEL 仍按顺序投递。
 * 不与 AI 截图等待共用锁；窗口与输入内容不会进入模型 metadata。
 */
@HiltViewModel
class BackgroundAppViewModel @Inject constructor(private val repository: MobileUseRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(BackgroundAppUiState(repository.displaySession.value, repository.smallWindowEnabled.value))
    val uiState: StateFlow<BackgroundAppUiState> = mutableState.asStateFlow()
    private val pendingTouches = ArrayDeque<BackgroundAppAction.Touch>()
    private val wakeInput = Channel<Unit>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            combine(repository.displaySession, repository.smallWindowEnabled) { session, small -> session to small }.collect { (session, small) ->
                mutableState.update {
                    if (it.session?.id != session?.id) BackgroundAppUiState(session, small)
                    else it.copy(session = session, smallWindowEnabled = small)
                }
            }
        }
        viewModelScope.launch {
            for (wake in wakeInput) {
                while (true) {
                    val next = synchronized(pendingTouches) { pendingTouches.removeFirstOrNull() } ?: break
                    if (repository.displaySession.value?.id != next.sessionId) continue
                    val result = repository.manualTouch(next.sessionId, next.event)
                    if (result.status == "input_unavailable") mutableState.update {
                        if (it.session?.id == next.sessionId) it.copy(inputUnavailable = true) else it
                    }
                }
            }
        }
    }

    fun onAction(action: BackgroundAppAction) {
        when (action) {
            is BackgroundAppAction.Menu -> mutableState.update { it.copy(menuExpanded = action.expanded) }
            is BackgroundAppAction.Settings -> mutableState.update { it.copy(settingsVisible = action.visible, menuExpanded = false) }
            is BackgroundAppAction.PreviewFailure -> mutableState.update { it.copy(previewUnavailable = action.failed) }
            BackgroundAppAction.DismissError -> mutableState.update { it.copy(inputUnavailable = false) }
            is BackgroundAppAction.SmallWindow -> viewModelScope.launch { repository.setSmallWindowEnabled(action.enabled) }
            BackgroundAppAction.Close -> uiState.value.session?.id?.let { id -> viewModelScope.launch { repository.closeSession(id) } }
            BackgroundAppAction.Back -> uiState.value.session?.id?.let { id ->
                viewModelScope.launch {
                    if (repository.manualBack(id).status == "input_unavailable") mutableState.update {
                        if (it.session?.id == id) it.copy(inputUnavailable = true) else it
                    }
                }
            }
            is BackgroundAppAction.Touch -> {
                synchronized(pendingTouches) {
                    val previous = pendingTouches.lastOrNull()
                    if (action.event.action == MobileTouchAction.MOVE && previous?.event?.action == MobileTouchAction.MOVE &&
                        previous.sessionId == action.sessionId && previous.event.gestureId == action.event.gestureId
                    ) pendingTouches.removeLast()
                    pendingTouches.addLast(action)
                }
                wakeInput.trySend(Unit)
            }
        }
    }
}
