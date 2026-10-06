package github.ponyhuang.gimi.domain.mobileuse

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内保留的后台应用显示会话，与单轮 AI 执行占用分离。
 * @property id 显示会话标识；窗口和人工输入必须携带它，旧窗口不能操作新显示。
 * @property chatId 所属聊天的稳定标识。
 * @property displayId Android 网关创建的显示编号。
 * @property width 实际画面像素宽度。
 * @property height 实际画面像素高度。
 * @property appName 最近打开的应用名称，尚未打开应用时为空。
 * @property completionVersion 成功结束执行的计数；宿主据此隐藏窗口，尺寸更新不能重放结束事件。
 */
data class MobileDisplaySession(
    val id: String,
    val chatId: String,
    val displayId: Int,
    val width: Int,
    val height: Int,
    val appName: String? = null,
    val completionVersion: Long = 0,
)

/** 连续单指手势的阶段。 */
enum class MobileTouchAction { DOWN, MOVE, UP, CANCEL }

/**
 * 已映射到实际画面像素的单指事件；时间与 Android 输入事件使用同一单调时钟。
 * @property gestureId 同一次按下、移动和抬起的稳定标识。
 * @property eventTimeMs 本次事件的单调时间，毫秒。
 */
data class MobileTouch(
    val action: MobileTouchAction,
    val x: Float,
    val y: Float,
    val gestureId: Long,
    val eventTimeMs: Long,
)

/** 映射后的画面坐标，排除等比预览的留白区域。 */
data class MobilePoint(val x: Float, val y: Float)

/** 外部应用画面只能等比缩放，预览留白不能成为目标应用的触摸。 */
object MobileDisplayCoordinates {
    fun map(x: Float, y: Float, viewportWidth: Int, viewportHeight: Int, width: Int, height: Int): MobilePoint? {
        if (viewportWidth <= 0 || viewportHeight <= 0 || width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
        val scale = minOf(viewportWidth.toFloat() / width, viewportHeight.toFloat() / height)
        val left = (viewportWidth - width * scale) / 2
        val top = (viewportHeight - height * scale) / 2
        val px = (x - left) / scale
        val py = (y - top) / scale
        return if (px >= 0 && py >= 0 && px < width && py < height) MobilePoint(px, py) else null
    }
}

/** 显示租约的准入结果；人工操作不进入这套 AI 占用规则。 */
enum class MobileExecutionAccess { ALLOWED, UNREGISTERED, CLOSED, OTHER_CHAT, BUSY }

/**
 * 单显示生命周期规则。短同步区只修改身份状态，不能持锁等待截图、Binder 或输入。
 * 主动关闭的执行标识在进程内保持失效，恢复同一执行也不能自动重建。
 */
class MobileDisplaySessions {
    private val mutableSession = MutableStateFlow<MobileDisplaySession?>(null)
    val session: StateFlow<MobileDisplaySession?> = mutableSession.asStateFlow()
    private val executions = mutableMapOf<String, String>()
    private val closedExecutions = mutableSetOf<String>()
    private val sessionOwners = mutableSetOf<String>()
    private var activeOwner: String? = null

    @Synchronized
    fun register(owner: String, chatId: String) {
        executions[owner] = chatId
        if (session.value?.chatId == chatId) sessionOwners.add(owner)
    }

    @Synchronized
    fun claim(owner: String): MobileExecutionAccess {
        if (owner in closedExecutions) return MobileExecutionAccess.CLOSED
        val chatId = executions[owner] ?: return MobileExecutionAccess.UNREGISTERED
        if (session.value?.chatId?.let { it != chatId } == true) return MobileExecutionAccess.OTHER_CHAT
        if (activeOwner != null && activeOwner != owner) return MobileExecutionAccess.BUSY
        activeOwner = owner
        if (session.value != null) sessionOwners.add(owner)
        return MobileExecutionAccess.ALLOWED
    }

    @Synchronized
    fun open(owner: String, id: String, displayId: Int, width: Int, height: Int) {
        check(activeOwner == owner && owner !in closedExecutions)
        sessionOwners.clear()
        sessionOwners.add(owner)
        mutableSession.value = MobileDisplaySession(id, checkNotNull(executions[owner]), displayId, width, height)
    }

    @Synchronized
    fun update(id: String, width: Int, height: Int, appName: String? = session.value?.appName) {
        val current = session.value?.takeIf { it.id == id } ?: return
        mutableSession.value = current.copy(width = width, height = height, appName = appName)
    }

    @Synchronized
    fun finish(owner: String): Boolean {
        executions.remove(owner)
        if (activeOwner != owner) return false
        activeOwner = null
        // StateFlow 保留完成标记，宿主尚未订阅或正在切窗时也不会遗漏关闭请求。
        mutableSession.value = session.value?.let { it.copy(completionVersion = it.completionVersion + 1) }
        return true
    }

    @Synchronized
    fun close(id: String): Boolean {
        if (session.value?.id != id) return false
        clear(blockOwner = true)
        return true
    }

    @Synchronized
    fun clear(blockOwner: Boolean) {
        if (blockOwner) {
            activeOwner?.let(closedExecutions::add)
            // 已完成的流也可能因确认/答复而恢复，旧执行不能绕过用户关闭。
            closedExecutions.addAll(sessionOwners)
            // 新一轮已经开始但尚未调用 mobile 工具，也属于关闭时的当前轮。
            session.value?.chatId?.let { chatId ->
                executions.filterValues { it == chatId }.keys.forEach(closedExecutions::add)
            }
        }
        activeOwner = null
        sessionOwners.clear()
        mutableSession.value = null
    }
}
