package github.ponyhuang.asssistantai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.R
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechSynthesisRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

@Singleton
class VoiceSpeechPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val synthesis: SpeechSynthesisRepository,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { }
        .build()

    fun isAvailable(): Boolean = synthesis.isAvailable()

    suspend fun play(text: String, route: BluetoothAudioRoute): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable() || text.isBlank()) return@withContext false
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            return@withContext false
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        try {
            if (!track.setPreferredDevice(route.communication)) return@withContext false
            var frames = 0L
            track.play()
            synthesis.synthesize(text).collect { bytes ->
                var offset = 0
                while (offset < bytes.size) {
                    val written = track.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_BLOCKING)
                    check(written >= 0) {
                        context.getString(R.string.bluetooth_voice_playback_failed, written)
                    }
                    offset += written
                    frames += written / 2
                }
            }
            while (track.playbackHeadPosition.toLong() < frames) delay(20)
            true
        } finally {
            track.runCatching { stop() }
            track.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
    }
}
