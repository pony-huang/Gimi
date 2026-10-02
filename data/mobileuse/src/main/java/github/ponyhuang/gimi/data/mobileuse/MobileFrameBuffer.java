package github.ponyhuang.gimi.data.mobileuse;

/** 当前截图 Surface 的最近有效帧；无新帧时保留缓存，换 Surface 或停止时必须清空。 */
final class MobileFrameBuffer {
    private long generation;
    private long sequence;
    private Frame latest;

    long reset() {
        latest = null;
        return ++generation;
    }

    void publish(long sourceGeneration, int width, int height, long capturedAtMs,
                 byte[] jpeg, int[] samples) {
        if (sourceGeneration != generation) return;
        latest = new Frame(width, height, capturedAtMs, ++sequence, generation, jpeg, samples);
    }

    Frame latest() { return latest; }

    /** 一次完整采集的图像、尺寸、时间和采样亮度，禁止把不同帧的几何信息拼接起来。 */
    static final class Frame {
        final int width;
        final int height;
        final long capturedAtMs;
        final long sequence;
        final long generation;
        final byte[] jpeg;
        final int[] samples;

        Frame(int width, int height, long capturedAtMs, long sequence, long generation,
              byte[] jpeg, int[] samples) {
            this.width = width;
            this.height = height;
            this.capturedAtMs = capturedAtMs;
            this.sequence = sequence;
            this.generation = generation;
            this.jpeg = jpeg;
            this.samples = samples;
        }
    }
}
