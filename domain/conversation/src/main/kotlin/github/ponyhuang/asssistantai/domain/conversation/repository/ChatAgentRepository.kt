package github.ponyhuang.asssistantai.domain.conversation.repository

import github.ponyhuang.asssistantai.domain.conversation.model.ChatRunEvent
import github.ponyhuang.asssistantai.domain.conversation.model.ImageAttachment
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelSelection
import kotlinx.coroutines.flow.Flow

interface ChatAgentRepository {
    suspend fun activateModel(selection: ModelSelection?): Result<Unit>

    suspend fun recreate(): Result<Unit>

    suspend fun send(
        sessionId: String,
        text: String,
        imageAttachments: List<ImageAttachment>,
    ): Flow<ChatRunEvent>

    suspend fun respondToToolConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<ChatRunEvent>
}

interface ChatAttachmentRepository {
    suspend fun read(references: List<String>): List<ImageAttachment>
}
