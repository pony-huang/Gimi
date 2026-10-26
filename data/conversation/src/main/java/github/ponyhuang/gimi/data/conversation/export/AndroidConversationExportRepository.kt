package github.ponyhuang.gimi.data.conversation.export

import android.content.Context
import android.net.Uri
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import dagger.hilt.android.qualifiers.ApplicationContext
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRepository
import github.ponyhuang.gimi.domain.conversation.export.ConversationExportRequest
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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
) : ConversationExportRepository {
    override suspend fun prepare(sessionId: String?): ConversationExportRequest? = withContext(Dispatchers.IO) {
        val ids = if (sessionId != null) {
            require(sessionId.isNotBlank())
            listOf(sessionId)
        } else {
            sessionService.listSessions(
                appName = AgentSessionIdentity.APP_NAME,
                userId = AgentSessionIdentity.DEFAULT_USER_ID,
            ).sessions.map { session ->
                session.key.id?.takeIf(String::isNotBlank)
                    ?: throw IOException("Session has no identifier")
            }.distinct()
        }
        if (ids.isEmpty()) return@withContext null
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        ConversationExportRequest(
            sessionIds = ids,
            timestamp = timestamp,
            fileName = if (ids.size == 1) sessionFileName(timestamp, ids.single()) else "sessions_$timestamp.zip",
            mimeType = if (ids.size == 1) "application/x-ndjson" else "application/zip",
            allSessions = sessionId == null,
        )
    }

    override suspend fun export(request: ConversationExportRequest, destination: String): Unit =
        withContext(Dispatchers.IO) {
            require(request.sessionIds.isNotEmpty())
            val staged = File.createTempFile("session-export-", ".tmp", context.cacheDir)
            try {
                staged.outputStream().buffered().use { output -> writeArchive(request, output) }
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

    private suspend fun writeArchive(request: ConversationExportRequest, output: OutputStream) {
        if (request.sessionIds.size == 1) {
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                SessionJsonlWriter.write(readSession(request.sessionIds.single()), writer)
            }
        } else {
            ZipOutputStream(output, Charsets.UTF_8).use { zip ->
                request.sessionIds.forEach { id ->
                    currentCoroutineContext().ensureActive()
                    val session = readSession(id)
                    zip.putNextEntry(ZipEntry(sessionFileName(request.timestamp, id)))
                    // writer 只刷新而不关闭，否则会提前结束整个 ZIP 流。
                    val writer = OutputStreamWriter(zip, Charsets.UTF_8)
                    SessionJsonlWriter.write(session, writer)
                    writer.flush()
                    zip.closeEntry()
                }
            }
        }
    }

    private suspend fun readSession(id: String) = sessionService.getSession(
        SessionKey(AgentSessionIdentity.APP_NAME, AgentSessionIdentity.DEFAULT_USER_ID, id),
    ) ?: throw IOException("Session no longer exists")
}

/** 编码路径字符，避免会话 ID 被解释成 ZIP 目录或覆盖其它条目。 */
private fun sessionFileName(timestamp: String, id: String): String =
    "${timestamp}_${URLEncoder.encode(id, Charsets.UTF_8.name())}.jsonl"
