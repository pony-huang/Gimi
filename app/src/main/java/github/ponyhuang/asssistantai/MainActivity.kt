package github.ponyhuang.asssistantai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.asssistantai.app.navigation.MainScreen
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import github.ponyhuang.asssistantai.voice.BluetoothVoiceService

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val requestedVoiceSessionId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedVoiceSessionId.value = intent.getStringExtra(BluetoothVoiceService.EXTRA_VOICE_SESSION_ID)
        enableEdgeToEdge()
        setContent {
            AsssistantaiTheme {
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
        requestedVoiceSessionId.value = intent.getStringExtra(BluetoothVoiceService.EXTRA_VOICE_SESSION_ID)
    }
}
