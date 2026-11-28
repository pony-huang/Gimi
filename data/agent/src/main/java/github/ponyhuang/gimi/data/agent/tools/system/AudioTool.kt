package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Context
import android.media.AudioManager
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.a2ui.A2uiMediaVolume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 音频域工具：读取 / 设置设备媒体音量。
 */
@Singleton
class AudioTool @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Tool(
        name = "get_media_volume",
        description = "Gets the current, minimum, and maximum media-volume levels on the device."
    )
    fun getMediaVolume(): Map<String, Int> = mediaVolumeState()

    @Tool(
        name = "set_media_volume",
        description = "Sets the device media volume to an absolute level. The applied level is clamped to the device's minimum and maximum.",
        requireConfirmation = true,
    )
    @Synchronized
    fun setMediaVolume(
        @Param("Target media volume level.")
        level: Int,
    ): Map<String, Int> {
        val minimum = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val appliedLevel = level.coerceIn(minimum, maximum)
        setMediaVolumeGradually(appliedLevel)
        return mediaVolumeState() + mapOf(
            "requestedLevel" to level,
            "appliedLevel" to appliedLevel,
        )
    }

    @Tool(
        name = "adjust_media_volume",
        description = "Adjusts the device media volume relative to its current level by a signed number of steps.",
        requireConfirmation = true,
    )
    @Synchronized
    fun adjustMediaVolume(
        @Param("Number of media-volume levels to add or subtract. Use a positive number to increase volume and a negative number to decrease it.")
        delta: Int,
    ): Map<String, Int> {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return setMediaVolume(current + delta) + mapOf("delta" to delta)
    }

    internal fun readVolumeForControl(authorized: Boolean): A2uiMediaVolume? {
        val manager = audioManager ?: return null
        val stream = AudioManager.STREAM_MUSIC
        return try {
            A2uiMediaVolume(
                level = manager.getStreamVolume(stream),
                minimum = manager.getStreamMinVolume(stream),
                maximum = manager.getStreamMaxVolume(stream),
                adjustable = authorized && !manager.isVolumeFixed,
            )
        } catch (_: SecurityException) {
            null
        }
    }

    @Synchronized
    internal fun setVolumeFromControl(level: Int): Boolean {
        val state = readVolumeForControl(authorized = true) ?: return false
        if (!state.adjustable) return false
        val target = level.coerceIn(state.minimum, state.maximum)
        return try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == target
        } catch (_: SecurityException) {
            false
        }
    }

    private fun mediaVolumeState(): Map<String, Int> = mapOf(
        "currentLevel" to audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        "minimumLevel" to audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC),
        "maximumLevel" to audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
    )

    private fun setMediaVolumeGradually(targetLevel: Int) {
        var level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // 丝滑增长
        while (level != targetLevel) {
            level += if (targetLevel > level) 1 else -1
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                level,
                AudioManager.FLAG_SHOW_UI,
            )
            if (level != targetLevel) {
                Thread.sleep(VOLUME_STEP_DELAY_MILLIS)
            }
        }
    }

    private companion object {
        const val VOLUME_STEP_DELAY_MILLIS = 80L
    }
}
