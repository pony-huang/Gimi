package github.ponyhuang.gimi.domain.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.StateFlow

/**
 * 一次实时通话的进程级唯一所有者。会话不随页面销毁、导航跳转或前后台切换而结束；
 * 页面挂断与状态栏结束操作都必须汇合到 [end] 这一个入口。
 * 通话只在用户点「开始通话」后建立（懒启动）：页面进入仅做模式预选与可用性展示。
 */
interface RealtimeVoiceCallController {
    val state: StateFlow<RealtimeVoiceCallState>

    /**
     * 用户点「开始通话」后按预选 [mode] 建立通话；已有通话或正在结束时只复用/等待，
     * 不重复占用麦克风。Chat 模式缺会话绑定、Realtime 引擎模式缺默认模型时回落轻聊。
     */
    fun start(conversationId: String?, selection: ModelSelection?, title: String, mode: RealtimeVoiceMode)

    /**
     * 重开一个新会话：结束当前语音会话并以相同模式重建。
     * 助手（Chat）模式可换绑新的持久化会话 [conversationId]（新建会话由调用方完成）；
     * 其余模式忽略该参数，直接重开内存会话。
     */
    fun restart(conversationId: String?, title: String)

    /** 应用重新可见时补齐前台优先级；后台启动受限后由此重试，不重启通话。 */
    fun ensureForeground()

    fun selectMode(mode: RealtimeVoiceMode)

    fun toggleMute()
    fun selectAudioDevice(deviceId: Int?)
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
