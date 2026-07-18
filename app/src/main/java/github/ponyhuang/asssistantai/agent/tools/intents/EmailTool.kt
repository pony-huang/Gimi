package github.ponyhuang.asssistantai.agent.tools.intents

import android.content.Intent
import android.net.Uri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Deprecated("")
class EmailTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "compose_email", description = "Opens an email app with a composed draft. The user reviews and sends it.") fun composeEmail(
        @Param("Recipient email addresses.") recipients: List<String>,
        @Param("Email subject.") subject: String? = null,
        @Param("Email body.") body: String? = null,
    ): Map<String, Any> {
        val to = recipients.map(String::trim).filter(String::isNotEmpty)
        if (to.isEmpty()) return mapOf("success" to false, "error" to "At least one recipient is required.")
        return queue.request("Compose email", "Open an email draft for ${to.joinToString()}.", Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, to.toTypedArray()); subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }; body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        })
    }
}
