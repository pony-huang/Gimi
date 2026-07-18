package github.ponyhuang.asssistantai.agent.tools.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Launches an external intent after ADK has obtained the user's tool confirmation. */
@Singleton
class IntentActionQueue @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @SuppressLint("QueryPermissionsNeeded")
    fun request(title: String, summary: String, intent: Intent): Map<String, Any> {
        if (intent.resolveActivity(context.packageManager) == null) {
            return mapOf("success" to false, "error" to "No installed app can handle this action.")
        }
        mainHandler.post {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return mapOf("success" to true, "summary" to summary)
    }
}
