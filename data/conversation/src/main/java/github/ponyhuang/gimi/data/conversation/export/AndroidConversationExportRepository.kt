package github.ponyhuang.gimi.data.conversation.export

import android.content.Context
import android.net.Uri
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.conversation.conversationExportDirectorySpec
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRepository
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRequest
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportSession
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 从原始 SessionService 读取归档；私有临时文件完成后才打开用户选择的文档。 */
@Singleton
class AndroidConversationExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionService: SessionService,
    private val storageRegistry: StorageRegistry,
) : ConversationExportRepository {
    override suspend fun prepare(target: ConversationExportSession): ConversationExportRequest =
        withContext(Dispatchers.IO) {
            require(target.sessionId.isNotBlank())
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            ConversationExportRequest(
                userId = target.userId,
                sessionId = target.sessionId,
                timestamp = timestamp,
                fileName = sessionFileName(timestamp, target.sessionId),
                mimeType = "application/x-ndjson",
            )
        }

    override suspend fun export(request: ConversationExportRequest, destination: String): Unit =
        withContext(Dispatchers.IO) {
            val staged = File.createTempFile(
                "session-export-",
                ".tmp",
                storageRegistry.resolve(conversationExportDirectorySpec.id, create = true),
            )
            try {
                staged.outputStream().buffered().use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        SessionJsonlWriter.write(readSession(request), writer)
                    }
                }
                currentCoroutineContext().ensureActive()
                val output = context.contentResolver.openOutputStream(Uri.parse(destination), "wt")
                    ?: throw IOException("Cannot open session export document")
                output.use { target ->
                    staged.inputStream().buffered().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            target.write(buffer, 0, count)
                        }
                    }
                }
            } finally {
                staged.delete()
            }
        }

    /** 会话必须从与写入时一致的分区读取，助手与 Chatbot 的会话互不可见。 */
    private suspend fun readSession(request: ConversationExportRequest) =
        sessionService.getSession(
            SessionKey(AgentSessionIdentity.APP_NAME, request.userId, request.sessionId),
        ) ?: throw IOException("Session no longer exists")
}

/** 编码路径字符，避免会话 ID 被解释成路径分隔符或非法文件名。 */
private fun sessionFileName(timestamp: String, id: String): String =
    "${timestamp}_${URLEncoder.encode(id, Charsets.UTF_8.name())}.jsonl"
