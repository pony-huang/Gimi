package github.ponyhuang.gimi.domain.mobileuse

/** Shizuku 授权及副屏当前状态。 */
enum class MobileUseAvailability {
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
 */
data class MobileUseResult(
    val status: String,
    val message: String,
    val displayId: Int? = null,
    val imageJpeg: ByteArray? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MobileUseResult

        if (displayId != other.displayId) return false
        if (status != other.status) return false
        if (message != other.message) return false
        if (width != other.width || height != other.height) return false
        if (!imageJpeg.contentEquals(other.imageJpeg)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = displayId ?: 0
        result = 31 * result + status.hashCode()
        result = 31 * result + message.hashCode()
        result = 31 * result + (width ?: 0)
        result = 31 * result + (height ?: 0)
        result = 31 * result + (imageJpeg?.contentHashCode() ?: 0)
        return result
    }
}

/** 单任务副屏能力；owner 为单轮 Agent 执行的稳定标识。 */
interface MobileUseRepository {
    fun availability(): MobileUseAvailability
    fun requestPermission(requestCode: Int)
    suspend fun observe(owner: String): MobileUseResult
    suspend fun launch(owner: String, packageName: String): MobileUseResult
    suspend fun tap(owner: String, x: Int, y: Int): MobileUseResult
    suspend fun tapRelative(owner: String, xPermille: Int, yPermille: Int): MobileUseResult
    suspend fun swipe(owner: String, x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): MobileUseResult
    suspend fun back(owner: String): MobileUseResult
    suspend fun typeText(owner: String, x: Int, y: Int, text: String): MobileUseResult
    suspend fun stop(owner: String): MobileUseResult
    fun textInputAvailable(): Boolean
}
