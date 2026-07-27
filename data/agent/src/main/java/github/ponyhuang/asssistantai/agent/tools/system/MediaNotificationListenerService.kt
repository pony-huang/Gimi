package github.ponyhuang.asssistantai.agent.tools.system

import android.service.notification.NotificationListenerService

/**
 * Empty notification listener service used as a token for
 * [android.media.session.MediaSessionManager.getActiveSessions].
 *
 * The system grants `MediaSessionManager` access to the calling app's
 * currently playing media sessions only when the app is registered as a
 * notification listener **and** the user has granted it notification access
 * via Settings → Notifications → Device & app notifications. This service
 * exists purely so we can hand a recognisable [android.content.ComponentName]
 * to that API; we don't consume any notifications here.
 */
class MediaNotificationListenerService : NotificationListenerService()