package github.ponyhuang.asssistantai.ui.chat

import androidx.annotation.MainThread

/** UI-facing lifecycle of the currently active agent turn. */
internal data class AgentRunStatus(
    val isRunning: Boolean,
    val turnComplete: Boolean,
)

/**
 * Applies an ADK event without treating non-partial tool events as the end of the turn.
 *
 * A non-partial event can be an intermediate function call or function response. Only
 * [turnComplete] is authoritative for normal completion; coroutine completion remains the
 * fallback when a provider ends its flow without emitting that marker.
 */
internal fun AgentRunStatus.afterEvent(
    partial: Boolean,
    turnComplete: Boolean,
): AgentRunStatus = when {
    turnComplete -> copy(isRunning = false, turnComplete = true)
    partial -> copy(isRunning = true, turnComplete = false)
    else -> this
}

/** Prevents a replaced run from clearing the state owned by a newer run. */
@MainThread
internal class AgentRunOwnership {
    private var currentToken: Any? = null

    fun claim(): Any = Any().also { currentToken = it }

    fun invalidate() {
        currentToken = null
    }

    fun isOwnedBy(token: Any): Boolean = currentToken === token
}
