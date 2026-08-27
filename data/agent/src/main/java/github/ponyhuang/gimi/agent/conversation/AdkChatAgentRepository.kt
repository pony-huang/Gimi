package github.ponyhuang.gimi.agent.conversation

import com.google.adk.kt.events.Event
import com.google.adk.kt.types.FunctionCall
import github.ponyhuang.gimi.agent.AgentChatRunner
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionCall
import github.ponyhuang.gimi.domain.conversation.model.ChatFunctionResponse
import github.ponyhuang.gimi.domain.conversation.model.parseLocalFileSearchResult
import github.ponyhuang.gimi.domain.conversation.model.parseRemoteImageResult
import github.ponyhuang.gimi.domain.conversation.model.ChatRunEvent
import github.ponyhuang.gimi.domain.conversation.model.ChatRunPart
import github.ponyhuang.gimi.domain.conversation.model.FileAttachment
import java.io.File
import github.ponyhuang.gimi.domain.conversation.model.ConversationToolConfiguration
import github.ponyhuang.gimi.domain.conversation.model.ToolConfirmationRequest
import github.ponyhuang.gimi.domain.conversation.repository.ChatAgentRepository
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AdkChatAgentRepository @Inject constructor(
    private val runner: AgentChatRunner,
) : ChatAgentRepository {
    override suspend fun send(
        sessionId: String,
        selection: ModelSelection,
        text: String,
        fileAttachments: List<FileAttachment>,
        toolConfiguration: ConversationToolConfiguration?,
    ): Flow<ChatRunEvent> = runner.send(
        userId = USER_ID,
        sessionId = sessionId,
        selection = selection,
        text = text,
        fileAttachments = fileAttachments,
        toolConfiguration = toolConfiguration,
    ).map { it.toDomain() }

    override suspend fun releaseSession(sessionId: String) {
        runner.releaseSession(sessionId)
    }

    override suspend fun respondToToolConfirmation(
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<ChatRunEvent> = runner.respondToToolConfirmation(
        userId = USER_ID,
        sessionId = sessionId,
        confirmationCallId = confirmationCallId,
        confirmed = confirmed,
    ).map { it.toDomain() }

    private fun Event.toDomain() = ChatRunEvent(
        id = id,
        invocationId = invocationId,
        author = author,
        parts = content?.parts.orEmpty().map { part ->
            ChatRunPart(
                text = part.text,
                thought = part.thought == true,
                attachment = part.inlineData?.let { blob ->
                    val mimeType = blob.mimeType ?: return@let null
                    val data = blob.data ?: return@let null
                    val displayName = blob.displayName.orEmpty()
                    FileAttachment.fromBytes(
                        mimeType = mimeType,
                        data = data,
                        displayName = displayName,
                    )
                } ?: part.fileData?.let { file ->
                    val mimeType = file.mimeType ?: return@let null
                    val reference = file.fileUri ?: return@let null
                    val payload = File(reference.removePrefix("file://"))
                    require(payload.isFile) { "Attachment payload is unavailable: $reference" }
                    FileAttachment.fromFile(
                        file = payload,
                        mimeType = mimeType,
                        displayName = file.displayName.orEmpty(),
                    )
                },
            )
        },
        functionCalls = functionCalls().map { it.toDomain() },
        functionResponses = functionResponses().map {
            ChatFunctionResponse(
                id = it.id,
                name = it.name,
                localFileSearchResult = parseLocalFileSearchResult(it.name, it.response),
                remoteImageResult = parseRemoteImageResult(it.response),
            )
        },
        partial = partial,
        turnComplete = turnComplete,
        errorCode = errorCode,
        errorMessage = errorMessage,
        timestamp = timestamp,
    )

    private fun FunctionCall.toDomain(): ChatFunctionCall {
        val rawArgs = args.mapKeys { it.key.toString() }
        val confirmation = takeIf {
            name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME
        }?.let {
            val original = args[FunctionCall.ORIGINAL_FUNCTION_CALL_KEY] as? Map<*, *>
            val toolName = original?.get("name") as? String
            if (toolName == null) null else ToolConfirmationRequest(
                toolName = toolName,
                args = (original["args"] as? Map<*, *>)
                    .orEmpty()
                    .mapKeys { entry -> entry.key.toString() },
            )
        }
        return ChatFunctionCall(id, name, rawArgs, confirmation)
    }

    private companion object {
        const val USER_ID = AgentSessionIdentity.DEFAULT_USER_ID
    }
}
