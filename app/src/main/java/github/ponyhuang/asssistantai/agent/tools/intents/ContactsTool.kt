package github.ponyhuang.asssistantai.agent.tools.intents

import android.content.Intent
import android.provider.ContactsContract
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "pick_contact", description = "Opens Contacts so the user can choose a contact.") fun pickContact(): Map<String, Any> = queue.request("Choose contact", "Open Contacts to choose a contact.", Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
    @Tool(name = "view_contact", description = "Opens an existing contact in the Contacts app.") fun viewContact(@Param("A contact content URI.") contactUri: String): Map<String, Any> = external("View contact", contactUri, Intent.ACTION_VIEW)
    @Tool(name = "edit_contact", description = "Opens an existing contact for editing in the Contacts app.") fun editContact(@Param("A contact content URI.") contactUri: String): Map<String, Any> = external("Edit contact", contactUri, Intent.ACTION_EDIT)
    @Tool(name = "insert_contact", description = "Opens Contacts to create a new contact with the provided display name.") fun insertContact(@Param("Contact display name.") name: String): Map<String, Any> = queue.request(
        "Create contact", "Create a contact named ${nonBlank(name, "name")}.",
        Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).putExtra(ContactsContract.Intents.Insert.NAME, name.trim()),
    )
    private fun external(title: String, uri: String, action: String) = try {
        queue.request(title, "$title in the Contacts app.", Intent(action, android.net.Uri.parse(nonBlank(uri, "contactUri"))))
    } catch (e: IllegalArgumentException) { mapOf("success" to false, "error" to e.message.orEmpty()) }
}
