package github.ponyhuang.gimi.data.modelaccess.shared

/**
 * 把 PCM16 裸音频封装成 WAV 容器。
 *
 * 走文件上传的识别端点（MiniMax multipart、OpenAI 兼容的 `input_audio`）都要求 WAV 头，
 * 否则服务端无法解析采样率与声道数。
 */
internal fun encodePcm16Wav(
    pcm16: ByteArray,
    sampleRateHz: Int,
    channelCount: Int,
): ByteArray {
    require(sampleRateHz > 0) { "sampleRateHz must be positive" }
    require(channelCount > 0) { "channelCount must be positive" }
    val bitsPerSample = 16
    val byteRate = sampleRateHz * channelCount * bitsPerSample / 8
    val blockAlign = channelCount * bitsPerSample / 8
    return ByteArray(44 + pcm16.size).also { output ->
        fun ascii(offset: Int, value: String) {
            value.forEachIndexed { index, char -> output[offset + index] = char.code.toByte() }
        }
        fun littleEndian16(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
        }
        fun littleEndian32(offset: Int, value: Int) {
            output[offset] = value.toByte()
            output[offset + 1] = (value ushr 8).toByte()
            output[offset + 2] = (value ushr 16).toByte()
            output[offset + 3] = (value ushr 24).toByte()
        }

        ascii(0, "RIFF")
        littleEndian32(4, 36 + pcm16.size)
        ascii(8, "WAVE")
        ascii(12, "fmt ")
        littleEndian32(16, 16)
        littleEndian16(20, 1)
        littleEndian16(22, channelCount)
        littleEndian32(24, sampleRateHz)
        littleEndian32(28, byteRate)
        littleEndian16(32, blockAlign)
        littleEndian16(34, bitsPerSample)
        ascii(36, "data")
        littleEndian32(40, pcm16.size)
        pcm16.copyInto(output, destinationOffset = 44)
    }
}
