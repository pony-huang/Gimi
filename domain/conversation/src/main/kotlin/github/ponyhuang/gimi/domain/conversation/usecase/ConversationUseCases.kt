package github.ponyhuang.gimi.domain.conversation.usecase

import github.ponyhuang.gimi.domain.conversation.model.Conversation
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.repository.ConversationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveConversationsUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    operator fun invoke(): StateFlow<List<Conversation>> = repository.conversations
}

class CreateConversationUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(
        initialModel: String = "",
        activate: Boolean = true,
        initialToolConfiguration: ConversationToolConfiguration? = null,
    ): String = repository.createConversation(initialModel, activate, initialToolConfiguration)
}

class DeleteConversationUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(sessionId: String, activeSessionId: String): Boolean {
        if (sessionId.isBlank() || sessionId == activeSessionId) return false
        repository.deleteConversation(sessionId)
        return true
    }
}
