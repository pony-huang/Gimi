package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 原型对比卡片外壳。 */
@Composable
private fun PrototypeComparisonCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )
            content()
        }
    }
}

/** M3 Button-Groups 底部栏生产预览：待通话、通话中未静音、通话中已静音（浅色）。 */
@Preview(name = "M3 按钮组底栏 · 浅色模式", widthDp = 390, locale = "zh")
@Composable
private fun VoiceInputDockLightPreview() {
    GimiTheme(darkTheme = false) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrototypeComparisonCard(
                "待通话状态",
                "左翼设置与键盘，中央为全圆呼叫胶囊 (Call)，右翼退出为鲜艳实心红",
            ) {
                VoiceInputDock(
                    state = RealtimeVoiceUiState(active = false),
                    onSettings = {}, onChat = {}, onCall = {}, onMute = {}, onBack = {},
                    composerContent = {}, settingsEnabled = true,
                )
            }
            PrototypeComparisonCard(
                "通话中 · 麦克风正常",
                "中央弹性形变为圆角方枕挂断 (CallEnd)，麦克风处于可用对讲态",
            ) {
                VoiceInputDock(
                    state = RealtimeVoiceUiState(
                        active = true,
                        voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true, muted = false),
                    ),
                    onSettings = {}, onChat = {}, onCall = {}, onMute = {}, onBack = {},
                    composerContent = {}, settingsEnabled = true,
                )
            }
            PrototypeComparisonCard(
                "通话中 · 麦克风已静音",
                "静音切为 MicOff 且底色保持中性微深灰（无红色刺激），右侧退出独享鲜红",
            ) {
                VoiceInputDock(
                    state = RealtimeVoiceUiState(
                        active = true,
                        voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true, muted = true),
                    ),
                    onSettings = {}, onChat = {}, onCall = {}, onMute = {}, onBack = {},
                    composerContent = {}, settingsEnabled = true,
                )
            }
        }
    }
}

/** M3 Button-Groups 底部栏生产预览：待通话与静音（深色）。 */
@Preview(name = "M3 按钮组底栏 · 深色模式", widthDp = 390, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceInputDockDarkPreview() {
    GimiTheme(darkTheme = true) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PrototypeComparisonCard(
                "深色模式 · 通话中且已静音",
                "中央为宣纸白主色挂断方枕，静音为深灰色块，退出为深红 errorContainer",
            ) {
                VoiceInputDock(
                    state = RealtimeVoiceUiState(
                        active = true,
                        voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true, muted = true),
                    ),
                    onSettings = {}, onChat = {}, onCall = {}, onMute = {}, onBack = {},
                    composerContent = {}, settingsEnabled = true,
                )
            }
        }
    }
}

/** 完整语音视口画板：生产环境整屏联动预览（浅色与深色）。 */
@Preview(name = "整屏视口 · M3 按钮组底栏 · 浅色", widthDp = 390, heightDp = 844, locale = "zh")
@Preview(name = "整屏视口 · M3 按钮组底栏 · 深色", widthDp = 390, heightDp = 844, locale = "zh", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceScreenM3FullPreview() {
    GimiTheme {
        val scene = voiceScene(MaterialTheme.colorScheme)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .voiceSceneBackground(scene),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // 顶部标题
                Surface(
                    shape = CircleShape,
                    color = scene.capsuleFill,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = "智能语音助手 · 通话中",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = scene.ink,
                    )
                }

                // 语音球体响应
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VoiceOrb(
                        state = RealtimeVoiceState(
                            phase = RealtimeVoicePhase.Listening,
                            listening = true,
                            inputLevel = 0.35f,
                            muted = true,
                        ),
                        switching = false,
                        statusText = "麦克风已静音",
                        modifier = Modifier.size(160.dp),
                    )
                    Text(
                        text = "麦克风已静音，轻触下方恢复对讲",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scene.captionDim,
                    )
                }

                // 底栏：M3 Button-Groups
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val previewState = RealtimeVoiceUiState(
                        active = true,
                        voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true, muted = true),
                        modelName = "DeepSeek V4",
                        modelTemplateId = "deepseek",
                        sttModelName = "Speech-02",
                        sttModelTemplateId = "minimax",
                        ttsModelName = "MiMo TTS",
                        ttsModelTemplateId = "mimo",
                        voiceName = "童童",
                    )
                    VoiceInputDock(
                        state = previewState,
                        onSettings = {},
                        onChat = {},
                        onCall = {},
                        onMute = {},
                        onBack = {},
                        composerContent = {},
                        settingsEnabled = true,
                    )
                    VoiceConfigMicroPills(
                        uiState = previewState,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
        }
    }
}
