package github.ponyhuang.gimi.data.voicewake

import com.google.gson.JsonParser
import github.ponyhuang.gimi.core.audio.VoiceAudioRecorder
import github.ponyhuang.gimi.domain.speech.model.normalizeWakeText
import java.io.Closeable
import org.vosk.Model
import org.vosk.Recognizer

class VoskWakeWordDetector(
    private val model: Model,
    private val recognitionPhrase: String,
) : Closeable {
    // 每个音频块都会比对关键词，规范化结果在会话期内不变，缓存避免逐块重复正则。
    private val normalizedKeyword = normalizeWakeText(recognitionPhrase)
    private var recognizer = createRecognizer()

    @Synchronized
    fun accept(pcm16: ByteArray): Boolean {
        val completed = recognizer.acceptWaveForm(pcm16, pcm16.size)
        val json = if (completed) recognizer.result else recognizer.partialResult
        val text = runCatching {
            JsonParser.parseString(json).asJsonObject
                .get(if (completed) "text" else "partial")
                ?.asString
                .orEmpty()
        }.getOrDefault("")
        return normalizedKeyword.isNotEmpty() && normalizeWakeText(text).contains(normalizedKeyword)
    }

    @Synchronized
    fun reset() {
        recognizer.close()
        recognizer = createRecognizer()
    }

    private fun createRecognizer(): Recognizer {
        val escaped = recognitionPhrase.replace("\\", "\\\\").replace("\"", "\\\"")
        return Recognizer(model, VoiceAudioRecorder.SAMPLE_RATE_HZ.toFloat(), "[\"$escaped\", \"[unk]\"]")
    }

    override fun close() {
        recognizer.close()
    }
}
