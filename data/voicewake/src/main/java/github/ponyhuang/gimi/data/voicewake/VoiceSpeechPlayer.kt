package github.ponyhuang.gimi.data.voicewake

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.data.voicewake.R
import github.ponyhuang.gimi.domain.speech.repository.SpeechSynthesisRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

internal fun playbackDrainTimeoutMs(frameCount: Long, sampleRate: Int): Long {
    val audioDurationMs = frameCount.coerceAtLeast(0L) * 1_000L / sampleRate
    return maxOf(MIN_PLAYBACK_DRAIN_TIMEOUT_MS, audioDurationMs + PLAYBACK_DRAIN_MARGIN_MS)
}

internal fun speechPlaybackTimeoutMs(textLength: Int): Long =
    (PLAYBACK_BASE_TIMEOUT_MS + textLength.coerceAtLeast(0).toLong() * PLAYBACK_TIMEOUT_PER_CHARACTER_MS)
        .coerceIn(MIN_PLAYBACK_TIMEOUT_MS, MAX_PLAYBACK_TIMEOUT_MS)

@Singleton
class VoiceSpeechPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val synthesis: SpeechSynthesisRepository,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun isAvailable(): Boolean = synthesis.isAvailable()

    suspend fun play(text: String, route: VoiceAudioRoute): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable() || text.isBlank()) return@withContext false
        // 蓝牙走 VOICE_COMMUNICATION 路由到耳机；外放走 MEDIA 路由到手机扬声器。
        val attributes = when (route) {
            is BluetoothAudioRoute -> AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            is SpeakerAudioRoute -> AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        }
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
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
            if (route is BluetoothAudioRoute && !track.setPreferredDevice(route.communication)) {
                return@withContext false
            }
            // TTS 网络流或底层播放头都可能在路由切换后停止推进；到期后释放资源，
            // 让上层退出 Speaking 并重新打开唤醒监听。
            withTimeoutOrNull(speechPlaybackTimeoutMs(text.length).milliseconds) {
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
                withTimeoutOrNull(playbackDrainTimeoutMs(frames, SAMPLE_RATE).milliseconds) {
                    while (track.playbackHeadPosition.toLong() < frames) delay(PLAYBACK_POLL_INTERVAL_MS)
                    true
                } ?: false
            } ?: false
        } finally {
            track.runCatching { stop() }
            track.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val PLAYBACK_POLL_INTERVAL_MS = 20L
    }
}

private const val MIN_PLAYBACK_DRAIN_TIMEOUT_MS = 3_000L
private const val PLAYBACK_DRAIN_MARGIN_MS = 2_000L
private const val PLAYBACK_BASE_TIMEOUT_MS = 10_000L
private const val PLAYBACK_TIMEOUT_PER_CHARACTER_MS = 500L
private const val MIN_PLAYBACK_TIMEOUT_MS = 30_000L
private const val MAX_PLAYBACK_TIMEOUT_MS = 300_000L
