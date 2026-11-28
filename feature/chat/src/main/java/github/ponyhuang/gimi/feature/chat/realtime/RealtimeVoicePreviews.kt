package github.ponyhuang.gimi.feature.chat.realtime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.conversation.model.AttachmentCategory
import github.ponyhuang.gimi.domain.conversation.model.DraftAttachment
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceFailure
import github.ponyhuang.gimi.domain.voice.repository.VoiceConversationStyle
import github.ponyhuang.gimi.domain.voice.repository.VoicePipeline
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoicePhase
import github.ponyhuang.gimi.domain.voice.repository.RealtimeVoiceState
import github.ponyhuang.gimi.feature.chat.ComposerInputContentParams
import github.ponyhuang.gimi.feature.chat.MessageData
import github.ponyhuang.gimi.feature.chat.VoiceInputUiState
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 对比无输入、轻声内收、强声内收、回答和静音，保持球形并使用固定相位与真实电平范围。 */
@Preview(name = "Orb response light", widthDp = 500, heightDp = 220, locale = "zh-rCN")
@Preview(name = "Orb response dark", widthDp = 500, heightDp = 220, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceOrbResponsePreview() {
    GimiTheme {
        val scene = voiceScene(MaterialTheme.colorScheme)
        Box(Modifier.fillMaxWidth().voiceSceneBackground(scene).padding(8.dp)) {
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "无输入" to RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true),
                    "轻声" to RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true, inputLevel = .06f),
                    "较强输入" to RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, listening = true, inputLevel = .38f),
                    "正在播报" to RealtimeVoiceState(phase = RealtimeVoicePhase.Speaking, outputLevel = .5f),
                    "麦克风已静音" to RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, muted = true),
                ).forEach { (label, state) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VoiceOrb(state, switching = false, statusText = label, modifier = Modifier.size(88.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = scene.captionDim)
                    }
                }
            }
        }
    }
}

