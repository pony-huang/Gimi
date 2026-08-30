package github.ponyhuang.gimi.data.agent.tools.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
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
        val launch = {
            launchIntentSafely(
                summary = summary,
                canResolve = { intent.resolveActivity(context.packageManager) != null },
                launch = {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            )
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return launch()
        }
        return runCatching {
            val result = CompletableFuture<Map<String, Any>>()
            check(mainHandler.post { result.complete(launch()) }) {
                "The main thread rejected the action."
            }
            result.get(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }.getOrElse { error ->
            mapOf(
                "success" to false,
                "error" to "$title failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    private companion object {
        const val LAUNCH_TIMEOUT_SECONDS = 5L
    }
}

internal fun launchIntentSafely(
    summary: String,
    canResolve: () -> Boolean,
    launch: () -> Unit,
): Map<String, Any> {
    if (!canResolve()) {
        return mapOf("success" to false, "error" to "No installed app can handle this action.")
    }
    return runCatching {
        launch()
        mapOf("success" to true, "summary" to summary)
    }.getOrElse { error ->
        mapOf(
            "success" to false,
            "error" to (error.message ?: "The action could not be started."),
        )
    }
}
