package github.ponyhuang.gimi.domain.mobileuse

/** 手机操作能力的可用状态，包含当前实现的授权与运行环境诊断。 */
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
 * 一次手机操作的结果；动作投递与后续观察的状态相互独立。
 *
 * @property status 机器可判定的结果码。
 * @property message 可向 Agent 展示的处理建议。
 * @property displayId 实现侧的显示诊断编号；通用调用方不得用它判断执行生命周期。
 * @property imageJpeg 本次观察的压缩截图，仅供临时模型请求，不可持久化。
 * @property width 当前目标截图宽度，未创建时为空。
 * @property height 当前目标截图高度，未创建时为空。
 * @property actionStatus 动作投递状态：not_requested、not_sent、delivered、rejected 或 unknown。
 * @property observation 内部画面观察快照；超时不代表动作未执行。
 * @property apps 应用查询结果；重名时也用于返回候选。
 * @property shellResult 兜底命令的退出码和输出；与后续画面观察是否成功相互独立。
 */
data class MobileUseResult(
    val status: String,
    val message: String,
    val displayId: Int? = null,
    val imageJpeg: ByteArray? = null,
    val width: Int? = null,
    val height: Int? = null,
    val actionStatus: String = ACTION_NOT_REQUESTED,
    val observation: MobileObservation? = null,
    val apps: List<MobileApp> = emptyList(),
    val shellResult: MobileShellResult? = null,
) {
    /** actionStatus 协议值单一真源；值即对外契约字符串，构造方与投递记录共用。 */
    companion object {
        const val ACTION_NOT_REQUESTED = "not_requested"
        const val ACTION_NOT_SENT = "not_sent"
        const val ACTION_DELIVERED = "delivered"
        const val ACTION_REJECTED = "rejected"
        const val ACTION_UNKNOWN = "unknown"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MobileUseResult

        if (displayId != other.displayId) return false
        if (status != other.status) return false
        if (message != other.message) return false
        if (width != other.width || height != other.height) return false
        if (actionStatus != other.actionStatus || observation != other.observation || apps != other.apps) return false
        if (shellResult != other.shellResult) return false
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
        result = 31 * result + apps.hashCode()
        result = 31 * result + (shellResult?.hashCode() ?: 0)
        result = 31 * result + (imageJpeg?.contentHashCode() ?: 0)
        return result
    }
}
