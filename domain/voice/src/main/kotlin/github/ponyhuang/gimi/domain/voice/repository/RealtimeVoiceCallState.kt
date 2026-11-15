package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection

/**
 * 进程级实时通话快照；语音页面、常驻通知与前台服务都以它作为唯一状态来源。
 * @property active 通话是否仍在占用麦克风；页面据此决定是否显示语音页。
 * @property ending 结束清理进行中；前台优先级须保持到音频资源与会话租约释放完毕。
 * @property voice 底层会话的阶段、字幕、电平与设备路由快照。
 * @property switching 模式切换进行中，禁止重复切换或恢复收音。
 * @property realtimeAvailable 是否已配置可用的 Realtime 默认模型；false 时 Realtime 模式不可进入，
 *   界面据此引导用户去默认模型页配置。
 * @property realtimeModelName 已配置的 Realtime 默认模型名；未配置时为空。
 * @property returnToCallRequested 状态栏通知请求返回语音页，界面消费后复位。
 */
data class RealtimeVoiceCallState(
    /** 单调递增的通话代次，隔离旧草稿准备与新通话。 */
    val callId: Long = 0,
    val active: Boolean = false,
    val ending: Boolean = false,
    val voice: RealtimeVoiceState = RealtimeVoiceState(),
    val mode: RealtimeVoiceMode = RealtimeVoiceMode.Simple,
    val switching: Boolean = false,
    val chatAvailable: Boolean = false,
    val realtimeAvailable: Boolean = false,
    val realtimeModelName: String = "",
    val conversationTitle: String = "",
    /** 通话开始时固定的模型选择，供附件校验与上传使用。 */
    val modelSelection: ModelSelection? = null,
    val modelName: String = "",
    val ttsModelName: String = "",
    val sttModelName: String = "",
    val returnToCallRequested: Boolean = false,
)
