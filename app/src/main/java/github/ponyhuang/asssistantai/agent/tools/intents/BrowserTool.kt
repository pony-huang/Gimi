package github.ponyhuang.asssistantai.agent.tools.intents

import android.content.Intent
import android.net.Uri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class BrowserTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "open_url", description = "Opens an HTTP or HTTPS URL in an installed browser.") fun openUrl(@Param("HTTP or HTTPS URL to open.") url: String): Map<String, Any> {
        val uri = url.trim().toUri()
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return mapOf("success" to false, "error" to "Use a complete http or https URL.")
        return queue.request("Open browser", "Open ${uri.host} in a browser.", Intent(Intent.ACTION_VIEW, uri))
    }
}
