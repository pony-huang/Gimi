package github.ponyhuang.asssistantai.speech

import android.util.Base64
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartInputAudio
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenAiCompatibleSpeechRecognitionClient : SpeechRecognitionClient {
    override suspend fun transcribe(
        config: SpeechRecognitionConfig,
        request: SpeechRecognitionRequest,
    ): String = withContext(Dispatchers.IO) {
        val wav = encodePcm16Wav(
            pcm16 = request.pcm16,
            sampleRateHz = request.sampleRateHz,
            channelCount = request.channelCount,
        )
        val inputAudio = ChatCompletionContentPartInputAudio.builder()
            .inputAudio(
                ChatCompletionContentPartInputAudio.InputAudio.builder()
                    .data(Base64.encodeToString(wav, Base64.NO_WRAP))
                    .format(ChatCompletionContentPartInputAudio.InputAudio.Format.WAV)
                    .build(),
            )
            .build()
        val message = ChatCompletionUserMessageParam.builder()
            .content(
                ChatCompletionUserMessageParam.Content.ofArrayOfContentParts(
                    listOf(ChatCompletionContentPart.ofInputAudio(inputAudio)),
                ),
            )
            .build()
        val params = ChatCompletionCreateParams.builder()
            .model(config.modelId)
            .addMessage(message)
            .putAdditionalBodyProperty(
                "asr_options",
                JsonValue.from(mapOf("language" to request.language)),
            )
            .build()
        val client = OpenAIOkHttpClient.builder()
            .baseUrl(config.baseUrl)
            .apiKey(config.apiKey)
            .build()
        client.chat().completions().create(params).choices()
            .asSequence()
            .mapNotNull { it.message().content().orElse(null) }
            .firstOrNull { it.isNotBlank() }
            ?: error("语音识别未返回文本")
    }
}

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
