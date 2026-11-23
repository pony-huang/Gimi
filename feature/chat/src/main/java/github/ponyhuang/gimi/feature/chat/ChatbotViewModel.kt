package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.WebCitation
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.domain.conversation.chatbot.ChatbotRules
import github.ponyhuang.gimi.domain.conversation.model.*
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileEntry
import github.ponyhuang.gimi.domain.conversation.repository.UploadedFileRegistry
import github.ponyhuang.gimi.domain.conversation.repository.VendorFileGateway
import github.ponyhuang.gimi.domain.conversation.runtime.AgentProfile
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.runtime.ChatbotConversationPartition
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechPlaybackRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechRecognitionRepository
import github.ponyhuang.gimi.domain.voice.repository.SpeechSettingsRepository
import github.ponyhuang.gimi.domain.conversation.usecase.toView
import github.ponyhuang.gimi.domain.voice.usecase.markdownToSpeechText
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Chatbot 一轮的展示结局；完成态由持久化事件本身表达，其余标记只存活于当前进程。 */
enum class ChatbotTurnOutcome { FAILED, STOPPED, INTERRUPTED, VOICE_INTERRUPTED, REFUSED }

/**
 * 独立 Chatbot 状态；chat 是传给现有无状态聊天组件的展示投影。
 *
 * @property sessionId 当前 Chatbot 会话 id，空串表示会话尚未解析完成。
 * @property selection 当前会话锁定的模型选择；语音先建的会话在文本首条提交时锁定。
 * @property options 会话级工具与思考配置，与助手共用同一契约。
 * @property draft 当前会话的输入草稿；只在进程内存在，不跨进程恢复。
 * @property messageOutcomes 助手消息 id → 本轮结局，用于渲染拒答、失败与中断。
 */
data class ChatbotUiState(
    val sessionId: String = "",
    val selection: ModelSelection? = null,
    val options: ConversationToolConfiguration = ChatbotRules.defaultToolConfiguration(null),
    val chat: ChatUiState = ChatUiState(isInitializing = true),
    val uploadedFiles: List<UploadedFileEntry> = emptyList(),
    val draft: MessageData = MessageData(),
    val messageOutcomes: Map<String, ChatbotTurnOutcome> = emptyMap(),
    /** 待呈现的一次性提示；Route 呈现后经 [ChatbotAction.ConsumeNotice] 清除。 */
    val notice: ChatbotNotice? = null,
)

/** Chatbot 一次性提示；文案由 Route 按枚举本地化，以 UiState 字段承载。 */
enum class ChatbotNotice {
    /** 尚未配置或选择可用的聊天模型。 */
    CONFIGURE_MODEL,

    /** 当前会话仍在处理或并发任务已满，需要稍后再发。 */
    SEND_BUSY,

    /** 目标会话受保护，不允许该操作。 */
    PROTECTED_SESSION,

    /** 本轮执行失败，可重试。 */
    FAILED,

    /** 当前会话已经是新会话，无需重复创建。 */
    ALREADY_NEW_CONVERSATION,
}

/** Chatbot 的用户意图，执行、上传和临时业务状态均由 ViewModel 持有。 */
sealed interface ChatbotAction {
    /** 新建独立空会话。 */
    data object NewConversation : ChatbotAction
    /** 恢复指定会话的消息与草稿。 */
    data class SwitchSession(val id: String) : ChatbotAction
    /** 选择模型，已锁定时交由领域规则判断是否新建会话。 */
    data class SelectModel(val selection: ModelSelection) : ChatbotAction
    /** 请求删除一个可删除会话。 */
    data class DeleteSession(val id: String) : ChatbotAction
    /** 修改后续轮次的思考开关。 */
    data class SetThinking(val enabled: Boolean) : ChatbotAction
    /** 修改后续轮次的思考强度。 */
    data class SetEffort(val effort: ReasoningEffort) : ChatbotAction
    /** 修改当前模型联网工具的启用状态。 */
    data class SetTool(val toolId: String, val functionId: String, val enabled: Boolean) : ChatbotAction
    /** 停止当前会话的执行。 */
    data object Stop : ChatbotAction
    /** 使用原用户消息重试最近失败或停止的轮次。 */
    data object Retry : ChatbotAction
    /** 确认一次性提示已呈现，清空 [ChatbotUiState.notice]。 */
    data object ConsumeNotice : ChatbotAction
}

/** 运行失败的类别，决定提示文案；具体异常细节只进日志。 */
private enum class ChatbotRunFailure { CONFIGURATION, NETWORK, UPLOAD, STORAGE }

/** 进程内记录的一轮结局：持久化事件表达完成语义，这里只记住失败/中断与重试载荷。 */
private data class ChatbotSessionOutcome(
    val outcome: ChatbotTurnOutcome,
    val failure: ChatbotRunFailure? = null,
    val retryText: String = "",
)

