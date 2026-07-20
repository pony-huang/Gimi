package github.ponyhuang.asssistantai.feature.chat

import github.ponyhuang.asssistantai.domain.conversation.model.Message

/** Whether a message has any content that can be rendered with the current display preference. */
internal fun Message.isVisibleInChat(showToolActivity: Boolean): Boolean =
    error != null ||
        textParts.isNotEmpty() ||
        imageAttachments.isNotEmpty() ||
        (showToolActivity && (functionCalls.isNotEmpty() || functionResponses.isNotEmpty()))
