package github.ponyhuang.gimi.agent.tools.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体域工具：直接播放 URL、控制其它应用的活跃媒体会话（播放/暂停/切歌等）。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.MEDIA]。
 *
 * [MediaNotificationListenerService] 与本类同居，仅作为 [MediaSessionManager.getActiveSessions]
 * 所需的 [ComponentName] 占位 —— 它不消费任何通知。
 */
@Singleton
class MediaTool @Inject constructor(
    @ApplicationContext private val context: Context,
    private val queue: IntentActionQueue,
) {
    private val mediaSessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, MediaNotificationListenerService::class.java)

    // ---------- 直接播放 URL ----------

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

    // ---------- 媒体会话控制 ----------

    @Tool(
        name = "has_notification_access",
        description = "Returns whether the app has permission to control other apps' media playback.",
    )
    fun hasNotificationAccess(): Map<String, Any> = mapOf(
        "success" to true,
        "granted" to isNotificationAccessGranted(),
    )

    @Tool(
        name = "request_notification_access",
        description = "Opens system settings so the user can grant permission to control other apps' media playback.",
    )
    fun requestNotificationAccess(): Map<String, Any> {
        if (isNotificationAccessGranted()) {
            return mapOf(
                "success" to true,
                "alreadyGranted" to true,
                "permissionSettingsOpened" to false,
            )
        }
        return queue.request(
            title = "Grant notification access",
            summary = "Open system notification access settings to enable media-session control.",
            intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) + mapOf("alreadyGranted" to false)
    }

    @Tool(
        name = "list_active_media_sessions",
        description = "Lists media sessions currently active in other apps after user confirmation.",
        requireConfirmation = true,
    )
    fun listActiveMediaSessions(): Map<String, Any> {
        if (!isNotificationAccessGranted()) return notificationAccessRequired()
        val manager = mediaSessionManager ?: return serviceUnavailableError()
        val controllers = try {
            manager.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            return notificationAccessRequired()
        }
        if (controllers.isEmpty()) {
            return mapOf(
                "success" to true,
                "sessions" to emptyList<Map<String, Any>>(),
                "message" to "No apps are currently publishing an active media session.",
            )
        }
        val sessions = controllers.map(::describeSession)
        return mapOf(
            "success" to true,
            "sessions" to sessions,
            "count" to sessions.size,
        )
    }

    @Tool(
        name = "skip_to_next_track",
        description = "Sends skip-to-next to the most recent active media session.",
        requireConfirmation = true,
    )
    fun skipToNextTrack(): Map<String, Any> = sendTransportCommand(MediaAction.NEXT)

    @Tool(
        name = "skip_to_previous_track",
        description = "Sends skip-to-previous to the most recent active media session.",
        requireConfirmation = true,
    )
    fun skipToPreviousTrack(): Map<String, Any> = sendTransportCommand(MediaAction.PREVIOUS)

    @Tool(
        name = "play_media_session",
        description = "Resumes playback on the most recent active media session.",
        requireConfirmation = true,
    )
    fun playMediaSession(): Map<String, Any> = sendTransportCommand(MediaAction.PLAY)

    @Tool(
        name = "pause_media_session",
        description = "Pauses playback on the most recent active media session.",
        requireConfirmation = true,
    )
    fun pauseMediaSession(): Map<String, Any> = sendTransportCommand(MediaAction.PAUSE)

    @Tool(
        name = "stop_media_session",
        description = "Stops playback on the most recent active media session.",
        requireConfirmation = true,
    )
    fun stopMediaSession(): Map<String, Any> = sendTransportCommand(MediaAction.STOP)

    @Tool(
        name = "toggle_play_pause_media_session",
        description = "Toggles play / pause on the most recent active media session: pauses when playing, plays otherwise.",
        requireConfirmation = true,
    )
    fun togglePlayPauseMediaSession(): Map<String, Any> {
        if (!isNotificationAccessGranted()) return notificationAccessRequired()
        val manager = mediaSessionManager ?: return serviceUnavailableError()
        val controller = try {
            manager.getActiveSessions(listenerComponent).lastOrNull()
        } catch (_: SecurityException) {
            return notificationAccessRequired()
        } ?: return noActiveSessionError()
        val state = controller.playbackState?.state
        val currentlyPlaying = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        val action = if (currentlyPlaying) MediaAction.PAUSE else MediaAction.PLAY
        return dispatch(controller, action)
    }

    /**
     * Backwards-compatible alias — keeps the older flat action name available.
     * New code should prefer the per-action tools above.
     */
    @Tool(
        name = "media_control",
        description = "Dispatches a transport command to the most recent active media session. " +
            "Action must be one of: next, previous, play, pause, stop.",
        requireConfirmation = true,
    )
    fun mediaControl(
        @Param("Transport command. One of: next, previous, play, pause, stop.")
        action: String,
    ): Map<String, Any> {
        val parsed = parseAction(action) ?: return mapOf(
            "success" to false,
            "error" to "Unsupported action '$action'. Use one of: next, previous, play, pause, stop.",
        )
        return sendTransportCommand(parsed)
    }

    // ---------- helpers ----------

    private fun sendTransportCommand(action: MediaAction): Map<String, Any> {
        if (!isNotificationAccessGranted()) return notificationAccessRequired()
        val manager = mediaSessionManager ?: return serviceUnavailableError()
        val controller = try {
            manager.getActiveSessions(listenerComponent).lastOrNull()
        } catch (_: SecurityException) {
            return notificationAccessRequired()
        } ?: return noActiveSessionError()
        return dispatch(controller, action)
    }

    private fun dispatch(controller: MediaController, action: MediaAction): Map<String, Any> {
        val transport = controller.transportControls
        try {
            when (action) {
                MediaAction.NEXT -> transport.skipToNext()
                MediaAction.PREVIOUS -> transport.skipToPrevious()
                MediaAction.PLAY -> transport.play()
                MediaAction.PAUSE -> transport.pause()
                MediaAction.STOP -> transport.stop()
            }
        } catch (_: RuntimeException) {
            return mapOf(
                "success" to false,
                "error" to "The active media session does not support the '${action.token}' action.",
                "packageName" to controller.packageName,
            )
        }
        return mapOf(
            "success" to true,
            "action" to action.token,
            "packageName" to controller.packageName,
        )
    }

    private fun describeSession(controller: MediaController): Map<String, Any> {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        return mapOf(
            "packageName" to controller.packageName,
            "title" to (metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()),
            "artist" to (metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()),
            "album" to (metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()),
            "durationMillis" to (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L),
            "playbackState" to playbackStateName(playbackState?.state),
            "playing" to (playbackState?.state == PlaybackState.STATE_PLAYING),
        )
    }

    private fun playbackStateName(state: Int?): String = when (state) {
        null -> "none"
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING -> "buffering"
        PlaybackState.STATE_ERROR -> "error"
        PlaybackState.STATE_CONNECTING -> "connecting"
        PlaybackState.STATE_FAST_FORWARDING -> "fast_forwarding"
        PlaybackState.STATE_REWINDING -> "rewinding"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "skipping_to_next"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "skipping_to_previous"
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "skipping_to_queue_item"
        else -> "unknown"
    }

    private fun parseAction(raw: String): MediaAction? {
        val normalized = raw.trim().lowercase().replace('-', '_')
        return MediaAction.entries.firstOrNull { it.token == normalized || it.aliases.contains(normalized) }
    }

    private fun isNotificationAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun notificationAccessRequired(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "Notification access is required to read or control other apps' media sessions. " +
            "Call request_notification_access and grant the permission.",
        "granted" to false,
    )

    private fun noActiveSessionError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "No app is currently publishing an active media session. Start playback in a media app and try again.",
    )

    private fun serviceUnavailableError(): Map<String, Any> = mapOf(
        "success" to false,
        "error" to "Media session service is unavailable on this device.",
    )

    private enum class MediaAction(val token: String, val aliases: Set<String>) {
        NEXT("next", setOf("skip_to_next", "skiptonext")),
        PREVIOUS("previous", setOf("skip_to_previous", "skiptoprevious", "prev")),
        PLAY("play", setOf("resume")),
        PAUSE("pause", setOf()),
        STOP("stop", setOf()),
    }

    private companion object {
        val SUPPORTED_SCHEMES = setOf("http", "https", "content")
    }
}

internal fun mediaIntentFlags(scheme: String?): Int =
    if (scheme == "content") Intent.FLAG_GRANT_READ_URI_PERMISSION else 0

/**
 * Empty notification listener service used as a token for
 * [android.media.session.MediaSessionManager.getActiveSessions].
 *
 * The system grants `MediaSessionManager` access to the calling app's
 * currently playing media sessions only when the app is registered as a
 * notification listener **and** the user has granted it notification access
 * via Settings  Notifications  Device & app notifications. This service
 * exists purely so we can hand a recognisable [android.content.ComponentName]
 * to that API; we don't consume any notifications here.
 */
class MediaNotificationListenerService : NotificationListenerService()
