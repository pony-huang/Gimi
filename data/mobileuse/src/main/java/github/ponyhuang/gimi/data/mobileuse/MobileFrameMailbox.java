package github.ponyhuang.gimi.data.mobileuse;

import java.util.function.Consumer;

/** 合并待编码图像，只保留最新一帧；固定编码节奏不会因连续帧而无限推迟。调用方负责串行访问。 */
final class MobileFrameMailbox<T> {
    private T pending;
    private boolean scheduled;
    private long nextEncodeAtMs;

    /** 返回首次安排编码的延迟；已安排时返回 -1，替换图像仍然保留最后一帧。 */
    long offer(T frame, long nowMs, Consumer<T> release) {
        if (pending != null) release.accept(pending);
        pending = frame;
        if (scheduled) return -1;
        scheduled = true;
        return Math.max(0, nextEncodeAtMs - nowMs);
    }

    /** 编码器接管待编码图像，完成后自行释放。 */
    T take(long nowMs) {
        T result = pending;
        pending = null;
        scheduled = false;
        nextEncodeAtMs = nowMs + 150;
        return result;
    }

    void reset(Consumer<T> release) {
        if (pending != null) release.accept(pending);
        pending = null;
        scheduled = false;
        nextEncodeAtMs = 0;
    }
}
