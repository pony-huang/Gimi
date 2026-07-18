package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import android.net.Uri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class MapsTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "show_location", description = "Opens a maps app and displays a location by name or latitude/longitude.") fun showLocation(@Param("A location name or latitude,longitude pair.") query: String): Map<String, Any> {
        val value = query.trim()
        if (value.isEmpty()) return mapOf("success" to false, "error" to "query must not be blank.")
        return queue.request("Open map", "Show $value in a maps app.", Intent(Intent.ACTION_VIEW,
            "geo:0,0?q=${Uri.encode(value)}".toUri()))
    }
}
