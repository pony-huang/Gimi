package github.ponyhuang.gimi.data.agent

/** Pure title formatting rules shared by ADK callbacks and tests. */
object ConversationTitle {
    const val PROVISIONAL_MAX_LENGTH = 50
    const val GENERATED_MAX_LENGTH = 60
    const val IMAGE_MESSAGE_TITLE = "图片消息"

    fun provisional(userText: String?): String? = userText
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.let { text ->
            if (text.length > PROVISIONAL_MAX_LENGTH) {
                text.take(PROVISIONAL_MAX_LENGTH).trimEnd() + "…"
            } else {
                text
            }
        }

    fun generated(raw: String?): String? = raw
        ?.replace(Regex("[\\r\\n\\\"']+"), " ")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(GENERATED_MAX_LENGTH)
        ?.trimEnd()
        ?.takeIf(String::isNotEmpty)
}
