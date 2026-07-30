package github.ponyhuang.gimi.feature.chat

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.Locale

private const val TAG = "SpeechRecognizerController"

/**
 * Interface for speech recognition controller.
 * Manages speech recognition using Android's SpeechRecognizer API.
 */
internal interface SpeechRecognizerController {
    val isListening: Boolean get() = false
    val rmsdB: Float get() = 0f

    fun startListening(): Boolean = false

    fun stopListening() {
        // No-op
    }

    fun cancel() {
        // No-op
    }

    fun release() {
        // No-op
    }
}

/**
 * Default implementation of SpeechRecognizerController, extending from ViewModel so that it can survive configuration changes.
 */
@SuppressLint("StaticFieldLeak") // We are using the application context
internal class SpeechRecognizerControllerViewModel(
    private val context: Context,
) : SpeechRecognizerController, ViewModel() {

    private var onPartialResult: ((String) -> Unit)? = null
    private var onFinalResult: ((String) -> Unit)? = null
    private var onErrorResult: ((String) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = mutableStateOf(false)
    override val isListening by _isListening

    private val _rmsdB = mutableFloatStateOf(0f)
    override val rmsdB by _rmsdB

    private val resultAccumulator = SpeechRecognitionResultAccumulator()

    fun setCallbacks(
        onPartialResult: ((String) -> Unit)?,
        onFinalResult: ((String) -> Unit)?,
        onError: ((String) -> Unit)?,
    ) {
        this.onPartialResult = onPartialResult
        this.onFinalResult = onFinalResult
        this.onErrorResult = onError
    }

    @Suppress("TooGenericExceptionCaught")
    override fun startListening(): Boolean {
        if (_isListening.value) {
            return false
        }

        speechRecognizer = speechRecognizer ?: createRecognizer() ?: run {
            Log.w(TAG, "Speech recognition is not available on this device")
            return false
        }

        return try {
            resetState()
            speechRecognizer?.startListening(createRecognitionIntent())
            _isListening.value = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}", e)
            false
        }
    }

    override fun stopListening() {
        if (!_isListening.value) {
            return
        }
        speechRecognizer?.stopListening()
        // Don't reset state here - onResults() will be called asynchronously
        // and will handle the reset after processing the final result
        _isListening.value = false
    }

    override fun cancel() {
        speechRecognizer?.cancel()
        resetState()
    }

    override fun release() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        resetState()
    }

    override fun onCleared() {
        release()
    }

    private fun createRecognizer(): SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(createRecognitionListener())
            }
        } else {
            null
        }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        // 1. Called first - recognizer is ready to listen
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech: params=${params?.getData()}")
        }

        // 2. Called when user starts speaking
        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech: User started speaking")
        }

        // 3. Called repeatedly during speech - provides audio level updates
        override fun onRmsChanged(rmsdB: Float) {
            _rmsdB.floatValue = rmsdB
        }

        // 4. Optional - may not be called, receives audio buffer
        override fun onBufferReceived(buffer: ByteArray?) {
            Log.d(TAG, "onBufferReceived: buffer size=${buffer?.size ?: 0}")
        }

        // 5. Optional - called multiple times if EXTRA_PARTIAL_RESULTS is enabled
        override fun onPartialResults(partialResults: Bundle?) {
            Log.d(TAG, "onPartialResults: ${partialResults?.getData()}")

            extractResult(partialResults)
                ?.takeIf(String::isNotBlank)
                ?.let { result ->
                    onPartialResult?.invoke(
                        resultAccumulator.preview(
                            result,
                            commit = partialResults.isFinalResult(),
                        ),
                    )
                }
        }

        // 6. Called when user stops speaking
        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech: User stopped speaking")
        }

        // 7. Called last (in normal flow) - provides final recognition results
        override fun onResults(results: Bundle?) {
            Log.d(TAG, "onResults: ${results?.getData()}")

            onFinalResult?.invoke(resultAccumulator.complete(extractResult(results)))

            resetState()
        }

        // 8. Can be called at any time if an error occurs
        override fun onError(error: Int) {
            val errorMessage = getErrorMessage(error)
            Log.e(TAG, "onError: $errorMessage")
            onErrorResult?.invoke(errorMessage)

            resetState()
        }

        // 9. Optional - handles additional events
        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "onEvent: eventType=$eventType, params=${params?.getData()}")
        }
    }

    private fun resetState() {
        _isListening.value = false
        _rmsdB.floatValue = 0f
        resultAccumulator.reset()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SpeechRecognizerControllerViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return SpeechRecognizerControllerViewModel(context = context.applicationContext) as T
        }
    }
}

@Suppress("DEPRECATION")
private fun Bundle.getData(): String =
    keySet().joinToString { key -> "$key=${get(key)}" }

private const val SILENCE_TIMEOUT_MS = 3000

private fun createRecognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_TIMEOUT_MS)
}

private fun extractResult(bundle: Bundle?): String? =
    bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

private fun Bundle?.isFinalResult(): Boolean =
    this?.getBoolean("final_result", false) ?: false

private fun getErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "The service does not allow to check for support."
    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> "The service does not support listening to model downloads events."
    SpeechRecognizer.ERROR_CLIENT -> "Other client side errors."
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Requested language is not available to be used with the current recognizer."
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Requested language is supported, but not available currently (e.g. not downloaded yet)."
    SpeechRecognizer.ERROR_NETWORK -> "Other network related errors."
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timed out."
    SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched."
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy."
    SpeechRecognizer.ERROR_SERVER -> "Server sends error status."
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Server has been disconnected, e.g. because the app has crashed."
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Too many requests from the same client."
    else -> "Unknown error: $error"
}
