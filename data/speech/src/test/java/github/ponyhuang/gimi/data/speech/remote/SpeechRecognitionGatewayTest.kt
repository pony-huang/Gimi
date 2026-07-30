package github.ponyhuang.gimi.data.speech.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRecognitionGatewayTest {
    @Test
    fun wavEncoderWritesPcmFormatAndPayload() {
        val pcm = byteArrayOf(1, 2, 3, 4)

        val wav = encodePcm16Wav(pcm, sampleRateHz = 16_000, channelCount = 1)

        assertEquals("RIFF", wav.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", wav.copyOfRange(8, 12).decodeToString())
        assertEquals("fmt ", wav.copyOfRange(12, 16).decodeToString())
        assertEquals("data", wav.copyOfRange(36, 40).decodeToString())
        assertEquals(16_000, littleEndian32(wav, 24))
        assertEquals(32_000, littleEndian32(wav, 28))
        assertEquals(pcm.toList(), wav.copyOfRange(44, wav.size).toList())
    }

    private fun littleEndian32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)
}