@HiltViewModel
class ChatbotViewModel @Inject constructor(
    @ChatbotConversationPartition private val conversations: ConversationRepository,
    private val chatAgent: ChatAgentRepository,
    private val catalog: ModelCatalogRepository,
    private val runtimeGate: AgentRuntimeGate,
    private val recognition: SpeechRecognitionRepository,
    private val playback: SpeechPlaybackRepository,
    private val speechSettings: SpeechSettingsRepository,
    private val fileRegistry: UploadedFileRegistry,
    private val fileGateway: VendorFileGateway,
    private val attachmentsRepository: github.ponyhuang.gimi.domain.conversation.repository.ChatAttachmentRepository,
    private val officialToolSupport: OfficialToolSupport,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatbotUiState())
    val uiState = _uiState.asStateFlow()
    private val _attachFileRequests = MutableSharedFlow<UploadedFileEntry>(extraBufferCapacity = 1)
    val attachFileRequests = _attachFileRequests.asSharedFlow()
    private val runs = mutableMapOf<String, Job>()

    /** 一次性提示以 UiState 承载：呈现前不丢失，呈现后由 Route 经 ConsumeNotice 清除。 */
    private fun notify(notice: ChatbotNotice) {
        _uiState.update { it.copy(notice = notice) }
    }
    // 三个 surface 共用的文档上传编排：chip 阶段预热与发送时补传共用任务去重与注册表记录，
    // 上传成功即登记"上传的文件"列表（与是否发送无关）。
    private val fileUploads = ChatFileUploadCoordinator(
        scope = viewModelScope,
        gateway = fileGateway,
        registry = fileRegistry,
        resolveConnection = { selection -> catalog.resolveConnection(selection) },
        // Chatbot 的文档入口随厂商矩阵开启；不支持上传的厂商没有文档草稿流入。
        isFileUploadEnabled = { selection -> templateIdOf(selection) in FILE_UPLOAD_TEMPLATE_IDS },
    )
    /** 未提交的输入草稿：只在进程内存在，进程结束即丢。 */
    private val drafts = mutableMapOf<String, MessageData>()
    /** 最近一轮的进程内结局；重试成功后清除。 */
    private val outcomes = mutableMapOf<String, ChatbotSessionOutcome>()
    /** 正在流式展示的助手消息；收尾以 ADK 落盘事件为准替换。 */
    private val streaming = mutableMapOf<String, Message>()
    private var conversationsList = emptyList<Conversation>()
    private var registeredFiles = emptyList<UploadedFileEntry>()
    private var instances = emptyList<ModelServiceInstance>()
    private var activeId: String? = null
    private var messages = emptyList<Message>()
    private var selection: ModelSelection? = null
    private var options = ChatbotRules.defaultToolConfiguration(null)
    private var recognitionAvailable = false
    private var initialized = false
    /** 当前会话的历史是否已从共享存储加载；加载前只做列表投影。 */
    private var loaded = false
    /** 已加载历史对应的内容版本；语音等外部写入据此触发重载。 */
    private var loadedRevision = 0L
    /** 当前会话厂商的已上传文件查询键；厂商随模型选择变化，驱动注册表查询切换。 */
    private val fileVendor = MutableStateFlow<String?>(null)
    private val voice = VoiceDictationCoordinator(viewModelScope, { recognition.transcribe(it) }) { mode ->
        _uiState.update { it.copy(chat = it.chat.copy(voiceTranscriptionMode = mode)) }
    }

    init {
        viewModelScope.launch {
            operation {
                conversations.refresh()
                activeId = conversations.lastConversationId()
                initialized = true
            }
            // 会话列表是常驻收集：初始化失败也继续收集等待存储恢复；
            // 单次迭代失败只提示一次，不能让异常终止收集导致列表永久冻结。
            conversations.conversations.collect { values ->
                operation {
                    conversationsList = values
                    when {
                        values.isEmpty() -> createSession()
                        activeId !in values.map { it.id } -> {
                            activeId = values.first().id
                            loadActive()
                        }
                        !loaded -> loadActive()
                        else -> publish()
                    }
                }
            }
        }
        viewModelScope.launch {
            conversations.conversationContentRevisions.collect { refreshHistoryIfStale() }
        }
        viewModelScope.launch { catalog.observeInstances().collect { values ->
            instances = values.map { instance ->
                // Chatbot 的文档附件只走厂商 file 引用（本地路径没有工具可读），上传能力随
                // 厂商矩阵开启；不支持的厂商不提供文档入口。
                val uploadSupported = instance.templateId in FILE_UPLOAD_TEMPLATE_IDS
                // Chatbot 的工具开关在会话内自己管理，实例级"厂商工具"偏好不参与门控。
                instance.copy(isFileUploadEnabled = uploadSupported, isOfficialToolsEnabled = true, connections = instance.connections
                    .filter { it.capability == ServiceCapability.Conversation &&
                        supportsChatbotProtocol(instance.templateId, it.protocol) }
                    .map { connection -> connection.copy(models = connection.models.map { model ->
                        model.copy(multimodal = model.multimodal.copy(
                            audioInput = null,
                            documentInput = if (uploadSupported) model.multimodal.documentInput else null,
                        ))
                    }) })
                    .withDerivedUploadCapabilities()
            }.filter { instance -> instance.isEnabled && instance.connections.any { it.models.isNotEmpty() } }
            if (initialized && activeId != null) assignInitialModel()
            publish()
        } }
        viewModelScope.launch {
            fileVendor.flatMapLatest { vendor ->
                if (vendor.isNullOrBlank()) flowOf(emptyList()) else fileRegistry.entriesFor(vendor)
            }.collect { registeredFiles = it; publish() }
        }
        // 上传状态变化即重投影：chip 的 fileId 回填与发送门控都读 draftUploadStates。
        viewModelScope.launch { fileUploads.states.collect { publish() } }
        viewModelScope.launch { recognition.availability.collect { recognitionAvailable = it; publish() } }
        viewModelScope.launch { playback.state.collect { publish() } }
        viewModelScope.launch { speechSettings.autoSpeakEnabled.collect { publish() } }
    }

    /** 当前选择归属厂商的 Chatbot 检索工具目录声明；无检索工具的厂商返回空集。 */
    private fun chatbotSpecs(templateId: String?): List<OfficialToolSpec> =
        officialToolSupport.all.filter { it.toolId in CHATBOT_WEB_SEARCH_TOOL_IDS[templateId].orEmpty() }

    private fun templateIdOf(selection: ModelSelection?): String? =
        selection?.let { value -> instances.firstOrNull { it.id == value.instanceId }?.templateId }

    private fun firstSelection(): ModelSelection? = instances.firstNotNullOfOrNull { instance ->
        instance.connections.firstNotNullOfOrNull { connection ->
            connection.models.firstOrNull {
                instance.templateId != "openai" || it.id != OPENAI_CHATBOT_SEARCH_MODEL
            }?.let { ModelSelection(instance.id, connection.id, it.id) }
        }
    }

    /** 目录实例可能在会话创建之后才到达：补选默认模型时按厂商重播工具默认勾选。 */
    private suspend fun assignInitialModel() {
        if (selection != null) return
        val id = activeId ?: return
        val picked = firstSelection() ?: return
        selection = picked
        options = ChatbotRules.defaultToolConfiguration(templateIdOf(picked))
        conversations.setConversationModel(id, ModelSelectionCodec.encode(picked))
        conversations.setConversationToolConfiguration(id, options)
        publish()
    }

    /** 读取当前会话的模型与工具元数据，并把「当前会话」指针落到 Chatbot 分区。 */
    private suspend fun loadActive() {
        val id = activeId ?: return
        val payload = conversations.activateConversation(id, selection?.let(ModelSelectionCodec::encode).orEmpty())
        selection = ModelSelectionCodec.decode(payload) ?: selection
        options = conversations.conversationToolConfiguration(id)
            ?: ChatbotRules.defaultToolConfiguration(templateIdOf(selection)).also {
                conversations.setConversationToolConfiguration(id, it)
            }
        // 版本先于读取采样：加载期间的新写入会留下更高版本，触发下一次重载。
        val revision = conversations.conversationContentRevisions.value[id] ?: 0L
        messages = conversations.loadMessages(id).orEmpty()
        loadedRevision = revision
        loaded = true
        publish()
    }

    private suspend fun createSession(selected: ModelSelection? = firstSelection()) {
        val id = conversations.createConversation(
            initialModel = selected?.let(ModelSelectionCodec::encode).orEmpty(),
            activate = true,
            initialToolConfiguration = ChatbotRules.defaultToolConfiguration(templateIdOf(selected)),
        )
        if (id.isBlank()) {
            notify(ChatbotNotice.FAILED)
            return
        }
        activeId = id
        selection = selected
        options = ChatbotRules.defaultToolConfiguration(templateIdOf(selected))
        messages = emptyList()
        loadedRevision = conversations.conversationContentRevisions.value[id] ?: 0L
        outcomes.remove(id)
        drafts[id] = MessageData()
        loaded = true
        publish()
    }

    fun onAction(action: ChatbotAction) {
        if (action == ChatbotAction.Stop) { activeId?.let { runs[it]?.cancel(ChatbotStopped()) }; return }
        if (action == ChatbotAction.ConsumeNotice) { _uiState.update { it.copy(notice = null) }; return }
        viewModelScope.launch { operation {
            when (action) {
                ChatbotAction.NewConversation -> {
                    val currentId = activeId
                    val isCurrentNew = !currentId.isNullOrBlank() &&
                        !_uiState.value.chat.isInitializing &&
                        runs[currentId]?.isActive != true &&
                        messages.isEmpty() &&
                        _uiState.value.chat.messages.isEmpty()
                    if (isCurrentNew) {
                        notify(ChatbotNotice.ALREADY_NEW_CONVERSATION)
                        return@operation
                    }
                    createSession(selection ?: firstSelection())
                }
                is ChatbotAction.SwitchSession -> if (conversationsList.any { it.id == action.id }) {
                    activeId = action.id
                    loadActive()
                }
                is ChatbotAction.SelectModel -> {
                    val current = selection ?: return@operation
                    val id = activeId ?: return@operation
                    if (runs[id]?.isActive == true) return@operation
                    when {
                        // 已提交首条消息即锁定：换模型须新建会话，同模型换实例保持不变。
                        messages.isNotEmpty() && ChatbotRules.requiresNewConversation(current.modelId, action.selection.modelId) ->
                            createSession(action.selection)
                        messages.isNotEmpty() -> Unit
                        else -> {
                            // 换模型即弃当前草稿的在途上传：附件随后按新厂商重建。
                            fileUploads.clear(drafts[id]?.attachments.orEmpty().map(DraftAttachment::reference))
                            val vendorChanged = templateIdOf(current) != templateIdOf(action.selection)
                            selection = action.selection
                            // 厂商切换后旧厂商的工具勾选无意义：重置为新厂商默认（检索工具全启用）。
                            if (vendorChanged) options = ChatbotRules.defaultToolConfiguration(templateIdOf(action.selection))
                            conversations.setConversationModel(id, ModelSelectionCodec.encode(action.selection))
                            conversations.setConversationToolConfiguration(id, options)
                            drafts[id] = drafts[id]?.copy(
                                attachments = drafts[id]?.attachments.orEmpty().filter { draft -> draft.fileId == null },
                            ) ?: MessageData()
                            publish()
                        }
                    }
                }
                is ChatbotAction.DeleteSession -> {
                    if (action.id == activeId || runs[action.id]?.isActive == true) {
                        notify(ChatbotNotice.PROTECTED_SESSION)
                    } else {
                        // Chatbot 的模型上下文就是该分区里的 ADK 会话，删除会话即清理上下文。
                        conversations.deleteConversation(action.id)
                        fileUploads.clear(drafts.remove(action.id)?.attachments.orEmpty().map(DraftAttachment::reference))
                        outcomes.remove(action.id)
                    }
                }
                is ChatbotAction.SetThinking -> updateOptions { it.copy(thinkingEnabled = action.enabled) }
                is ChatbotAction.SetEffort -> updateOptions { it.copy(reasoningEffort = action.effort, thinkingEnabled = true) }
                is ChatbotAction.SetTool -> updateOptions { current ->
                    val spec = chatbotSpecs(templateIdOf(selection)).firstOrNull { it.toolId == action.toolId }
                    current.setOfficialFunctionEnabled(
                        toolId = action.toolId,
                        functionId = action.functionId,
                        supportedFunctionIds = spec?.staticFunctionIds?.toSet().orEmpty(),
                        enabled = action.enabled,
                    )
                }
                ChatbotAction.Retry -> {
                    val failedTurn = _uiState.value.chat.failedTurn ?: return@operation
                    val lastUserText = failedTurn.userMessage.textParts.joinToString("") { it.text }
                    launchRun(
                        text = outcomes[activeId]?.retryText?.takeIf(String::isNotBlank) ?: lastUserText,
                        retry = true,
                        submission = ChatSubmission {},
                    )
                }
                ChatbotAction.Stop -> Unit
            }
        } }
    }

    private suspend fun updateOptions(transform: (ConversationToolConfiguration) -> ConversationToolConfiguration) {
        val id = activeId ?: return
        if (runs[id]?.isActive == true) return
        options = transform(options)
        conversations.setConversationToolConfiguration(id, options)
        publish()
    }

    fun saveDraft(sessionId: String, draft: MessageData) {
        if (sessionId.isBlank()) return
        drafts[sessionId] = draft
        publish()
    }

    fun send(text: String, attachments: List<DraftAttachment>, onResult: (ChatSubmissionResult) -> Unit) {
        launchRun(text, attachments, submission = ChatSubmission(onResult))
    }

    /**
     * 一轮运行的唯一入口：模型上下文与落盘由 CHATBOT profile 的 ADK Runner 负责，
     * 本方法只准备附件、收集事件做流式展示，并在收尾时刷新共享会话。
     */
    private fun launchRun(
        text: String,
        attachments: List<DraftAttachment> = emptyList(),
        retry: Boolean = false,
        submission: ChatSubmission,
    ) {
        val id = activeId
        val selected = selection
        if (id == null || selected == null) {
            submission.complete(ChatSubmissionResult.REJECTED)
            notify(ChatbotNotice.CONFIGURE_MODEL)
            return
        }
        // 会话忙/并发满与未配置模型是两类原因，提示文案必须区分。
        if (runs[id]?.isActive == true || runs.values.count { it.isActive } >= MAX_PARALLEL_TASKS) {
            submission.complete(ChatSubmissionResult.REJECTED)
            notify(ChatbotNotice.SEND_BUSY)
            return
        }
        val accumulator = ChatbotRunAccumulator()
        runs[id] = viewModelScope.launch {
            var lease: AgentRunLease? = null
            try {
                requireChatbotConnection(selected)
                require(attachments.size <= 20 && attachments.none { it.category == AttachmentCategory.AUDIO || it.sizeBytes > 100L * 1024 * 1024 })
                lease = runtimeGate.acquire(AgentTaskSource.CHATBOT, id)
                // 文件上传按厂商矩阵收口：不支持的厂商忽略文档附件（不上传也不发送）。
                val uploadSupported = templateIdOf(selected) in FILE_UPLOAD_TEMPLATE_IDS
                // 重试恢复 SDK 保存的原执行：不重复归档、不重发用户消息。
                val prepared = if (retry) emptyList() else prepareAttachments(id, selected, attachments, uploadSupported)
                val execution = chatAgent.createExecution(
                    sessionId = id,
                    selection = selected,
                    toolConfiguration = options,
                    a2uiCapabilities = null,
                    profile = AgentProfile.CHATBOT,
                )
                submission.complete(ChatSubmissionResult.ACCEPTED)
                outcomes.remove(id)
                drafts[id] = MessageData()
                // 发送被接受即本轮附件已消费：清理上传状态，不让终态跨轮残留。
                fileUploads.clear(attachments.map(DraftAttachment::reference))
                if (!retry) {
                    // 乐观插入本轮用户消息；收尾时以 ADK 落盘的历史为准。
                    messages = messages + Messages.fromUser(text, prepared.toFileAttachments())
                }
                publish()
                val sent = if (uploadSupported) prepared.toFileAttachments()
                    else prepared.filterNot { it.category == AttachmentCategory.DOCUMENT }.toFileAttachments()
                execution.send(text, sent, retry).collect { event ->
                    accumulator.accept(event)
                    accumulator.message()?.let { streaming[id] = it }
                    publish()
                }
                // 事件流正常收尾即本轮完成：SDK 的错误事件在流里抛异常，不走这里。
                accumulator.finishReason?.let { reason ->
                    when (reason) {
                        ChatFinishReason.SAFETY -> outcomes[id] = ChatbotSessionOutcome(ChatbotTurnOutcome.REFUSED, retryText = text)
                        ChatFinishReason.MAX_TOKENS -> outcomes[id] = ChatbotSessionOutcome(ChatbotTurnOutcome.STOPPED, retryText = text)
                        else -> outcomes.remove(id)
                    }
                }
                if (speechSettings.autoSpeakEnabled.value && activeId == id) {
                    accumulator.text().takeIf(String::isNotBlank)?.let { reply ->
                        // 有正文必有内容事件；播放 key 与气泡上的 message.id（落盘事件 id）一致。
                        val replyId = requireNotNull(accumulator.messageId)
                        playback.play("$replyId:speech", markdownToSpeechText(reply))
                    }
                }
            } catch (cancelled: CancellationException) {
                outcomes[id] = ChatbotSessionOutcome(ChatbotTurnOutcome.STOPPED, retryText = text)
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                Log.w(TAG, "Chatbot run rejected", failure)
                outcomes[id] = ChatbotSessionOutcome(ChatbotTurnOutcome.FAILED, ChatbotRunFailure.CONFIGURATION, text)
                notify(ChatbotNotice.CONFIGURE_MODEL)
            } catch (failure: ChatbotRunException) {
                Log.w(TAG, "Chatbot run failed: ${failure.failure}", failure)
                outcomes[id] = ChatbotSessionOutcome(ChatbotTurnOutcome.FAILED, failure.failure, text)
                notify(if (failure.failure == ChatbotRunFailure.CONFIGURATION) ChatbotNotice.CONFIGURE_MODEL else ChatbotNotice.FAILED)
            } catch (failure: Exception) {
                // Agent 侧的失败以异常抛出（模型错误、工具异常等）：轮次落失败态，提示重试。
                Log.w(TAG, "Chatbot run failed", failure)
                outcomes[id] = ChatbotSessionOutcome(ChatbotTurnOutcome.FAILED, ChatbotRunFailure.NETWORK, text)
                notify(ChatbotNotice.FAILED)
            } finally {
                lease?.release()
                withContext(NonCancellable) { reload(id) }
                // Job 在 finally 里仍为 isActive=true；必须先摘除再 publish，
                // 否则最后一次投影会把 isAgentRunning 固定为 true，界面卡在「正在整理回复」。
                runs.remove(id, coroutineContext[Job])
                publish()
            }
        }
    }

    /**
     * 收尾时以 ADK 落盘事件为准刷新本地历史，并刷新抽屉行（标题与预览由共享仓储解析）。
     * 采用走 [adoptLoadedHistory] 身份保持式合并：内容等价的消息保留内存身份，
     * 避免收尾换 id 触发整轮 item key 变化与静态重 parse（气泡刷新式闪烁）。
     */
    private suspend fun reload(id: String) {
        val live = streaming.remove(id)
        if (activeId == id) {
            val revision = conversations.conversationContentRevisions.value[id] ?: 0L
            // 把流式消息一并纳入配对：内容等价时保留其对象身份（partial/turnComplete 同步为落盘值）。
            // 配合累计器采用真实事件 id，收尾 reload 前后回答 item 的 key 与渲染状态全程稳定。
            messages = adoptLoadedHistory(messages + listOfNotNull(live), conversations.loadMessages(id).orEmpty())
            loadedRevision = revision
        }
        conversations.refreshConversation(id)
    }

    /** 语音等外部写入推进内容版本后重载当前会话；本会话执行中由收尾 reload 负责，不在此覆盖。 */
    private suspend fun refreshHistoryIfStale() {
        while (true) {
            val id = activeId ?: return
            if (!loaded || runs[id]?.isActive == true) return
            val revision = conversations.conversationContentRevisions.value[id] ?: 0L
            if (revision <= loadedRevision) return
            val latest = conversations.loadMessages(id).orEmpty()
            // 加载期间切会话或起了新任务：放弃本次读取，避免旧快照覆盖新状态。
            if (activeId != id || runs[id]?.isActive == true) return
            messages = adoptLoadedHistory(messages, latest)
            loadedRevision = revision
            publish()
        }
    }

    /** 发送与候选列表采用同一协议规则。 */
    private fun requireChatbotConnection(selection: ModelSelection?) {
        val connection = selection?.let { catalog.resolveConnection(it) }
            ?: throw ChatbotRunException(ChatbotRunFailure.CONFIGURATION)
        if (!supportsChatbotProtocol(templateIdOf(selection), connection.protocol)) {
            throw ChatbotRunException(ChatbotRunFailure.CONFIGURATION)
        }
    }

    /** 归档本轮附件；有上传能力的厂商再把文档换成厂商 file 引用。 */
    private suspend fun prepareAttachments(
        sessionId: String,
        selection: ModelSelection,
        attachments: List<DraftAttachment>,
        uploadSupported: Boolean,
    ): List<DraftAttachment> {
        val prepared = cancellationAwareRunCatching {
            attachmentsRepository.read(sessionId, attachments)
        }.getOrElse { throw ChatbotRunException(ChatbotRunFailure.UPLOAD, it) }.map { file ->
            DraftAttachment(
                reference = file.payloadReference ?: VendorFileReference.uri(requireNotNull(file.fileVendor), requireNotNull(file.fileId)),
                displayName = file.displayName, mimeType = file.mimeType, sizeBytes = file.sizeBytes,
                category = file.category, fileId = file.fileId, fileVendor = file.fileVendor,
            )
        }.toMutableList()
        if (!uploadSupported) return prepared
        // 跨厂商复用的 file_id-only 草稿没有本地副本可重传：与"厂商不支持文档"同一
        // 收口策略整条丢弃，而不是带着外厂 file_id 上传必然失败拖垮整轮。
        val vendor = templateIdOf(selection)
        val uploadable = prepared
            .filterNot { draft ->
                draft.category == AttachmentCategory.DOCUMENT &&
                    draft.isVendorUploaded && draft.fileVendor != vendor &&
                    VendorFileReference.parse(draft.reference) != null
            }
            .toMutableList()
        if (uploadable.size != prepared.size) {
            Log.w(TAG, "Dropped cross-vendor file-only drafts without local copy")
        }
        uploadable.indices.forEach { index ->
            currentCoroutineContext().ensureActive()
            if (uploadable[index].category == AttachmentCategory.DOCUMENT) {
                // 发送时补传与 chip 阶段共用同一编排：同厂商已就绪短路、同引用任务去重、
                // 厂商切换后的本地副本换厂商重传；终态非 Ready 统一落 UPLOAD 轮次失败。
                when (val state = fileUploads.awaitUpload(uploadable[index], selection)) {
                    is DraftUploadState.Ready ->
                        uploadable[index] = uploadable[index].copy(fileId = state.fileId, fileVendor = state.vendor)
                    else -> throw ChatbotRunException(ChatbotRunFailure.UPLOAD)
                }
            }
        }
        return uploadable
    }

    /** 触发文档草稿的厂商上传（chip 阶段预热）；重试复用同一路径，成功即写注册表。 */
    fun uploadDocumentDraft(draft: DraftAttachment) {
        val selected = selection ?: return
        fileUploads.upload(draft, selected)
    }

    fun transcribeVoice(pcm: ByteArray, mode: VoiceInputMode, draft: MessageData, onResult: (Result<VoiceDictationResult>) -> Unit) =
        voice.start(pcm, mode, draft, onResult)
    fun cancelTranscription() = voice.cancel()
    fun togglePlayback(id: String, text: String) = playback.toggle(id, markdownToSpeechText(text))
    fun recentAction(action: RecentConversationsAction) {
        _uiState.update { it.copy(chat = it.chat.reduceRecentConversations(action)) }
    }
    fun attachFile(entry: UploadedFileEntry) {
        if (uiState.value.uploadedFiles.any { it.fileId == entry.fileId }) _attachFileRequests.tryEmit(entry)
    }
    fun toggleTimeline(id: String) { _uiState.update { state -> state.copy(chat = state.chat.copy(
        expandedActivityGroupIds = state.chat.expandedActivityGroupIds.let { if (id in it) it - id else it + id },
    )) } }

    private companion object {
        const val TAG: String = "ChatbotViewModel"
        const val MAX_PARALLEL_TASKS: Int = 3
    }

    private suspend fun operation(block: suspend () -> Unit) {
        cancellationAwareRunCatching { block() }.onFailure { notify(ChatbotNotice.FAILED) }
    }

    private fun publish() {
        val id = activeId.orEmpty()
        val running = runs[id]?.isActive == true
        val outcome = outcomes[id]
        val projected = projectMessages(id, running, outcome)
        val retryable = projected.outcomes.values.any {
            it == ChatbotTurnOutcome.FAILED || it == ChatbotTurnOutcome.STOPPED || it == ChatbotTurnOutcome.INTERRUPTED
        }
        val failedTurn = if (retryable) {
            projected.messages.lastOrNull { message -> message.role == MessageRole.User }?.let { user ->
                ChatTurn(id = user.id, sessionId = id, userMessage = user, messages = projected.messages, status = ChatTurnStatus.FAILED)
            }
        } else null
        val instance = instances.firstOrNull { it.id == selection?.instanceId }
        val specs = chatbotSpecs(instance?.templateId)
        val old = _uiState.value.chat
        val chat = old.copy(
            messages = projected.messages,
            listItems = projected.messages.toChatListItems(TimelineActivityState(isAgentRunning = running)),
            sessionId = id,
            isAgentRunning = running,
            isInitializing = !initialized || id.isBlank(),
            currentModelSelection = selection,
            availableLLMModelSettings = instances,
            modelCatalogLoadState = CatalogLoadState.Ready,
            toolConfiguration = options,
            isSpeechRecognitionAvailable = recognitionAvailable,
            autoSpeakEnabled = speechSettings.autoSpeakEnabled.value,
            speechPlaybackState = playback.state.value,
            conversations = conversationsList,
            conversationTaskStatuses = runs.filterValues { it.isActive }
                .keys.associateWith { ConversationTaskStatus.Running(AgentTaskPhase.GENERATING) },
            officialToolDescriptors = specs.map { spec ->
                OfficialToolDescriptor(
                    id = spec.toolId,
                    sourceServiceId = instance?.id.orEmpty(),
                    sourceServiceName = instance?.name.orEmpty(),
                    functions = spec.staticFunctionIds.map { OfficialToolFunction(it, it, "") },
                )
            },
            // 上传状态以草稿引用为键，按当前会话草稿过滤即跨会话隔离；chip 的 fileId
            // 回填与发送门控都读这份投影。
            draftUploadStates = fileUploads.states.value.filterKeys {
                it in drafts[id]?.attachments.orEmpty().map(DraftAttachment::reference).toSet()
            },
            failedTurn = failedTurn,
        )
        val vendor = instance?.templateId
        fileVendor.value = vendor
        val fileEntries = registeredFiles.filter { it.vendor == vendor }
        _uiState.value = ChatbotUiState(
            sessionId = id,
            selection = selection,
            options = options,
            chat = chat,
            uploadedFiles = fileEntries,
            draft = drafts[id] ?: MessageData(),
            messageOutcomes = projected.outcomes,
        )
    }

    /**
     * 把持久化历史和进程内结局合成为展示消息：
     * 失败/停止/拒答标记落在本轮助手消息上；该轮没有任何助手消息时补一条，
     * 让错误态始终可见。原生实时转写可以随通话结束单独落盘，不据此推断文本生成中断。
     */
    private fun projectMessages(
        id: String,
        running: Boolean,
        outcome: ChatbotSessionOutcome?,
    ): ProjectedMessages {
        // 运行中先显示累计的流式回复；收尾后由落盘事件替换。
        streaming[id]?.let { live -> return ProjectedMessages(messages + live, emptyMap()) }
        if (messages.isEmpty()) return ProjectedMessages(messages, emptyMap())
        val lastIndex = messages.indexOfLast { it.role == MessageRole.User }
        if (lastIndex < 0) return ProjectedMessages(messages, emptyMap())
        val lastUser = messages[lastIndex]
        val assistantAfter = messages.subList(lastIndex + 1, messages.size).lastOrNull { it.role == MessageRole.Assistant }
        if (lastUser.isNativeRealtimeTranscript) return ProjectedMessages(messages, emptyMap())
        val effective = outcome?.outcome
            ?: if (!running && assistantAfter == null) ChatbotTurnOutcome.INTERRUPTED else null
        if (effective == null) return ProjectedMessages(messages, emptyMap())
        // 音频由语音链路保存，普通 Chatbot 重试无法恢复原语音回合。
        val displayOutcome = if (lastUser.fileAttachments.any { it.category == AttachmentCategory.AUDIO }) {
            ChatbotTurnOutcome.VOICE_INTERRUPTED
        } else {
            effective
        }
        val markerId = assistantAfter?.id ?: "${lastUser.id}:outcome"
        val withMarker = if (assistantAfter == null) {
            messages + Messages.fromAssistant(
                id = markerId,
                author = selection?.modelId.orEmpty(),
                timestamp = lastUser.timestamp,
            )
        } else {
            messages
        }
        return ProjectedMessages(withMarker, mapOf(markerId to displayOutcome))
    }

    private data class ProjectedMessages(
        val messages: List<Message>,
        val outcomes: Map<String, ChatbotTurnOutcome>,
    )
}

