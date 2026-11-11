package github.ponyhuang.gimi.domain.voice.repository

/** 维持通话期间进程前台优先级与常驻通知的 Android 侧能力；领域层不接触服务组件。 */
interface RealtimeVoiceCallForeground {
    /** 确保前台服务已启动；重复调用只投递启动请求，不新建通话。 */
    fun start()
}
