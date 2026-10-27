package github.ponyhuang.gimi.feature.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Preview(name = "Compact composer light", widthDp = 390, heightDp = 780)
@Composable
fun CompactComposerLightPreview() = CompactComposerPreview(false)

@Preview(name = "Compact composer dark", widthDp = 390, heightDp = 780)
@Composable
fun CompactComposerDarkPreview() = CompactComposerPreview(true)

@Composable
private fun CompactComposerPreview(dark: Boolean) {
    AsssistantaiTheme(darkTheme = dark) {
        androidx.compose.material3.Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 32.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                Text("紧凑输入胶囊", style = MaterialTheme.typography.titleLarge)
                Text("文字输入 · 空闲", style = MaterialTheme.typography.labelLarge)
                CompactPreviewCapsule {
                    CompactPreviewTextInput("")
                }
                Text("文字输入 · 待发送", style = MaterialTheme.typography.labelLarge)
                CompactPreviewCapsule {
                    CompactPreviewTextInput("帮我总结一下今天的工作计划", "deepseekv4-flash", "deepseek")
                }
                Text("语音输入 · 录音中", style = MaterialTheme.typography.labelLarge)
                CompactPreviewCapsule {
                    DefaultVoiceRecordingContent(VoiceRecordingContentParams(
                        levels = List(48) { ((it % 7) + 1) / 8f }, remainingSeconds = 47,
                        onCancel = {}, onFinish = {}, onSend = {},
                    ))
                }
                Text("语音输入 · 转写中", style = MaterialTheme.typography.labelLarge)
                CompactPreviewCapsule { TranscribingContent() }
            }
        }
    }
}

@Composable
private fun CompactPreviewCapsule(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = chatCapsuleColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) { content() }
}

@Composable
private fun CompactPreviewTextInput(text: String, modelName: String = "GPT-5.4", serviceId: String = "openai") {
    DefaultComposerInputContent(Modifier, ComposerInputContentParams(
        messageData = MessageData(text), isGenerating = false,
        voiceInputState = VoiceInputUiState.Idle, isVoiceInputAvailable = true,
        voiceErrorMessage = null, onVoiceErrorShown = {}, onTextChange = {},
        onRemoveAttachment = {}, onSendClick = {}, onStopClick = {}, onVoiceInputStart = {},
        modelSelectorContent = { ModelStatusDisplay(modelName, serviceId, "未选择模型", false, {}) },
    ))
}
