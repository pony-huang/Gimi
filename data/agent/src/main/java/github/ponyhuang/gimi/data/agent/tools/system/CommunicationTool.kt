package github.ponyhuang.gimi.data.agent.tools.system

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通讯域工具：拨号、起草短信。
 *
 * 对应 [github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory.COMMUNICATION]。
 */
@Singleton
class CommunicationTool @Inject constructor(
    private val queue: IntentActionQueue,
) {
    @Tool(name = "dial_phone_number", description = "Opens the system dialer with a phone number.", requireConfirmation = true)
    fun dialPhoneNumber(
        @Param("Phone number to show in the system dialer.") phoneNumber: String,
    ): Map<String, Any> {
        val number = phoneNumber.trim()
        if (number.isEmpty()) return mapOf("success" to false, "error" to "phoneNumber must not be blank.")
        return queue.request(
            "Open dialer",
            "Open the dialer for $number. The user must place the call.",
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)),
        )
    }

    @Tool(name = "compose_message", description = "Opens a messaging app with a draft text message. The user reviews and sends it.", requireConfirmation = true)
    fun composeMessage(
        @Param("Recipient phone number, optional.") phoneNumber: String? = null,
        @Param("Message text.") text: String,
    ): Map<String, Any> {
        val uri = "smsto:${phoneNumber.orEmpty().trim()}".toUri()
        return queue.request(
            "Compose message",
            "Open the messaging app with a draft message.",
            Intent(Intent.ACTION_SENDTO, uri).putExtra("sms_body", text),
        )
    }
}
