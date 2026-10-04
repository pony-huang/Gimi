package github.ponyhuang.gimi.feature.chat

import android.util.Log
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationSessionResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 会话恢复、新建、切换与历史版本刷新，统一协调导航版本和异步加载所有权。
 *
 * 只拥有导航/加载控制状态；消息、运行 token 与 UI 仍使用 ViewModel 的唯一状态。
 * 运行中保留内存流，空闲历史读取同时校验加载或运行 token，防止晚到结果覆盖新状态。
 */
internal class ChatSessionNavigationCoordinator(
    private val uiState: MutableStateFlow<ChatUiState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    private val sessionResolver: ConversationSessionResolver,
    private val runtimeFor: (String) -> ChatSessionRuntime,
    private val runtimeOrNull: (String) -> ChatSessionRuntime?,
    private val showRuntime: (String, Boolean) -> Unit,
    private val publishRuntime: (ChatSessionRuntime) -> Unit,
    private val clearSpeechSession: () -> Unit,
) {
    var navigationVersion = 0L
        private set
    var loadingSessionId: String? = null
        private set
    private var sessionLoadJob: Job? = null
    private var activeSessionLoadToken: Any? = null

    private fun contentRevision(sessionId: String): Long =
        repository.conversationContentRevisions.value[sessionId] ?: 0L

    /** 历史只在该会话无执行者时更新；版本留在仓库中，后台会话不依赖瞬时通知。 */
    suspend fun refreshVisibleHistoryIfStale() {
        val sessionId = uiState.value.sessionId
        val runtime = runtimeOrNull(sessionId) ?: return
        fun canReload(): Boolean = uiState.value.sessionId == sessionId &&
            !uiState.value.isInitializing && loadingSessionId == null && !runtime.isActive

        while (canReload()) {
            val revision = contentRevision(sessionId)
            if (!runtime.isLoaded || runtime.loadedContentRevision >= revision) return
            val runToken = runtime.runToken
            val messages = repository.loadMessages(sessionId) ?: return
            // 加载期间即使一次新任务已经完成，也不能用旧读取覆盖它的内存结果。
            if (!canReload() || runtime.runToken !== runToken) return
            if (runtime.loadedContentRevision > revision) return
            runtime.closePartChannels()
            runtime.messages = messages
            runtime.loadedContentRevision = revision
            publishRuntime(runtime)
        }
    }

    /**
     * 启动期会话恢复：依次尝试
     * 1. 元数据 RoomDatabase 中 `isLast=true` 的 id（仍在 ADK Room 中）；
     * 2. Room 中 `lastUpdateTime` 最大的会话（即最近活跃的）；
     * 3. 创建一个新的空会话（首次安装 / 全部被删的兜底）。
     *
     * 供 [ChatRoute] 在 `LaunchedEffect(Unit)` 内调用，让首屏打字前已经有可用 sessionId，
     * 避免依赖 `send()` 的兜底分支。仅在进程级（`uiState.value.sessionId` 为空）执行一次；同一 ViewModel 实例内多次调用安全。
     */
    fun restoreOrCreateSession() {
        if (uiState.value.sessionId.isNotBlank() || uiState.value.isInitializing) return
        val loadToken = Any()
        activeSessionLoadToken = loadToken
        uiState.update { it.copy(isInitializing = true) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val snapshot = sessionResolver.resolveCurrentOrCreate()
                val revision = contentRevision(snapshot.sessionId)
                val history = repository.loadMessages(snapshot.sessionId).orEmpty()
                currentCoroutineContext().ensureActive()
                if (activeSessionLoadToken !== loadToken) return@launch
                val runtime = runtimeFor(snapshot.sessionId)
                runtime.messages = history
                runtime.modelSelection = snapshot.modelSelection
                runtime.toolConfiguration = snapshot.toolConfiguration
                runtime.isLoaded = true
                runtime.loadedContentRevision = revision
                showRuntime(snapshot.sessionId, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Unable to restore current conversation", failure)
            } finally {
                if (activeSessionLoadToken === loadToken) {
                    activeSessionLoadToken = null
                    sessionLoadJob = null
                    uiState.update { it.copy(isInitializing = false) }
                    refreshVisibleHistoryIfStale()
                }
            }
        }
        sessionLoadJob = job
        job.start()
    }

    /** 新建会话后仅在导航版本未变化时切入；旧会话执行仍可在后台继续。 */
    fun newConversation() {
        navigationVersion++
        val navigationAtReset = navigationVersion
        scope.launch {
            val newId = createConversationWithDefaults()
            if (navigationAtReset != navigationVersion) return@launch
            if (newId.isNotBlank()) {
                switchSession(newId)
            } else {
                Log.w(TAG, "reset() failed to create a new conversation; UI state unchanged.")
            }
        }
    }

    fun switchSession(sessionId: String) {
        if (sessionId.isBlank()) return
        if (sessionId == uiState.value.sessionId && !uiState.value.isInitializing) return
        navigationVersion++
        runtimeOrNull(uiState.value.sessionId)?.closePartChannels()
        sessionLoadJob?.cancel()
        clearSpeechSession()
        val loadToken = Any()
        activeSessionLoadToken = loadToken
        loadingSessionId = sessionId
        // 让 MainScreen 中央 spinner 立刻接管，避免重新读取已结束会话时旧 messages
        // 残留闪烁；运行中的会话仍直接显示内存流，不能被历史读取打断。
        val targetRuntime = runtimeFor(sessionId)
        val requiresHistoryLoad = !targetRuntime.isLoaded || !targetRuntime.isActive
        showRuntime(sessionId, requiresHistoryLoad)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val snapshot = sessionResolver.activate(sessionId)
                currentCoroutineContext().ensureActive()
                if (activeSessionLoadToken !== loadToken) return@launch
                targetRuntime.modelSelection = snapshot?.modelSelection
                targetRuntime.toolConfiguration = snapshot?.toolConfiguration
                // 运行中的会话必须继续使用内存中的流式状态；已结束的缓存会话则
                // 每次切入都从持久化历史重建，避免后台完成后把旧的内存快照重新展示。
                if (targetRuntime.isLoaded && targetRuntime.isActive) {
                    targetRuntime.attention = SessionResultAttention.NONE
                    targetRuntime.reseedPartialChannels()
                    showRuntime(sessionId, false)
                    return@launch
                }
                val revision = contentRevision(sessionId)
                val messages = repository.loadMessages(sessionId)
                if (activeSessionLoadToken !== loadToken) return@launch
                when {
                    messages == null -> {
                        Log.i(TAG, "switchSession($sessionId): session missing; creating a fresh one.")
                        repository.discardConversationMetadata(sessionId)
                        val newId = createConversationWithDefaults()
                        if (activeSessionLoadToken !== loadToken) return@launch
                        if (newId.isNotBlank()) {
                            val newRuntime = runtimeFor(newId)
                            newRuntime.isLoaded = true
                            if (activeSessionLoadToken !== loadToken) return@launch
                            showRuntime(newId, false)
                        } else {
                            // create 失败也别把 spinner 永久卡住 — 解锁 UI 让用户能重试。
                            uiState.update { it.copy(isInitializing = false) }
                            Log.w(TAG, "switchSession($sessionId): createConversation failed; isInitializing cleared anyway.")
                        }
                    }

                    else -> {
                        // 命中：history 非空 = 旧 session；history 空 = 刚建的空 session。
                        // 一次性 commit sessionId + messages + isInitializing=false，
                        // 避免两次 messages 写导致两帧渲染。
                        targetRuntime.messages = messages
                        targetRuntime.isLoaded = true
                        targetRuntime.loadedContentRevision = revision
                        targetRuntime.attention = SessionResultAttention.NONE
                        targetRuntime.reseedPartialChannels()
                        showRuntime(sessionId, false)
                        uiState.update { state ->
                            if (state.sessionId == sessionId) {
                                state.copy(scrollToLatestRequest = state.scrollToLatestRequest + 1L)
                            } else {
                                state
                            }
                        }
                    }
                }
            } finally {
                if (activeSessionLoadToken === loadToken) {
                    activeSessionLoadToken = null
                    loadingSessionId = null
                    sessionLoadJob = null
                    refreshVisibleHistoryIfStale()
                }
            }
        }
        sessionLoadJob = job
        job.start()
    }

    /** 创建会话并固定解析得到的模型与工具配置，消息展示交由导航提交。 */
    private suspend fun createConversationWithDefaults(): String {
        val snapshot = sessionResolver.createAndActivate()
        runtimeFor(snapshot.sessionId).apply {
            modelSelection = snapshot.modelSelection
            toolConfiguration = snapshot.toolConfiguration
        }
        return snapshot.sessionId
    }

    private companion object {
        private const val TAG = "ChatSessionNavigation"
    }
}