/** 懒启动待启动态：主按钮为「开始通话」，静音与设备控制禁用，新会话按钮禁用。 */
@Preview(name = "Idle light", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Idle dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceIdlePreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(), started = false, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

/** 待启动态 + 助手模式不可用（未绑定会话）：模式菜单引导配置而非置灰实时入口。 */
@Preview(name = "Idle chat unavailable light", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Idle chat unavailable dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceIdleChatUnavailablePreview() {
    GimiTheme {
        RealtimeVoiceScreen(RealtimeVoiceUiState(), started = false,
            onStartCall = {}, onToggleMute = {}, onEnd = {})
    }
}

/** 待启动态 + 助手模式（已绑定会话）：新建会话是唯一可点的右胶囊动作，通话设备仍不可切。 */
@Preview(name = "Idle assistant ready light", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Idle assistant ready dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceIdleAssistantReadyPreview() {
    GimiTheme {
        RealtimeVoiceScreen(RealtimeVoiceUiState(style = VoiceConversationStyle.ASSISTANT,
            pipeline = VoicePipeline.CASCADED, conversationTitle = "当前聊天"), started = false,
            onStartCall = {}, onToggleMute = {}, onEnd = {})
    }
}

@Preview(name = "Simple light", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Simple dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSimplePreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(
        phase = RealtimeVoicePhase.Listening, listening = true), modelName = "DeepSeek V4"),
        started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

@Preview(name = "Chat speaking", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Chat speaking dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Chat compact large text", widthDp = 360, heightDp = 640, fontScale = 1.5f, locale = "zh-rCN")
@Composable
private fun VoiceChatPreview() {
    GimiTheme {
        RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(
            phase = RealtimeVoicePhase.Speaking, listening = true,
            inputAttachments = listOf(FileAttachment(
                mimeType = "application/pdf", id = "preview-pdf", sizeBytes = 2048,
                displayName = "人工智能简史.pdf", fileId = "file_preview", fileVendor = "deepseek",
            )),
            inputText = "帮我想一个不赶时间的周末。", spokenText = "可以，把这个周末留一点空白。",
            speakingText = "周六先睡到自然醒，再去附近走走。",
            responseText = "可以，把这个周末留一点空白。周六先睡到自然醒，再去附近走走。找一家安静的小店。"),
            modelName = "DeepSeek V4", modelTemplateId = "deepseek",
            style = VoiceConversationStyle.ASSISTANT,
            conversationTitle = "周末放松计划",
            sttModelName = "MiniMax Speech-02", sttModelTemplateId = "minimax",
            ttsModelName = "MiMo TTS", ttsModelTemplateId = "mimo",
            voiceName = "童童"),
            started = true, onStartCall = {}, onToggleMute = {}, onEnd = {})
    }
}

/** 底部配置微胶囊组：级联（3模型+音色）、半级联（2模型+音色）、实时（1模型+音色）及超长文案省略预览。 */
@Preview(name = "Micro pills pipelines light", widthDp = 390, locale = "zh-rCN")
@Preview(name = "Micro pills pipelines dark", widthDp = 390, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceConfigMicroPillsPreview() {
    GimiTheme {
        val scene = voiceScene(MaterialTheme.colorScheme)
        Column(
            modifier = Modifier.fillMaxWidth().voiceSceneBackground(scene).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VoiceConfigMicroPills(
                uiState = RealtimeVoiceUiState(
                    pipeline = VoicePipeline.CASCADED,
                    modelName = "deepseek-v4-ultra-long-preview-name",
                    modelTemplateId = "deepseek",
                    sttModelName = "Speech-02-HD-Pro",
                    sttModelTemplateId = "minimax",
                    ttsModelName = "MiMo-TTS-v2-Expressive",
                    ttsModelTemplateId = "mimo",
                    voiceName = "童童（超长自定义复刻音色名称示例）",
                ),
            )
            VoiceConfigMicroPills(
                uiState = RealtimeVoiceUiState(
                    pipeline = VoicePipeline.SEMI_CASCADED,
                    modelName = "Qwen3 Omni",
                    modelTemplateId = "qwen",
                    ttsModelName = "MiMo TTS",
                    ttsModelTemplateId = "mimo",
                    voiceName = "童童",
                ),
            )
            VoiceConfigMicroPills(
                uiState = RealtimeVoiceUiState(
                    pipeline = VoicePipeline.NATIVE_REALTIME,
                    modelName = "qwen3-omni-flash-realtime-2026-preview",
                    modelTemplateId = "qwen",
                    voiceName = "Cherry",
                ),
            )
        }
    }
}

/** 回执文本缺少原文换行时的进度高亮：仍按已播内容分段，不退回整块原文。 */
@Preview(name = "Caption progress", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Caption progress dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceCaptionProgressPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(
        phase = RealtimeVoicePhase.Speaking, listening = true,
        spokenText = "画版是2015年开播的，",
        speakingText = "“转正”更偏玩笑说法。",
        responseText = "画版是2015年开播的，\n\n“转正”更偏玩笑说法。\n\n你是在念文案，还是想聊这段内容？"),
        modelName = "DeepSeek V4"), started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

@Preview(name = "Save failure", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Save failure dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSaveFailurePreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Failed,
        failure = RealtimeVoiceFailure.Persistence), modelName = "DeepSeek V4", style = VoiceConversationStyle.ASSISTANT),
        started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

@Preview(name = "Switching", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Switching dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceSwitchingPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(modelName = "DeepSeek V4", style = VoiceConversationStyle.ASSISTANT, switching = true),
        started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

@Preview(name = "Transcribing", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Transcribing dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceTranscribingPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(
        phase = RealtimeVoicePhase.Transcribing, listening = true, inputText = "如果下雨，还有什么安排？"), modelName = "DeepSeek V4"),
        started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

/** 实时助手：当前与实时 Chatbot 同一引擎，验证胶囊新条目与 ⓘ 信息层配套的固定模型名。 */
@Preview(name = "Realtime assistant light", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Realtime assistant dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceRealtimeAssistantPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(
        phase = RealtimeVoicePhase.Listening, listening = true), modelName = "qwen3-omni-flash-realtime",
        pipeline = VoicePipeline.NATIVE_REALTIME, realtimeAvailable = true), started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

/** 实时助手工具执行中：Thinking 悬挂 + 「正在调用工具」胶囊补位静默，胶囊配色跟随主题场景。 */
@Preview(name = "Realtime assistant tool running light", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Realtime assistant tool running dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceRealtimeAssistantToolRunningPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(
        phase = RealtimeVoicePhase.Thinking, listening = true, toolNotice = "get_weather",
        inputText = "杭州今天多少度？"), modelName = "qwen3-omni-flash-realtime",
        pipeline = VoicePipeline.NATIVE_REALTIME, realtimeAvailable = true), started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

@Preview(name = "Generating", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Generating dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceGeneratingPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Thinking,
        inputText = "如果下雨，还有什么安排？", responseText = "那就把节奏放得更慢一点。"), modelName = "DeepSeek V4"),
        started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

@Preview(name = "Muted", widthDp = 400, heightDp = 864, locale = "zh-rCN")
@Preview(name = "Muted dark", widthDp = 400, heightDp = 864, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceMutedPreview() {
    GimiTheme { RealtimeVoiceScreen(RealtimeVoiceUiState(voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening,
        muted = true), modelName = "DeepSeek V4"), started = true, onStartCall = {}, onToggleMute = {}, onEnd = {}) }
}

/** 胶囊编辑与附件状态；包含软键盘弹出后的紧凑可视高度（heightDp = 520）及深浅色生产布局预览。 */
@Preview(name = "Capsule editing light (IME compact)", widthDp = 390, heightDp = 520, locale = "zh-rCN")
@Preview(name = "Capsule editing dark (IME compact)", widthDp = 390, heightDp = 520, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceCapsuleEditingPreview() = VoiceCapsulePreview(attachments = false)

@Preview(name = "Capsule attachments light", widthDp = 400, heightDp = 560, locale = "zh-rCN")
@Preview(name = "Capsule attachments dark", widthDp = 400, heightDp = 560, locale = "zh-rCN", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VoiceCapsuleAttachmentsPreview() = VoiceCapsulePreview(attachments = true)

@Composable
private fun VoiceCapsulePreview(attachments: Boolean) {
    GimiTheme {
        val draft = MessageData(
            text = "帮我看看这份方案",
            attachments = if (attachments) listOf(DraftAttachment(
                reference = "preview-file", displayName = "项目方案.pdf", mimeType = "application/pdf",
                sizeBytes = 2048, category = AttachmentCategory.DOCUMENT,
            )) else emptyList(),
        )
        RealtimeVoiceScreen(
            uiState = RealtimeVoiceUiState(
                active = true,
                conversationTitle = "简历修改建议",
                modelName = "deepseek-flash",
                voice = RealtimeVoiceState(phase = RealtimeVoicePhase.Listening, inputPaused = true),
                composer = VoiceComposerUiState(editing = true, draft = draft),
            ),
            started = true, onStartCall = {}, onToggleMute = {}, onEnd = {},
            composerContent = {
                VoiceComposerInput(
                    params = ComposerInputContentParams(
                        messageData = draft, isGenerating = false,
                        voiceInputState = VoiceInputUiState.Idle,
                        isVoiceInputAvailable = false, voiceErrorMessage = null,
                        onVoiceErrorShown = {}, onTextChange = {}, onRemoveAttachment = {},
                        onSendClick = {}, onStopClick = {}, onVoiceInputStart = {},
                    ), sendEnabled = true, onClose = {},
                )
            },
        )
    }
}
