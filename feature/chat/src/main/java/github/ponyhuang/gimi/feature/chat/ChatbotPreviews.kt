package github.ponyhuang.gimi.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.conversation.model.*
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 生产组件的隔离 fixture，覆盖模式入口、空会话与消息状态。 */
@Composable
private fun ChatbotPreview(dark: Boolean = false, hasMessages: Boolean = false) {
    val selection = ModelSelection("preview", "messages", "deepseek-flash")
    val messages = if (hasMessages) listOf(
        Message(id = "user", invocationId = "turn", author = "user", role = MessageRole.User, textParts = listOf(TextPart(text = "请介绍一下联网搜索和文件解析"))),
        Message(id = "assistant", invocationId = "turn", author = "deepseek-flash", role = MessageRole.Assistant, textParts = listOf(TextPart(text = "可以通过官方工具搜索网页，也可以上传文件后结合内容继续提问。")), turnComplete = true),
    ) else emptyList()
    val instance = ModelServiceInstance("preview", "DeepSeek", "deepseek", true, true, true, "preview",
        listOf(ServiceConnection("messages", ServiceCapability.Conversation, ApiProtocol.Anthropic, "https://api.deepseek.com/anthropic", models = listOf(Model("deepseek-flash", "deepseek-flash")))))
        .withDerivedUploadCapabilities()
    GimiTheme(darkTheme = dark) {
        ChatScaffold(
            state = ChatUiState(sessionId = "preview", messages = messages, listItems = messages.toChatListItems(TimelineActivityState()),
                availableLLMModelSettings = listOf(instance), currentModelSelection = selection, modelCatalogLoadState = CatalogLoadState.Ready,
                toolConfiguration = ConversationToolConfiguration(thinkingEnabled = false)),
            partChannelProvider = { null }, onSend = { _, _, _ -> }, onStop = {},
            onTranscribeVoice = { _, _, _, _ -> }, onToggleSpeechPlayback = { _, _ -> },
            onOpenVoice = {}, onOpenDocument = {}, onToolConfirmation = {}, onToolConfirmationAlwaysAllow = {}, onFullAccessChange = {},
            onSelectModel = {}, onModelSwitchBlocked = {}, onOpenDrawer = {}, onConfigureModels = {}, onNewConversation = {},
            onReasoningEffortChange = {}, onMcpServerEnabledChange = { _, _ -> }, onOfficialToolOpened = {},
            onOfficialToolFunctionEnabledChange = { _, _, _ -> }, onOfficialToolFunctionsRetry = {}, chatMode = ChatMode.CHATBOT,
        )
    }
}

@Preview(name = "Chatbot · empty · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun ChatbotEmptyLightPreview() = ChatbotPreview()

@Preview(name = "Chatbot · messages · dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun ChatbotMessagesDarkPreview() = ChatbotPreview(dark = true, hasMessages = true)
