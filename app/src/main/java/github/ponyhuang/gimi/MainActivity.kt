package github.ponyhuang.gimi

import android.content.Intent
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
import github.ponyhuang.gimi.domain.conversation.repository.ChatDisplayRepository
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.data.voicewake.BluetoothVoiceController
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var chatDisplayRepository: ChatDisplayRepository

    private val requestedVoiceSessionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedVoiceSessionId.value = intent.getStringExtra(BluetoothVoiceController.BLUETOOTH_VOICE_EXTRA_SESSION_ID)
        enableEdgeToEdge()
        setContent {
            // 用户未显式切换时（null）跟随系统深色模式，切换后锁定为所选模式。
            val darkThemeOverride by chatDisplayRepository.darkThemeOverride
                .collectAsStateWithLifecycle()
            AsssistantaiTheme(darkTheme = darkThemeOverride ?: isSystemInDarkTheme()) {
                MainScreen(
                    requestedSessionId = requestedVoiceSessionId.value,
                    onRequestedSessionHandled = { requestedVoiceSessionId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedVoiceSessionId.value = intent.getStringExtra(BluetoothVoiceController.BLUETOOTH_VOICE_EXTRA_SESSION_ID)
    }
}
