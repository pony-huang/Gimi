package github.ponyhuang.asssistantai.domain.conversation.repository

import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunEvent
import github.ponyhuang.asssistantai.domain.conversation.model.ImageAttachment
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.Flow

interface ChatAgentRepository {
    suspend fun send(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        imageAttachments: List<ImageAttachment>,
    ): Flow<ChatRunEvent>

    suspend fun respondToToolConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<ChatRunEvent>

    suspend fun releaseSession(sessionId: String)
}

interface ChatAttachmentRepository {
    suspend fun read(references: List<String>): List<ImageAttachment>
}
