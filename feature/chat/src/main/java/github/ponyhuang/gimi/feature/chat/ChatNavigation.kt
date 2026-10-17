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

/** 聊天 ViewModel 随宿主 Activity 保持，避免导航条目重建时丢失会话状态。 */
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

}

/** Cross-capability callbacks and activity inputs supplied by the app composition root. */
class ChatNavigationCallbacks(
    val onReturnToChat: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onConfigureModels: () -> Unit,
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
            sharedMediaUris = callbacks.sharedMediaUris,
            onSharedMediaConsumed = callbacks.onSharedMediaConsumed,
        )
        true
    }

    else -> false
}
