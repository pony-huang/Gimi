package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.StateFlow

/**
 * 一次实时通话的进程级唯一所有者。会话不随页面销毁、导航跳转或前后台切换而结束；
 * 页面挂断与状态栏结束操作都必须汇合到 [end] 这一个入口。
 * 通话只在用户点「开始通话」后建立（懒启动）：页面进入仅做配置预选与可用性展示。
 */
interface RealtimeVoiceCallController {
    val state: StateFlow<RealtimeVoiceCallState>

    /**
     * 用户点「开始通话」后按预选的对话方式与链路建立通话；已有通话或正在结束时只复用/等待，
     * 不重复占用麦克风。级联助手缺会话绑定、原生实时缺有效模型时回落级联 Chatbot。
     */
    fun start(
        conversationId: String?,
        selection: ModelSelection?,
        title: String,
        style: VoiceConversationStyle,
        pipeline: VoicePipeline,
    )

    /** 应用重新可见时补齐前台优先级；后台启动受限后由此重试，不重启通话。 */
    fun ensureForeground()

    /** 通话中切换对话方式或链路；变化需确认后由界面调用，切换期间重复调用被忽略。 */
    fun selectConfiguration(style: VoiceConversationStyle, pipeline: VoicePipeline)

    /**
     * 通话中按当前有效配置重建会话；对话方式与链路不变但模型、音色等会话快照字段
     * 变化后由界面确认触发（重新解析有效配置），切换期间重复调用被忽略。
     */
    fun rebuild()

    fun toggleMute()
    fun setInputPaused(paused: Boolean)
    suspend fun submit(input: VoiceManualInput): VoiceInputResult
    fun retry()

    /**
     * 唯一结束入口：保持前台优先级直到旧会话收尾、麦克风/路由/播放释放并完成最终保存，
     * 随后把状态复位为无通话，让页面收起、通知移除、前台服务退出。
     */
    fun end()

    /** 状态栏通知请求返回语音页；请求驻留在状态中直到页面消费，避免后台期间被漏掉。 */
    fun requestReturnToCall()
    fun consumeReturnToCall()
}
