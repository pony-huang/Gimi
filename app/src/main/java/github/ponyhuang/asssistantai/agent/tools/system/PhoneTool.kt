package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import android.net.Uri
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "dial_phone_number", description = "Opens the system dialer with a phone number. The user initiates the call.", requireConfirmation = true) fun dial(@Param("Phone number to show in the system dialer.") phoneNumber: String): Map<String, Any> {
        val number = phoneNumber.trim()
        if (number.isEmpty()) return mapOf("success" to false, "error" to "phoneNumber must not be blank.")
        return queue.request("Open dialer", "Open the dialer for $number. The user must place the call.", Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }
}
