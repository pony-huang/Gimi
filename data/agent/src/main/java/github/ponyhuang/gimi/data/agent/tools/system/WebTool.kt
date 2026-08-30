package github.ponyhuang.gimi.data.agent.tools.system

import android.app.SearchManager
import android.content.Intent
import androidx.core.net.toUri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Web 域工具：调用系统搜索意图直接在搜索类应用中检索，以及打开 HTTP/HTTPS URL。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.WEB]。
 */
@Singleton
class WebTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "search_web", description = "Opens a web search for the provided query in an installed search-capable app.")
    fun searchWeb(@Param("Web search query.") query: String): Map<String, Any> {
        val value = query.trim()
        if (value.isEmpty()) return mapOf("success" to false, "error" to "query must not be blank.")
        return queue.request(
            "Web search",
            "Search the web for $value.",
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, value),
        )
    }

    @Tool(name = "open_url", description = "Opens an HTTP or HTTPS URL in the user's browser.")
    fun openUrl(@Param("HTTP or HTTPS URL to open.") url: String): Map<String, Any> {
        val uri = url.trim().toUri()
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            return mapOf("success" to false, "error" to "Use a complete http or https URL.")
        }
        return queue.request("Open browser", "Open ${uri.host} in a browser.", Intent(Intent.ACTION_VIEW, uri))
    }
}
