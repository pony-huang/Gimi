package github.ponyhuang.asssistantai.agent.tools.system

import android.content.Intent
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Deprecated("")
class NotesTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "create_note", description = "Opens a compatible notes app with a new note prefilled with the provided title and text.") fun createNote(@Param("Note title.") title: String, @Param("Note body.") text: String): Map<String, Any> = queue.request(
        "Create note", "Open a note app with the supplied title and text.",
        Intent(ACTION_CREATE_NOTE).setType("text/plain").putExtra(EXTRA_NAME, title).putExtra(EXTRA_TEXT, text),
    )
    private companion object {
        const val ACTION_CREATE_NOTE = "com.google.android.gms.actions.CREATE_NOTE"
        const val EXTRA_NAME = "com.google.android.gms.actions.extra.NAME"
        const val EXTRA_TEXT = "com.google.android.gms.actions.extra.TEXT"
    }
}
