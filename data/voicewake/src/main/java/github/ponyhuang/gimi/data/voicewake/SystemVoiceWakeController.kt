package github.ponyhuang.gimi.data.voicewake

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeState
import github.ponyhuang.gimi.domain.speech.model.VoiceWakeStatus
import github.ponyhuang.gimi.domain.speech.model.WakePhraseError
import github.ponyhuang.gimi.domain.speech.model.WakePhraseException
import github.ponyhuang.gimi.domain.speech.model.normalizeWakePhrase
import github.ponyhuang.gimi.domain.speech.model.validateWakePhrase
import github.ponyhuang.gimi.domain.speech.repository.VoiceWakeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 基于 Android 设备端识别器、仅在应用可见时运行的语音唤醒仓储。 */
@Singleton
class SystemVoiceWakeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: VoiceWakePreferences,
    recognizer: VoiceWakeRecognizer,
    private val commandSubmitter: VoiceWakeCommandSubmitter,
) : VoiceWakeRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recognizerAvailable = recognizer.isAvailable
    private val mutableState = MutableStateFlow(
        VoiceWakeState(
            enabled = preferences.enabled.value && recognizerAvailable,
            recognizerAvailable = recognizerAvailable,
            status = initialStatus(preferences.enabled.value, recognizerAvailable),
            triggerPhrases = preferences.triggerPhrases.value,
        ),
    )
    private val manager = SystemVoiceWakeManager(
        scope = scope,
        recognizer = recognizer,
        initialTriggerWords = preferences.triggerPhrases.value,
        hasRecordAudioPermission = ::hasRecordAudioPermission,
        onCommand = { match ->
            mutableState.update {
                it.copy(
                    status = VoiceWakeStatus.Submitting,
                    lastCommand = match.command,
                    message = null,
                )
            }
            try {
                commandSubmitter.submit(match.command)
            } finally {
                mutableState.update {
                    if (it.enabled) it.copy(status = VoiceWakeStatus.Idle) else it
                }
            }
        },
    )
    override val state: StateFlow<VoiceWakeState> = mutableState.asStateFlow()

    init {
        manager.setEnabled(mutableState.value.enabled)
        scope.launch {
            manager.isListening.collect { listening ->
                mutableState.update { current ->
                    when {
                        listening -> current.copy(status = VoiceWakeStatus.Listening, message = null)
                        current.status == VoiceWakeStatus.Listening -> current.copy(
                            status = if (current.enabled) VoiceWakeStatus.Idle else VoiceWakeStatus.Disabled,
                        )
                        else -> current
                    }
                }
            }
        }
    }

    override fun setEnabled(enabled: Boolean) {
        val effective = enabled && mutableState.value.recognizerAvailable
        preferences.setEnabled(effective)
        mutableState.update {
            it.copy(
                enabled = effective,
                status = when {
                    !it.recognizerAvailable -> VoiceWakeStatus.Unavailable
                    effective -> VoiceWakeStatus.Idle
                    else -> VoiceWakeStatus.Disabled
                },
                message = null,
            )
        }
        manager.setEnabled(effective)
    }

    override fun setForeground(foreground: Boolean) {
        manager.setForeground(foreground)
    }

    override fun refreshPermission() {
        manager.refreshPermission()
    }

    override fun addTriggerPhrase(phrase: String): Result<Unit> = runCatching {
        val normalized = normalizeWakePhrase(phrase)
        validateWakePhrase(normalized)?.let { throw WakePhraseException(it) }
        val current = mutableState.value.triggerPhrases
        if (current.any { it.equals(normalized, ignoreCase = true) }) {
            throw WakePhraseException(WakePhraseError.Duplicate)
        }
        publishPhrases(current + normalized)
    }

    override fun removeTriggerPhrase(phrase: String): Result<Unit> = runCatching {
        val current = mutableState.value.triggerPhrases
        if (current.size == 1) throw WakePhraseException(WakePhraseError.LastPhrase)
        publishPhrases(current.filterNot { it == phrase })
    }

    private fun publishPhrases(phrases: List<String>) {
        preferences.setTriggerPhrases(phrases)
        manager.updateTriggerWords(phrases)
        mutableState.update { it.copy(triggerPhrases = phrases) }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        fun initialStatus(enabled: Boolean, available: Boolean): VoiceWakeStatus = when {
            !available -> VoiceWakeStatus.Unavailable
            enabled -> VoiceWakeStatus.Idle
            else -> VoiceWakeStatus.Disabled
        }
    }
}
