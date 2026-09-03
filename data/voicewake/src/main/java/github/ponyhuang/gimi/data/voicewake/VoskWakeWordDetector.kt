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
        val normalizedKeyword = normalizeWakeText(recognitionPhrase)
        return normalizedKeyword.isNotEmpty() && normalizeWakeText(text).contains(normalizedKeyword)
    }

    @Synchronized
    fun reset() {
        recognizer.close()
        recognizer = createRecognizer()
    }

    private fun createRecognizer(): Recognizer {
        // 自定义唤醒词可能落在所选模型的词汇表之外。Vosk 的 UpdateGrammarFst 在 grammar
        // 词汇全部不在表内时会构造出病态 grammar FST，LookaheadComposeFst 随之触发
        // pure-virtual 崩溃。因此这里不做 grammar 限定，唤醒检测完全交给 accept() 的
        // 文本包含匹配；匹配关键词即构造参数 recognitionPhrase。
        return Recognizer(model, VoiceAudioRecorder.SAMPLE_RATE_HZ.toFloat())
    }

    override fun close() {
        recognizer.close()
    }
}
