package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 使用 Android 设备端 [SpeechRecognizer] 提供单轮自由语音文本。 */
@Singleton
class AndroidOnDeviceVoiceWakeRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : VoiceWakeRecognizer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var generation = 0L

    override val isAvailable: Boolean
        get() = runCatching {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        }.getOrDefault(false)

    override fun start(onEvent: (VoiceWakeRecognitionEvent) -> Unit) {
        val requestedGeneration = ++generation
        mainHandler.post {
            if (requestedGeneration != generation) return@post
            destroyRecognizer()
            if (!isAvailable) {
                onEvent(VoiceWakeRecognitionEvent.Error(retryable = false))
                return@post
            }

            val recognizer = runCatching {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            }.getOrElse {
                onEvent(VoiceWakeRecognitionEvent.Error(retryable = false))
                return@post
            }
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(
                Listener(
                    isCurrent = {
                        requestedGeneration == generation && speechRecognizer === recognizer
                    },
                    onEvent = onEvent,
                ),
            )
            runCatching { recognizer.startListening(recognitionIntent()) }
                .onFailure {
                    destroyRecognizer()
                    onEvent(VoiceWakeRecognitionEvent.Error(retryable = false))
                }
        }
    }

    override fun stop() {
        generation += 1
        mainHandler.post { destroyRecognizer() }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    private fun destroyRecognizer() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private class Listener(
        private val isCurrent: () -> Boolean,
        private val onEvent: (VoiceWakeRecognitionEvent) -> Unit,
    ) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            emit(VoiceWakeRecognitionEvent.Ready)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.firstTranscript()?.let {
                emit(VoiceWakeRecognitionEvent.Transcript(it, isFinal = false))
            }
        }

        override fun onResults(results: Bundle?) {
            val transcript = results.firstTranscript()
            if (transcript == null) {
                emit(VoiceWakeRecognitionEvent.Error())
            } else {
                emit(VoiceWakeRecognitionEvent.Transcript(transcript, isFinal = true))
            }
        }

        override fun onError(error: Int) {
            emit(
                VoiceWakeRecognitionEvent.Error(
                    retryable = error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS &&
                        error != SpeechRecognizer.ERROR_CLIENT,
                ),
            )
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun emit(event: VoiceWakeRecognitionEvent) {
            if (isCurrent()) onEvent(event)
        }
    }
}

private fun Bundle?.firstTranscript(): String? =
    this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf(String::isNotEmpty)
