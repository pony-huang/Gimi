package github.ponyhuang.gimi.domain.modelcatalog.model

const val OPENAI_CHATBOT_SEARCH_MODEL = "gpt-5-search-api"
const val DEEPSEEK_CHATBOT_SEARCH_MODEL = "deepseek-flash"

fun supportsChatbotProtocol(templateId: String?, protocol: ApiProtocol): Boolean =
    protocol == ApiProtocol.Anthropic ||
        (templateId == "openai" && protocol == ApiProtocol.OpenaiChatcompletions)
