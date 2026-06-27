package github.ponyhuang.asssistantai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.asssistantai.ui.chat.MainScreen
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AsssistantaiTheme {
                MainScreen()
            }
        }
    }
}