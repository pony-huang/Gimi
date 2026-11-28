package github.ponyhuang.gimi.feature.chat.a2ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import github.ponyhuang.gimi.domain.a2ui.A2uiRendererFunctions

/** 当前可见聊天页面提供的音乐操作和收藏状态。 */
internal class ChatA2uiMusicActions(
    val sessionId: String,
    val renderer: A2uiRendererFunctions,
    val liked: Map<String, Boolean>,
    val pendingLikes: Set<String>,
    val setLiked: (String, Boolean) -> Unit,
    val canRecommend: Boolean,
)

internal val LocalChatA2uiMusicActions = compositionLocalOf<ChatA2uiMusicActions?> { null }
internal val LocalChatA2uiSurfaceId = staticCompositionLocalOf<String?> { null }
