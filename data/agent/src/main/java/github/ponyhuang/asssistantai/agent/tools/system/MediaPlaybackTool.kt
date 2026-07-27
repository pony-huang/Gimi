package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import androidx.core.net.toUri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

/** Opens a music or video URL in a compatible app installed on the device. */
@Singleton
class MediaPlaybackTool @Inject constructor(
    private val queue: IntentActionQueue,
) {
    @Tool(name = "play_media", description = "Opens a media app to play audio or video from a direct URL.", requireConfirmation = true)
    fun playMedia(
        @Param("An HTTP or HTTPS URL pointing to audio or video.")
        url: String,
        @Param("Media type to play: music or video.")
        mediaType: String,
    ): Map<String, Any> {
        val uri = url.trim().toUri()
        if (uri.scheme !in SUPPORTED_SCHEMES || uri.schemeSpecificPart.isNullOrBlank()) {
            return mapOf(
                "success" to false,
                "error" to "Use a non-empty http, https, or content URL.",
            )
        }

        val mimeType = when (mediaType.lowercase().trim()) {
            "music" -> "audio/*"
            "video" -> "video/*"
            else -> return mapOf(
                "success" to false,
                "error" to "Unsupported media type. Use music or video.",
            )
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType)
            .addFlags(mediaIntentFlags(uri.scheme))
        return queue.request("Play media", "Open $mediaType media in a compatible player.", intent)
    }

    private companion object {
        val SUPPORTED_SCHEMES = setOf("http", "https", "content")
    }
}

internal fun mediaIntentFlags(scheme: String?): Int =
    if (scheme == "content") Intent.FLAG_GRANT_READ_URI_PERMISSION else 0
