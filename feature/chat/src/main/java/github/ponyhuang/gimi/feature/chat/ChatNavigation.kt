package github.ponyhuang.gimi.feature.chat

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 聊天相关的 ViewModel 统一挂在 Activity 作用域：Chat 主界面与“查看全部”等子目的地
 * 是不同的导航条目，若各自 `hiltViewModel()` 会拿到独立实例，子目的地读不到会话状态
 * （本地文件搜索结果回查会永远落空）。
 */
@Composable
internal fun activityScopedChatViewModel(): ChatViewModel {
    val activity = checkNotNull(LocalContext.current.findComponentActivity()) {
        "Chat feature requires a ComponentActivity host."
    }
    return viewModel(activity)
}

/** 沿 ContextWrapper 链找到宿主 Activity，供跨目的地共享 ViewModel 使用。 */
internal tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

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
