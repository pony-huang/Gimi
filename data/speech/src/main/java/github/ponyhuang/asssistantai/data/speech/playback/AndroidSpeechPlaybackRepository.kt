package github.ponyhuang.asssistantai.data.speech.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackState
import github.ponyhuang.asssistantai.domain.speech.model.SpeechPlaybackStatus
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechPlaybackRepository
import github.ponyhuang.asssistantai.domain.speech.repository.SpeechSynthesisRepository
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Singleton
class AndroidSpeechPlaybackRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val synthesis: SpeechSynthesisRepository,
) : SpeechPlaybackRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow(SpeechPlaybackState())
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val cache = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {}
    private var cacheBytes = 0
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var pausedByFocusLoss = false
    private var generation = 0L
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(::onAudioFocusChanged)
        .build()

    override val state: StateFlow<SpeechPlaybackState> = _state.asStateFlow()
    override val errors: SharedFlow<String> = _errors.asSharedFlow()

    override fun toggle(messageId: String, text: String) {
        val current = _state.value
        if (current.messageId == messageId) {
            when (current.status) {
                SpeechPlaybackStatus.Loading -> stop()
                SpeechPlaybackStatus.Playing -> pause()
                SpeechPlaybackStatus.Paused -> resume()
                SpeechPlaybackStatus.Idle -> start(messageId, text)
            }
        } else {
            stop()
            start(messageId, text)
        }
    }

    override fun play(messageId: String, text: String) {
        stop()
        start(messageId, text)
    }

    override fun clearSession() {
        stop()
        synchronized(cache) {
            cache.clear()
            cacheBytes = 0
        }
    }

    override fun stop() {
        generation++
        playbackJob?.cancel()
        playbackJob = null
        audioTrack?.runCatching { stop() }
        audioTrack?.release()
        audioTrack = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        pausedByFocusLoss = false
        _state.value = SpeechPlaybackState()
    }

    private fun start(messageId: String, text: String) {
        val identity = synthesis.cacheIdentity()
        if (identity == null) {
            _errors.tryEmit("请先在设置中启用服务并选择默认语音播放模型")
            return
        }
        val cacheKey = "$messageId|${text.hashCode()}|$identity"
        val token = ++generation
        _state.value = SpeechPlaybackState(messageId, SpeechPlaybackStatus.Loading)
        playbackJob = scope.launch {
            try {
                val cached = synchronized(cache) { cache[cacheKey] }
                if (cached != null) {
                    playPcm(messageId, token) { write -> write(cached) }
                } else {
                    val collected = ByteArrayOutputStream()
                    playPcm(messageId, token) { write ->
                        synthesis.synthesize(text).collect { chunk ->
                            collected.write(chunk)
                            write(chunk)
                        }
                    }
                    putCache(cacheKey, collected.toByteArray())
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (error: Throwable) {
                if (generation == token) {
                    _errors.tryEmit(error.message ?: "语音播放失败")
                }
            } finally {
                if (generation == token) {
                    audioTrack?.release()
                    audioTrack = null
                    audioManager.abandonAudioFocusRequest(focusRequest)
                    playbackJob = null
                    _state.value = SpeechPlaybackState()
                }
            }
        }
    }

    private suspend fun playPcm(
        messageId: String,
        token: Long,
        source: suspend (suspend (ByteArray) -> Unit) -> Unit,
    ) {
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            error("无法获取音频播放焦点")
        }
        val minBuffer = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_MASK)
                    .setEncoding(ENCODING)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, SAMPLE_RATE))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        var totalFrames = 0L
        track.play()
        source { bytes ->
            if (generation != token) throw CancellationException()
            if (_state.value.status == SpeechPlaybackStatus.Loading) {
                _state.value = SpeechPlaybackState(messageId, SpeechPlaybackStatus.Playing)
            }
            var offset = 0
            while (offset < bytes.size) {
                val written = track.write(bytes, offset, bytes.size - offset, AudioTrack.WRITE_BLOCKING)
                if (written < 0) {
                    if (generation != token) throw CancellationException()
                    error("音频播放写入失败：$written")
                }
                offset += written
                totalFrames += written / BYTES_PER_FRAME
            }
        }
        while (generation == token && track.playbackHeadPosition.toLong() < totalFrames) {
            delay(20)
        }
    }

    private fun pause(fromFocusLoss: Boolean = false) {
        pausedByFocusLoss = fromFocusLoss
        audioTrack?.pause()
        _state.value.messageId?.let {
            _state.value = SpeechPlaybackState(it, SpeechPlaybackStatus.Paused)
        }
    }

    private fun resume() {
        pausedByFocusLoss = false
        audioTrack?.play()
        _state.value.messageId?.let {
            _state.value = SpeechPlaybackState(it, SpeechPlaybackStatus.Playing)
        }
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioTrack?.setVolume(1f)
                if (pausedByFocusLoss && _state.value.status == SpeechPlaybackStatus.Paused) resume()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> audioTrack?.setVolume(0.2f)
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> if (_state.value.status == SpeechPlaybackStatus.Playing) pause(fromFocusLoss = true)
        }
    }

    private fun putCache(key: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > MAX_CACHE_BYTES) return
        synchronized(cache) {
            cache.put(key, bytes)?.let { cacheBytes -= it.size }
            cacheBytes += bytes.size
            while (cacheBytes > MAX_CACHE_BYTES && cache.isNotEmpty()) {
                val eldest = cache.entries.iterator().next()
                cacheBytes -= eldest.value.size
                cache.remove(eldest.key)
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val CHANNEL_MASK = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_FRAME = 2
        const val MAX_CACHE_BYTES = 32 * 1024 * 1024
    }
}
