package github.ponyhuang.gimi

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.app.navigation.MainScreen
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.feature.chat.sharedImageUris
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.voice.AssistantPanelInteractor
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appearanceRepository: AppearanceRepository
    @Inject
    lateinit var assistantSessionCoordinator: AssistantSessionCoordinator
    @Inject
    lateinit var assistantPanelInteractor: AssistantPanelInteractor

    private val sharedMediaUris = mutableStateOf<List<Uri>>(emptyList())
    private val openChatRequest = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedMediaUris.value = savedInstanceState
            ?.getStringArrayList(KEY_SHARED_MEDIA_URIS)
            ?.map(Uri::parse)
            ?: sharedImageUris(intent)
        if (intent.action == ACTION_OPEN_CURRENT_CHAT) openChatRequest.value += 1
        enableEdgeToEdge()
        setContent {
            // 用户未显式切换时（null）跟随系统深色模式，切换后锁定为所选模式。
            val darkThemeOverride by appearanceRepository.darkThemeOverride
                .collectAsStateWithLifecycle()
            AsssistantaiTheme(darkTheme = darkThemeOverride ?: isSystemInDarkTheme()) {
                MainScreen(
                    assistantSessionCoordinator = assistantSessionCoordinator,
                    assistantPanelInteractor = assistantPanelInteractor,
                    openChatRequest = openChatRequest.value,
                    sharedMediaUris = sharedMediaUris.value,
                    onSharedMediaConsumed = { sharedMediaUris.value = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.action) {
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
            -> sharedMediaUris.value = sharedImageUris(intent)
            ACTION_OPEN_CURRENT_CHAT -> openChatRequest.value += 1
        }
    }

    override fun onResume() {
        super.onResume()
        MainActivityVisibility.setForeground(true)
    }

    override fun onPause() {
        MainActivityVisibility.setForeground(false)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(
            KEY_SHARED_MEDIA_URIS,
            ArrayList(sharedMediaUris.value.map(Uri::toString)),
        )
    }

    companion object {
        const val KEY_SHARED_MEDIA_URIS = "shared_media_uris"
        const val ACTION_OPEN_CURRENT_CHAT = "github.ponyhuang.gimi.action.OPEN_CURRENT_CHAT"
    }
}
