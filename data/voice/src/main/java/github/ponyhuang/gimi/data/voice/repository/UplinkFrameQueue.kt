package github.ponyhuang.gimi.data.voice.repository

/**
 * 上行音频与本地判轮控制的有界 FIFO。
 *
 * 帧来自 VAD 的逐帧回调，该缓冲**按帧复用**（`SileroVoiceActivityDetector` 明确要求
 * 回调方不得跨帧持有），因此入队必须拷贝：否则队列里的每一帧都指向同一块随后被
 * 反复覆写的内存，且异步发送时还会继续被录音线程改写。
 *
 * 提交与打断控制也走同一队列，保证「本轮音频 → commit → 新语音」不会被后续音频
 * 插队。容量满时只丢最旧音频，不丢判轮控制。
 *
 * 非线程安全：调用方须在 `inputLock` 内访问（与 duplex 会话的交换同锁）。
 */
internal class UplinkFrameQueue(private val maxFrames: Int) {

    /** 单条上行命令；音频携带独立 PCM 副本，控制项不占音频容量。 */
    sealed interface Item {
        /** 一帧 16 kHz 单声道 PCM16 采集音频。 */
        data class Audio(val pcm16: ByteArray) : Item

        /** 本地 VAD 判定本轮结束，提交此前已发送的音频并请求响应。 */
        data object CommitTurn : Item

        /** 本地 VAD 判定用户重新开口，取消活动或刚请求但未确认的响应。 */
        data object Interrupt : Item
    }

    private val items = ArrayDeque<Item>()
    private var audioFrames = 0

    init {
        require(maxFrames > 0) { "maxFrames must be positive" }
    }

    /** 拷贝后入队；超出上限只丢最旧音频。 */
    fun enqueue(frame: ByteArray) {
        while (audioFrames >= maxFrames) {
            val oldestAudio = items.indexOfFirst { it is Item.Audio }
            if (oldestAudio < 0) break
            items.removeAt(oldestAudio)
            audioFrames--
        }
        items.addLast(Item.Audio(frame.copyOf()))
        audioFrames++
    }

    /** 在当前音频之后排入一次本地判轮提交。 */
    fun enqueueCommit() {
        items.addLast(Item.CommitTurn)
    }

    /** 在当前待处理命令之后排入一次本地打断。 */
    fun enqueueInterrupt() {
        items.addLast(Item.Interrupt)
    }

    /** 按入队顺序取出下一条命令；队列为空返回 null。 */
    fun poll(): Item? {
        val item = items.removeFirstOrNull() ?: return null
        if (item is Item.Audio) audioFrames--
        return item
    }

    fun clear() {
        items.clear()
        audioFrames = 0
    }
}
