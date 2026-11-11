package github.ponyhuang.gimi.data.voice.repository

/**
 * 上行 PCM 帧的有界 FIFO。
 *
 * 帧来自 VAD 的逐帧回调，该缓冲**按帧复用**（`SileroVoiceActivityDetector` 明确要求
 * 回调方不得跨帧持有），因此入队必须拷贝：否则队列里的每一帧都指向同一块随后被
 * 反复覆写的内存，且异步发送时还会继续被录音线程改写。
 *
 * 会话就绪前（实时助手模式解析工具桥的挂起窗口）帧在此暂存，容量满时丢最旧；
 * 会话就绪后由**单个**消费协程按入队顺序取出，保证暂存帧与新帧的相对顺序。
 *
 * 非线程安全：调用方须在 `inputLock` 内访问（与 duplex 会话的交换同锁）。
 */
internal class UplinkFrameQueue(private val maxFrames: Int) {

    private val frames = ArrayDeque<ByteArray>()

    init {
        require(maxFrames > 0) { "maxFrames must be positive" }
    }

    /** 拷贝后入队；超出上限丢最旧。 */
    fun enqueue(frame: ByteArray) {
        frames.addLast(frame.copyOf())
        while (frames.size > maxFrames) frames.removeFirst()
    }

    /** 按入队顺序取出下一帧；队列为空返回 null。 */
    fun poll(): ByteArray? = frames.removeFirstOrNull()

    fun clear() {
        frames.clear()
    }
}
