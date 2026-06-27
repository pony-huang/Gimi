package github.ponyhuang.asssistantai.agent.tools.intents

import android.content.Intent
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "get_file", description = "Opens a file picker so the user can choose a file of the requested MIME type.") fun getFile(@Param("MIME type to select, such as image/* or application/pdf.") mimeType: String): Map<String, Any> = pick(Intent.ACTION_GET_CONTENT, "Choose file", mimeType)
    @Tool(name = "open_file", description = "Opens the system document picker so the user can select a persistable file of the requested MIME type.") fun openFile(@Param("MIME type to open, such as image/* or application/pdf.") mimeType: String): Map<String, Any> = pick(Intent.ACTION_OPEN_DOCUMENT, "Open file", mimeType)
    private fun pick(action: String, title: String, mimeType: String): Map<String, Any> {
        val type = mimeType.trim(); if (type.isEmpty() || !type.contains('/')) return mapOf("success" to false, "error" to "mimeType must be a MIME type.")
        return queue.request(title, "$title with type $type.", Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType(type))
    }
}
