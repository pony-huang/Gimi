package github.ponyhuang.asssistantai.agent.tools.intents

import android.content.Intent
import com.google.adk.kt.annotations.Tool
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideHailingTool @Inject constructor(private val queue: IntentActionQueue) {
    @Tool(name = "request_ride", description = "Opens a ride-hailing app so the user can request a ride.") fun requestRide(): Map<String, Any> = queue.request("Request ride", "Open a ride-hailing app to request a ride.", Intent("android.intent.action.RESERVE_TAXI"))
}
