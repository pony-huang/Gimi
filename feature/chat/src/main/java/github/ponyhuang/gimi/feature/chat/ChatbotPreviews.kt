package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.conversation.model.*
import github.ponyhuang.gimi.domain.modelcatalog.model.*
import github.ponyhuang.gimi.ui.theme.GimiTheme

/** 生产组件的隔离 fixture，覆盖模式入口、空会话与消息状态。 */
@Composable
private fun ChatbotPreview(dark: Boolean = false, hasMessages: Boolean = false) {
    val selection = ModelSelection("preview", "messages", "deepseek-flash")
    val uriHandler = LocalUriHandler.current
    val webReply = "可以通过官方工具搜索网页，也可以上传文件后结合内容继续提问。"
    val messages = if (hasMessages) listOf(
        Message(id = "user", invocationId = "turn", author = "user", role = MessageRole.User, textParts = listOf(TextPart(text = "请介绍一下联网搜索和文件解析"))),
        Message(id = "assistant", invocationId = "turn", author = "deepseek-flash", role = MessageRole.Assistant, textParts = listOf(TextPart(text = webReply)), turnComplete = true,
            webCitations = listOf(
                WebCitation(WebSource("https://example.org/search", "网页搜索"), webReply.indexOf('，') + 1),
                WebCitation(WebSource("https://example.com/files", "从上传的文件中获取内容并继续追问的完整说明"), webReply.length),
                WebCitation(WebSource("https://example.net/guide", "使用指南"), webReply.length),
                WebCitation(WebSource("https://developer.example.org/help", "帮助中心"), webReply.length),
            )),
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
            onOpenVoice = {}, onOpenDocument = {}, onOpenWebSource = uriHandler::openUri, onToolConfirmation = {}, onToolConfirmationAlwaysAllow = {}, onFullAccessChange = {},
            onSelectModel = {}, onModelSwitchBlocked = {}, onOpenDrawer = {}, onConfigureModels = {}, onNewConversation = {},
            onReasoningEffortChange = {}, onMcpServerEnabledChange = { _, _ -> }, onOfficialToolOpened = {},
            onOfficialToolFunctionEnabledChange = { _, _, _ -> }, onOfficialToolFunctionsRetry = {}, chatMode = ChatMode.CHATBOT,
        )
    }
}

@Preview(name = "Chatbot · empty · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun ChatbotEmptyLightPreview() = ChatbotPreview()

@Preview(name = "Chatbot · citations · light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun ChatbotMessagesLightPreview() = ChatbotPreview(hasMessages = true)

@Preview(name = "Chatbot · messages · dark", showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun ChatbotMessagesDarkPreview() = ChatbotPreview(dark = true, hasMessages = true)