/** Chatbot 运行失败：类别决定提示文案，底层异常只进日志。 */
private class ChatbotRunException(
    val failure: ChatbotRunFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

/** 轮次附件 → 可发送附件：厂商引用直通，本地归档走载荷路径。 */
private fun DraftAttachment.toFileAttachment(): FileAttachment = FileAttachment(
    mimeType = mimeType,
    id = reference,
    sizeBytes = sizeBytes,
    displayName = displayName,
    payloadReference = reference.takeUnless { it.startsWith("gimi-file:") },
    fileId = fileId,
    fileVendor = fileVendor,
)

/** 本轮全部附件 → 可发送附件列表。 */
private fun List<DraftAttachment>.toFileAttachments(): List<FileAttachment> = map { it.toFileAttachment() }

/**
 * 一轮运行事件的累计器：partial 是增量，完整事件只补齐缺失的部分。
 *
 * 工具调用按调用 id 去重：SSE 下同一次调用会分多次增量下发。
 * 完成语义交给 ADK 落盘事件；这里只维护流式展示与停止原因。
 *
 * [messageId] 采用首个内容事件的真实 `Event.id`：ADK 同一次模型调用的流式增量与收尾完整事件
 * 共享同一 Event.id，且 partial 事件永不落库 —— 落盘历史的助手消息 id 与流式消息 id 因此同源。
 * 收尾 reload 后回答 item 的 LazyColumn key 不变，气泡不会被销毁重建整段重 parse（闪烁）；
 * 自动朗读的播放 key 也与气泡上的 message.id 一致。
 */
private class ChatbotRunAccumulator {
    var messageId: String? = null
        private set
    private val text = StringBuilder()
    private val thinking = StringBuilder()
    private val activities = linkedMapOf<String, FunctionCallView>()
    private var webCitations = emptyList<WebCitation>()
    var finishReason: ChatFinishReason? = null
        private set

    fun accept(event: ChatRunEvent) {
        if (event.webCitations.isNotEmpty()) webCitations = event.webCitations
        // 错误事件的停止原因不代表拒答（协议适配层把服务异常也归到 SAFETY），失败由异常路径处理。
        if (event.errorCode == null && event.errorMessage.isNullOrBlank()) {
            event.finishReason?.let { finishReason = it }
        }
        if (messageId == null && event.author != "user" &&
            (event.parts.any { it.text != null } || event.functionCalls.isNotEmpty())
        ) {
            messageId = event.id
        }
        event.functionCalls.forEach { call ->
            // 与历史回放同一 toView 口径：收尾 reload 后调用 chip 的参数摘要不变，避免文案跳变。
            activities[call.id ?: call.name] = call.toView()
        }
        if (event.partial) {
            event.parts.forEach { part ->
                val content = part.text ?: return@forEach
                if (part.thought) thinking.append(content) else text.append(content)
            }
        } else {
            // 完整事件带全量正文：增量缺失时（例如非流式响应）用它补齐，不重复累加。
            if (text.isBlank()) text.append(event.parts.filterNot { it.thought }.mapNotNull { it.text }.joinToString(""))
            if (thinking.isBlank()) thinking.append(event.parts.filter { it.thought }.mapNotNull { it.text }.joinToString(""))
        }
    }

    fun text(): String = text.toString()

    /** 当前流式助手消息；调用方只在累计器有内容后附加到列表。 */
    fun message(): Message? {
        if (text.isBlank() && thinking.isBlank() && activities.isEmpty()) return null
        // 有内容必然已消费过内容事件，messageId 一定已采用。
        val id = requireNotNull(messageId)
        return Messages.fromAssistant(id = id, author = "Chatbot").copy(
            textParts = listOf(
                TextPart("$id:thinking", thinking.toString(), true),
                TextPart("$id:text", text.toString()),
            ).filter { it.text.isNotEmpty() },
            functionCalls = activities.values.toList(),
            webCitations = webCitations,
            partial = true,
            turnComplete = false,
        )
    }
}
