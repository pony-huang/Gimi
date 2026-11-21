package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 进程级实时通话快照；语音页面、常驻通知与前台服务都以它作为唯一状态来源。
 * @property active 通话是否仍在占用麦克风；页面据此决定是否显示语音页。
 * @property ending 结束清理进行中；前台优先级须保持到音频资源与会话租约释放完毕。
 * @property voice 底层会话的阶段、字幕、电平与设备路由快照。
 * @property style 当前通话的对话方式。
 * @property pipeline 当前通话的语音处理链路。
 * @property switching 配置切换进行中，禁止重复切换或恢复收音。
 * @property cascadedAvailable 级联链路是否可用：识别与合成默认模型有效；级联助手还需会话绑定。
 * @property realtimeAvailable 是否已配置可用的原生实时默认模型；false 时该链路不可进入，
 *   界面据此引导用户去语音设置配置。
 * @property realtimeModelName 已配置的原生实时默认模型名；未配置时为空。
 * @property returnToCallRequested 状态栏通知请求返回语音页，界面消费后复位。
 */
data class RealtimeVoiceCallState(
    /** 单调递增的通话代次，隔离旧草稿准备与新通话。 */
    val callId: Long = 0,
    val active: Boolean = false,
    val ending: Boolean = false,
    val voice: RealtimeVoiceState = RealtimeVoiceState(),
    val style: VoiceConversationStyle = VoiceConversationStyle.CHATBOT,
    val pipeline: VoicePipeline = VoicePipeline.CASCADED,
    val switching: Boolean = false,
    val cascadedAvailable: Boolean = false,
    val semiCascadedAvailable: Boolean = false,
    val realtimeAvailable: Boolean = false,
    val realtimeModelName: String = "",
    val conversationTitle: String = "",
    /** 通话开始时固定的模型选择，供附件校验与上传使用。 */
    val modelSelection: ModelSelection? = null,
    val modelName: String = "",
    val ttsModelName: String = "",
    val sttModelName: String = "",
    val returnToCallRequested: Boolean = false,
) {
    /** 通话连接、运行和释放资源期间禁止修改设置。 */
    val configurationLocked: Boolean get() = active || ending || switching
}
