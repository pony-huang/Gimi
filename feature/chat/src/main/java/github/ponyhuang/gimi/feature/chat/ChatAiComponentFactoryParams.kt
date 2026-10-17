package github.ponyhuang.gimi.feature.chat

import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Parameters for [ChatAiComponentFactory.ComposerLeadingContent].
 *
 * @param isGenerating Whether the AI is currently generating a response.
 * @param configurationReady Whether the per-session tool configuration has been loaded.
 *   While false the attachment entry stays disabled so the "add to chat" sheet cannot
 *   be opened against unloaded configuration.
 * @param onAttachmentsClick Called when the user requests to add attachments.
 */
public data class ComposerLeadingContentParams(
    val isGenerating: Boolean,
    val configurationReady: Boolean = true,
    val onAttachmentsClick: () -> Unit,
)

/**
 * Parameters for [ChatAiComponentFactory.ComposerInputContent].
 *
 * @param messageData The message currently being composed.
 * @param isGenerating Whether the AI is currently generating a response.
 * @param voiceInputState Current controlled voice-input presentation state.
 * @param onTextChange Called when the input text changes.
 * @param onRemoveAttachment Called when the user removes an attachment.
 * @param onSendClick Called when the user sends the message.
 * @param onStopClick Called when the user stops AI generation.
 * @param onVoiceInputStart Called when the user starts voice input.
 * @param retainExpanded Whether an active child surface requires the composer to stay expanded.
 * @param onExpandedChange Called when the default input switches between compact and expanded layouts.
 * @param configurationReady Whether the per-session tool configuration has been loaded;
 *   forwarded to [ComposerLeadingContentParams].
 */
public data class ComposerInputContentParams(
    val messageData: MessageData,
    val isGenerating: Boolean,
    val voiceInputState: VoiceInputUiState,
    val isVoiceInputAvailable: Boolean,
    val voiceErrorMessage: String?,
    val onVoiceErrorShown: () -> Unit,
    val onTextChange: (String) -> Unit,
    val onRemoveAttachment: (DraftAttachment) -> Unit,
    val onSendClick: () -> Unit,
    val onStopClick: () -> Unit,
    val onVoiceInputStart: () -> Unit,
    val retainExpanded: Boolean = false,
    val onExpandedChange: (Boolean) -> Unit = { },
    val onAttachmentsClick: () -> Unit = { },
    val modelSelectorContent: @Composable () -> Unit = { },
    val configurationReady: Boolean = true,
    /** 内容请求只允许文字或语音，隐藏普通聊天的附件入口。 */
    val attachmentsEnabled: Boolean = true,
)

/** Immutable presentation state for the composer's voice-input session. */
public sealed interface VoiceInputUiState {
    /** 没有进行中的录音或转写。 */
    public data object Idle : VoiceInputUiState

    /** 录音展示快照；levels 为 0..1 的历史音量，remainingSeconds 为剩余时限。 */
    public data class Recording(
        val levels: List<Float> = emptyList(),
        val remainingSeconds: Int = 60,
    ) : VoiceInputUiState

    /** 识别等待状态；mode 决定成功后编辑草稿还是立即发送。 */
    public data class Transcribing(val mode: VoiceInputMode = VoiceInputMode.TRANSCRIBE) : VoiceInputUiState
}

/**
 * 全宽录音胶囊的音量、剩余时限与三个独立操作入口。
 * @property onFinish 停止录音并转写到草稿。
 * @property onSend 停止录音、转写并通过普通入口发送。
 */
public data class VoiceRecordingContentParams(
    val levels: List<Float>,
    val remainingSeconds: Int,
    val onCancel: () -> Unit,
    val onFinish: () -> Unit,
    val onSend: () -> Unit,
)

/**
 * Parameters for [ChatAiComponentFactory.ComposerTrailingContent].
 *
 * @param isGenerating Whether the AI is currently generating a response.
 */
public data class ComposerTrailingContentParams(
    val isGenerating: Boolean,
)

/**
 * Parameters for [ChatAiComponentFactory.AITypingIndicatorLabel].
 *
 * @param modifier The modifier to apply to the label content.
 */
public data class AITypingIndicatorLabelParams(
    val modifier: Modifier = Modifier,
)

/**
 * Parameters for [ChatAiComponentFactory.AITypingIndicatorIndicator].
 *
 * @param modifier The modifier to apply to the indicator content.
 */
public data class AITypingIndicatorIndicatorParams(
    val modifier: Modifier = Modifier,
)

/**
 * Parameters for [ChatAiComponentFactory.SpeechToTextButtonIdleContent].
 *
 * @param onClick Called when the user taps the idle content to start recording.
 */
public data class SpeechToTextButtonIdleContentParams(
    val onClick: () -> Unit,
    val enabled: Boolean = true,
)
