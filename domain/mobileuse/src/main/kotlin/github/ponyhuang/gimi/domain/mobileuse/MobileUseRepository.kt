package github.ponyhuang.gimi.domain.mobileuse

import kotlinx.coroutines.flow.StateFlow

/** Shizuku 授权及副屏当前状态。 */
enum class MobileUseAvailability {
    DISABLED,
    SHIZUKU_MISSING,
    SHIZUKU_STOPPED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    ROOT_UNSUPPORTED,
    READY,
    BUSY,
    UNSUPPORTED,
}

/**
 * 一次副屏操作的结果。
 *
 * @property status 机器可判定的结果码。
 * @property message 可向 Agent 展示的处理建议。
 * @property displayId 当前副屏 ID，未创建时为空。
 * @property imageJpeg 本次观察的压缩截图，仅供临时模型请求，不可持久化。
 * @property width 当前副屏截图宽度，未创建时为空。
 * @property height 当前副屏截图高度，未创建时为空。
 * @property actionStatus 动作投递状态：not_requested、not_sent、delivered、rejected 或 unknown。
 * @property observation 本次画面和元素共同使用的观察快照；超时不代表动作未执行。
 */
data class MobileUseResult(
    val status: String,
    val message: String,
    val displayId: Int? = null,
    val imageJpeg: ByteArray? = null,
    val width: Int? = null,
    val height: Int? = null,
    val actionStatus: String = "not_requested",
    val observation: MobileObservation? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MobileUseResult

        if (displayId != other.displayId) return false
        if (status != other.status) return false
        if (message != other.message) return false
        if (width != other.width || height != other.height) return false
        if (actionStatus != other.actionStatus || observation != other.observation) return false
        if (!imageJpeg.contentEquals(other.imageJpeg)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = displayId ?: 0
        result = 31 * result + status.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + (width ?: 0)
        result = 31 * result + (height ?: 0)
        result = 31 * result + actionStatus.hashCode()
        result = 31 * result + (observation?.hashCode() ?: 0)
        result = 31 * result + (imageJpeg?.contentHashCode() ?: 0)
        return result
    }
}

/** 单显示后台操作能力；owner 为本轮执行标识，显示按稳定 chatId 跨轮保留。 */
interface MobileUseRepository {
    /** 用户是否允许 Agent 使用后台操作，独立于系统授权。 */
    val enabled: StateFlow<Boolean>
    val displaySession: StateFlow<MobileDisplaySession?>
    val smallWindowEnabled: StateFlow<Boolean>
    suspend fun setSmallWindowEnabled(enabled: Boolean)
    suspend fun registerExecution(owner: String, chatId: String)
    /** 保存开关；关闭时释放当前任务资源，后续操作必须被拒绝。 */
    suspend fun setEnabled(enabled: Boolean)
    fun availability(): MobileUseAvailability
    fun requestPermission(requestCode: Int)
    suspend fun observe(owner: String): MobileUseResult
    suspend fun launch(owner: String, packageName: String): MobileUseResult
    suspend fun click(owner: String, observationId: String, elementId: String): MobileUseResult
    suspend fun setText(owner: String, observationId: String, elementId: String, text: String): MobileUseResult
    suspend fun tap(owner: String, observationId: String, x: Int, y: Int): MobileUseResult
    suspend fun tapRelative(owner: String, observationId: String, xPermille: Int, yPermille: Int): MobileUseResult
    suspend fun swipe(owner: String, observationId: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): MobileUseResult
    suspend fun back(owner: String, observationId: String): MobileUseResult
    suspend fun typeText(owner: String, observationId: String, x: Int, y: Int, text: String): MobileUseResult
    suspend fun finishExecution(owner: String): MobileUseResult
    suspend fun closeSession(sessionId: String): MobileUseResult
    suspend fun manualTouch(sessionId: String, touch: MobileTouch): MobileUseResult
    suspend fun manualBack(sessionId: String): MobileUseResult
    fun textInputAvailable(): Boolean
}
