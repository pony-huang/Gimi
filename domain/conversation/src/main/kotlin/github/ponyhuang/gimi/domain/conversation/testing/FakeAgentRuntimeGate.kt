package github.ponyhuang.gimi.domain.conversation.testing

import github.ponyhuang.gimi.domain.conversation.runtime.ActiveAgentTask
import github.ponyhuang.gimi.domain.conversation.runtime.AgentMutationResult
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRunLease
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeGate
import github.ponyhuang.gimi.domain.conversation.runtime.AgentRuntimeState
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskPhase
import github.ponyhuang.gimi.domain.conversation.runtime.AgentTaskSource
import github.ponyhuang.gimi.domain.conversation.runtime.isBusy
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Test fake of [AgentRuntimeGate] with configurable initial state and call recording.
 *
 * - [initialState] defaults to [AgentRuntimeState.Idle]; use [busy] for a busy gate.
 * - [acquire] records every call in [acquisitions] and returns a lease that records
 *   [AgentRunLease.updatePhase] in [phaseMutations] and [AgentRunLease.release] in
 *   [releaseCount]; set [acquireException] to make it throw instead.
 * - [runMutation] returns [AgentMutationResult.BlockedByActiveAgent] while the current
 *   state is busy, otherwise applies the block.
 *
 * 测试夹具由 `domain:conversation` 提供；feature 测试需要
 * `testImplementation(project(":domain:conversation"))` 来使用。
 */
class FakeAgentRuntimeGate(
    initialState: AgentRuntimeState = AgentRuntimeState.Idle,
) : AgentRuntimeGate {

    data class Acquisition(
        val source: AgentTaskSource,
        val sessionId: String?,
        val phase: AgentTaskPhase,
    )

    override val state = MutableStateFlow(initialState)

    val acquisitions = mutableListOf<Acquisition>()
    val phaseMutations = mutableListOf<AgentTaskPhase>()
    var releaseCount = 0
        private set

    var acquireException: Throwable? = null

    override suspend fun acquire(
        source: AgentTaskSource,
        sessionId: String?,
        phase: AgentTaskPhase,
    ): AgentRunLease {
        acquisitions += Acquisition(source, sessionId, phase)
        acquireException?.let { throw it }
        return object : AgentRunLease {
            override fun updatePhase(phase: AgentTaskPhase) {
                phaseMutations += phase
            }

            override fun release() {
                releaseCount++
            }
        }
    }

    override suspend fun <T> runMutation(block: suspend () -> T): AgentMutationResult<T> =
        if (state.value.isBusy) {
            AgentMutationResult.BlockedByActiveAgent
        } else {
            AgentMutationResult.Applied(block())
        }

    companion object {
        fun busy(
            source: AgentTaskSource = AgentTaskSource.CHAT,
            phase: AgentTaskPhase = AgentTaskPhase.GENERATING,
        ): FakeAgentRuntimeGate = FakeAgentRuntimeGate(
            AgentRuntimeState.Busy(
                listOf(ActiveAgentTask(source = source, phase = phase)),
            ),
        )
    }
}