package github.ponyhuang.gimi.voice

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.gimi.MainActivity
import github.ponyhuang.gimi.domain.appearance.AppearanceRepository
import github.ponyhuang.gimi.domain.assistant.model.isPresentationResultIdle
import github.ponyhuang.gimi.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.gimi.feature.assistant.AssistantSurface
import github.ponyhuang.gimi.feature.assistant.AssistantSurfaceMode
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import javax.inject.Inject
import kotlinx.coroutines.delay

/** 锁屏语音唤醒的轻量界面，只展示当前一轮摘要。 */
@AndroidEntryPoint
class AssistantLockScreenActivity : ComponentActivity() {
    @Inject lateinit var coordinator: AssistantSessionCoordinator
    @Inject lateinit var appearanceRepository: AppearanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        setContent {
            val state by coordinator.state.collectAsStateWithLifecycle()
            val darkThemeOverride by appearanceRepository.darkThemeOverride.collectAsStateWithLifecycle()
            LaunchedEffect(state.presentationVisible) {
                if (!state.presentationVisible) finish()
            }
            LaunchedEffect(state.phase) {
                if (state.presentationVisible && state.phase.isPresentationResultIdle()) {
                    delay(AssistantResultStayMs)
                    coordinator.hidePresentation()
                }
            }
            if (!state.presentationVisible) {
                return@setContent
            }
            AsssistantaiTheme(darkTheme = darkThemeOverride ?: isSystemInDarkTheme()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    AssistantSurface(
                        state = state,
                        mode = AssistantSurfaceMode.LOCK_SCREEN,
                        onDismiss = {
                            coordinator.hidePresentation()
                            finish()
                        },
                        onStop = coordinator::stop,
                        onOpenChat = ::unlockAndOpenChat,
                    )
                }
            }
        }
    }

    private fun unlockAndOpenChat() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (!keyguardManager.isKeyguardLocked) {
            openCurrentChat()
            return
        }
        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = openCurrentChat()
            },
        )
    }

    private fun openCurrentChat() {
        coordinator.hidePresentation()
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_CURRENT_CHAT)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}

private const val AssistantResultStayMs = 5_000L
