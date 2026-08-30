package github.ponyhuang.gimi.feature.chat

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Destinations whose content and internal transitions are owned by the chat feature. */
sealed interface ChatDestination : NavKey {
    /** Main conversation destination. */
    @Serializable
    data object Chat : ChatDestination

    /**
     * Structured local-file search results for one conversation response.
     *
     * @property sessionId Owning conversation identifier.
     * @property responseId Tool response identifier to restore.
     */
    @Serializable
    data class SearchResults(
        val sessionId: String,
        val responseId: String,
    ) : ChatDestination
}

/** Cross-capability callbacks and activity inputs supplied by the app composition root. */
class ChatNavigationCallbacks(
    val onReturnToChat: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onConfigureModels: () -> Unit,
    val onOpenSearchResults: (sessionId: String, responseId: String) -> Unit,
    val onBack: () -> Unit,
    val requestedSessionId: String?,
    val onRequestedSessionHandled: () -> Unit,
    val sharedMediaUris: List<Uri>,
    val onSharedMediaConsumed: () -> Unit,
)

/** Resolves chat-owned destinations without exposing Route composables to `:app`. */
@Composable
fun ChatEntryProvider(
    destination: NavKey,
    callbacks: ChatNavigationCallbacks,
): Boolean = when (destination) {
    ChatDestination.Chat -> {
        ChatRoute(
            onReturnToChat = callbacks.onReturnToChat,
            onOpenSettings = callbacks.onOpenSettings,
            onConfigureModels = callbacks.onConfigureModels,
            onShowAllLocalFiles = callbacks.onOpenSearchResults,
            requestedSessionId = callbacks.requestedSessionId,
            onRequestedSessionHandled = callbacks.onRequestedSessionHandled,
            sharedMediaUris = callbacks.sharedMediaUris,
            onSharedMediaConsumed = callbacks.onSharedMediaConsumed,
        )
        true
    }

    is ChatDestination.SearchResults -> {
        ChatSearchResultsRoute(
            sessionId = destination.sessionId,
            responseId = destination.responseId,
            onBack = callbacks.onBack,
        )
        true
    }

    else -> false
}
