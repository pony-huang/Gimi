package github.ponyhuang.asssistantai.ui.chat

import github.ponyhuang.asssistantai.model.Message

/** Whether a message has any content that can be rendered with the current display preference. */
internal fun Message.isVisibleInChat(showToolActivity: Boolean): Boolean =
    error != null ||
        textParts.isNotEmpty() ||
        imageAttachments.isNotEmpty() ||
        (showToolActivity && (functionCalls.isNotEmpty() || functionResponses.isNotEmpty()))
