package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class MessagingTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "compose_message", description = "Opens a messaging app with a draft text message. The user reviews and sends it.", requireConfirmation = true) fun composeMessage(@Param("Recipient phone number, optional.") phoneNumber: String? = null, @Param("Message text.") text: String): Map<String, Any> {
        val uri = "smsto:${phoneNumber.orEmpty().trim()}".toUri()
        return queue.request("Compose message", "Open the messaging app with a draft message.", Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", text))
    }
}
