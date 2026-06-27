package github.ponyhuang.asssistantai.agent.tools.intents

import android.app.SearchManager
import android.content.Intent
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "search_web", description = "Opens a web search for the provided query in an installed search-capable app.") fun searchWeb(@Param("Web search query.") query: String): Map<String, Any> {
        val value = query.trim(); if (value.isEmpty()) return mapOf("success" to false, "error" to "query must not be blank.")
        return queue.request("Web search", "Search the web for $value.", Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, value))
    }
}
