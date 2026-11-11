package github.ponyhuang.gimi.data.mobileuse

/** 系统任务的最小投影；同时保留根应用和顶部应用以识别登录跳转或跨应用页面。 */
internal data class MobileAppTask(
    val displayId: Int,
    val userId: Int,
    val basePackage: String?,
    val topPackage: String?,
    val visible: Boolean,
)

/** 后台启动不能搬走用户正在看的应用，也不能夺取别的副屏的任务。 */
internal object MobileAppLaunchPolicy {
    const val STARTED = 0
    const val MAIN_SCREEN_BUSY = 1
    const val OTHER_DISPLAY_BUSY = 2
    const val INSPECTION_UNAVAILABLE = 3
    const val START_REJECTED = 4

    fun rejection(tasks: List<MobileAppTask>, packageName: String, userId: Int, targetDisplay: Int): Int? {
        val matching = tasks.filter {
            it.userId == userId && (it.basePackage == packageName || it.topPackage == packageName)
        }
        if (matching.any { it.displayId == 0 && it.visible }) return MAIN_SCREEN_BUSY
        if (matching.any { it.displayId > 0 && it.displayId != targetDisplay }) return OTHER_DISPLAY_BUSY
        return null
    }
}
