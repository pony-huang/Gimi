package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Context
import android.media.AudioManager
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Controls the device media-volume stream. */
@Singleton
class VolumeTool @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Tool(name = "get_media_volume", description = "Gets the current, minimum, and maximum media-volume levels on the device.")
    fun getMediaVolume(): Map<String, Int> = mediaVolumeState()

    @Tool(
        name = "set_media_volume",
        description = "Sets the device media volume to an absolute level, ramping gradually so the change feels smooth. The applied level is clamped to the device's minimum and maximum.",
        requireConfirmation = true,
    )
    fun setMediaVolume(
        @Param("Target media volume level. The volume changes gradually. Use get_media_volume first to learn the device's minimum and maximum levels.")
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
        description = "Adjusts the device media volume relative to its current level by a signed number of steps, ramping gradually so the change feels smooth.",
        requireConfirmation = true,
    )
    fun adjustMediaVolume(
        @Param("Number of media-volume levels to add or subtract. Use a positive number to increase volume and a negative number to decrease it.")
        delta: Int,
    ): Map<String, Int> {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return setMediaVolume(current + delta) + mapOf("delta" to delta)
    }

    private fun mediaVolumeState(): Map<String, Int> = mapOf(
        "currentLevel" to audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        "minimumLevel" to audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC),
        "maximumLevel" to audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
    )

    private fun setMediaVolumeGradually(targetLevel: Int) {
        var level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        // 丝滑音调
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
