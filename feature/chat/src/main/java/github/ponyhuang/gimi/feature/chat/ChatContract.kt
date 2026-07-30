package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 聊天页用户意图契约 — [ChatViewModel] 全部"发后即忘"的用户操作都经
 * [ChatViewModel.onAction] 进入，ViewModel 内部按 action 分发到现有逻辑。
 *
 * 返回值式 API 不完全走 action 语义：
 * - `send(text, attachments)`：返回值（是否接受发送）驱动 composer 的草稿清除
 *   （`consumeDraftForSend`），因此输入框路径直接调用；[Send] action 供发后即忘场景使用；
 * - `transcribeVoice(pcm16)`：suspend 请求-响应调用，由语音输入组件 await 结果；
 * - `partChannelFor(partId)`：渲染期同步查询流式文本增量 channel。
 */
sealed interface ChatAction {
    /** 发送用户消息（可附带草稿附件）。 */
    data class Send(
        val text: String,
        val draftAttachments: List<DraftAttachment> = emptyList(),
    ) : ChatAction

    /** 用户主动中断当前 turn（点击 composer 上的停止按钮）。 */
    data object StopStreaming : ChatAction

    /** 播放 / 停止某条消息的语音朗读。 */
    data class ToggleSpeechPlayback(val messageId: String, val markdown: String) : ChatAction

    /** 把用户对挂起工具调用的确认 / 拒绝决定送回 runner。 */
    data class RespondToToolConfirmation(val confirmed: Boolean) : ChatAction

    /** 启动期会话恢复：恢复上次会话 / 最近会话 / 新建空会话。 */
    data object RestoreOrCreateSession : ChatAction

    /** 开始一个全新的会话。 */
    data object NewConversation : ChatAction

    /** 切换到指定会话。 */
    data class SwitchSession(val sessionId: String) : ChatAction

    /** 触发一次会话列表刷新。 */
    data object RefreshConversations : ChatAction

    /** 删除指定会话（当前激活或进行中的会话会被拒绝并提示）。 */
    data class DeleteConversation(val sessionId: String) : ChatAction

    /** 为当前会话选择聊天模型。 */
    data class SelectModel(val selection: ModelSelection) : ChatAction

    /** 启用 / 停用某个本地工具。 */
    data class SetLocalToolEnabled(val toolId: String, val enabled: Boolean) : ChatAction

    /** 设置当前会话向模型声明函数工具的加载方式。 */
    data class SetToolAccessMode(val mode: ToolAccessMode) : ChatAction

    /** 启用 / 停用某个 MCP server。 */
    data class SetMcpServerEnabled(val serverId: String, val enabled: Boolean) : ChatAction

    /** 启用 / 停用某个官方工具的单个函数。 */
    data class SetOfficialFunctionEnabled(
        val toolId: String,
        val functionId: String,
        val enabled: Boolean,
        val supportedFunctionIds: Set<String>,
    ) : ChatAction

    /** 触发指定官方工具的函数列表异步加载（已加载的只做 marker 展开）。 */
    data class LoadOfficialToolFunctions(val toolId: String) : ChatAction

    /** 用户关闭工具配置保存失败的提示。 */
    data object ClearToolConfigurationError : ChatAction

    /** 切换夜间模式（写入明确偏好，不再跟随系统）。 */
    data class SetDarkTheme(val enabled: Boolean) : ChatAction
}

/**
 * 聊天页一次性 UI 反馈（Toast 等），由 Route 经 [ChatViewModel.effects] 通道消费；
 * Android 副作用（Toast / 导航 / 权限）只发生在 Route。
 *
 * 当前 ViewModel 产生的瞬时反馈只有 notice 一类 —— 导航与附件打开均由 Route 自主发起，
 * 不经过 ViewModel，因此不引入多余的 effect 成员。
 */
sealed interface ChatEffect {
    /** 向用户展示一条一次性提示；文案由 Route 侧解析（`ChatNotice` → stringResource / 动态文本）。 */
    data class ShowNotice(val notice: ChatNotice) : ChatEffect
}

/**
 * 一次性用户提示。对象型 notice 由 Route 映射到 string resource；
 * [Message] 携带上游（语音播放、附件校验等）给出的动态文本。
 */
sealed interface ChatNotice {
    data object ConfigureChatModel : ChatNotice
    data object ModelSwitchBlocked : ChatNotice
    data object ParallelTaskLimitReached : ChatNotice
    data object ActiveConversationDeleteBlocked : ChatNotice

    /** 一次发送混入了多种类型的附件。 */
    data object MixedAttachmentCategories : ChatNotice

    /** 附件校验时当前聊天模型已不可用。 */
    data object ChatModelUnavailable : ChatNotice

    /** 当前模型不具备该附件类别（图片 / 音频 / 文档）的输入能力。 */
    data object AttachmentCategoryUnsupported : ChatNotice

    /** 附件的 MIME 类型不受支持或单文件超过模型上限。 */
    data class AttachmentUnsupportedOrTooLarge(val displayName: String) : ChatNotice

    /** 文档附件合计大小超过单次请求上限。 */
    data object DocumentTotalSizeLimitExceeded : ChatNotice

    /** 上游（语音播放等）给出的动态文本，不经资源映射。 */
    data class Message(val text: String) : ChatNotice
}
