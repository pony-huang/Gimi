package github.ponyhuang.asssistantai.voice

import com.google.gson.JsonParser
import github.ponyhuang.asssistantai.domain.speech.model.wakeKeywordGrammar
import java.io.Closeable
import org.vosk.Model
import org.vosk.Recognizer

class VoskWakeWordDetector(
    modelPath: String,
    keyword: String,
) : Closeable {
    private val model = Model(modelPath)
    private var keyword = keyword
    private var recognizer = createRecognizer(keyword)

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
        val normalizedKeyword = normalizeWakeText(keyword)
        return normalizedKeyword.isNotEmpty() && normalizeWakeText(text).contains(normalizedKeyword)
    }

    @Synchronized
    fun updateKeyword(value: String) {
        if (value == keyword) return
        recognizer.close()
        keyword = value
        recognizer = createRecognizer(value)
    }

    @Synchronized
    fun reset() {
        recognizer.close()
        recognizer = createRecognizer(keyword)
    }

    private fun createRecognizer(value: String): Recognizer {
        val escaped = wakeKeywordGrammar(value).replace("\\", "\\\\").replace("\"", "\\\"")
        return Recognizer(model, BluetoothPcmRecorder.SAMPLE_RATE_HZ.toFloat(), "[\"$escaped\", \"[unk]\"]")
    }

    override fun close() {
        recognizer.close()
        model.close()
    }
}
