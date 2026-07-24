package github.ponyhuang.asssistantai.assistant

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dagger.hilt.android.AndroidEntryPoint
import github.ponyhuang.asssistantai.MainActivity
import github.ponyhuang.asssistantai.domain.assistant.model.AssistantInvocationSource
import github.ponyhuang.asssistantai.domain.assistant.repository.AssistantSessionCoordinator
import github.ponyhuang.asssistantai.feature.assistant.AssistantOverlayRoute
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import github.ponyhuang.asssistantai.voice.BluetoothVoiceService
import javax.inject.Inject

/**
 * 统一的透明助理浮层 Activity：singleTask、不进最近任务、支持锁屏展示。
 * 锁屏下普通录音/问答/播报直接可用；批准敏感操作前先解锁。
 */
@AndroidEntryPoint
class AssistantOverlayActivity : ComponentActivity() {

    @Inject lateinit var coordinator: AssistantSessionCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            AsssistantaiTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Black.copy(alpha = 0.35f),
                                1f to Color.Black.copy(alpha = 0.6f),
                            ),
                        )
                        .safeDrawingPadding()
                        .imePadding(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AssistantOverlayRoute(
                            source = AssistantInvocationSource.TILE,
                            onClose = { finish() },
                            onOpenInChat = { sessionId -> openInChat(sessionId) },
                            approveConfirmation = ::approveWithKeyguard,
                        )
                    }
                }
            }
        }
    }

    /** 批准必须先解锁；解锁取消/失败均自动拒绝。拒绝本身不需要解锁。 */
    private fun approveWithKeyguard(proceed: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked != true) {
            proceed()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    proceed()
                }

                override fun onDismissCancelled() {
                    coordinator.respondToConfirmation(false)
                }

                override fun onDismissError() {
                    coordinator.respondToConfirmation(false)
                }
            },
        )
    }

    private fun openInChat(sessionId: String?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        sessionId?.let { intent.putExtra(BluetoothVoiceService.EXTRA_VOICE_SESSION_ID, it) }
        startActivity(intent)
        finish()
    }

}
