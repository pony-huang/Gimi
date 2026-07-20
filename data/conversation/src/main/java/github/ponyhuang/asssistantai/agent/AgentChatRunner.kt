package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.ResumabilityConfig
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.annotations.ExperimentalResumabilityFeature
import com.google.adk.kt.apps.App
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.events.Event
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import github.ponyhuang.asssistantai.domain.conversation.model.ImageAttachment

/**
 * Agent 聊天运行器 — 把 ADK `InMemoryRunner.runAsync(...)` 封装为一个简洁的 `Flow<Event>`。
 *
 * 设计要点：
 * - 内部持有一个 `InMemoryRunner`，构造期注入 [sessionService]（持久化的 `RoomSessionService`）与 [artifactService]（持久化的 `FileArtifactService`）。
 * - 不再持有任何 in-memory 默认实现；持久化层由 [AsssistantaiApp] 注入。
 * - Agent **不是一次性构建**：`factory` 在每次 [recreate] 时重新调用，让用户切换
 *   `ModelServiceStore` 中的启用服务后，下一次"新建对话"能看到新模型。当前正在进行的
 *   会话不受影响（`send` 时已快照 runner 引用）。
 * - 不对 `Event` 做任何加工；Event → UI 渲染的合并工作由 `ChatViewModel` 的 reducer 完成。
 * - `runConfig` 默认开启 SSE 流式，便于 UI 端做打字机效果。
 *
 * 线程模型：所有调用都通过协程 `Flow` 完成，框架本身不持有任何额外线程。
 */
class AgentChatRunner(
    private val factory: suspend () -> BaseAgent,
    private val sessionService: SessionService,
    private val artifactService: ArtifactService?,
    private val configurationRevision: () -> Any = { Unit },
) {

    /**
     * 当前生效的 runner；[recreate] 会整体替换为新工厂产出的 agent 上跑出来的实例。
     *
     * 声明 `@Volatile` 是因为 [recreate] 由主线程触发（顶栏"新建对话"按钮），
     * [send] 在 `Dispatchers.IO` 上消费 `runAsync` 返回的 Flow——需要 happens-before。
     */
    @Volatile
    private var runner: InMemoryRunner? = null
    @Volatile
    private var runnerRevision: Any? = null
    private val runnerMutex = Mutex()

    @OptIn(ExperimentalResumabilityFeature::class)
    private fun buildRunner(agent: BaseAgent): InMemoryRunner = InMemoryRunner(
        app = App(
            appName = APP_NAME,
            rootAgent = agent,
            resumabilityConfig = ResumabilityConfig(isResumable = true),
        ),
        sessionService = sessionService,
        artifactService = artifactService,
    )

    /**
     * 用工厂产出的最新 agent 重建底层 runner。
     *
     * 调用时机："新建对话"按钮——保证下一条消息从新 agent 出发。当前正在 `runAsync` 中的
     * 会话不受影响（快照在 [send] 入口处已取好）。
     */
    suspend fun recreate() {
        runnerMutex.withLock {
            runner = buildRunner(factory())
            runnerRevision = configurationRevision()
        }
    }

    /**
     * Drops the current runner without creating a replacement.
     *
     * Used when no model is currently available, so a runner built from a model that has since
     * been disabled or removed cannot continue serving later requests.
     */
    suspend fun invalidate() {
        runnerMutex.withLock {
            runner = null
            runnerRevision = null
        }
    }

    /**
     * 把用户文本发送给 Agent，返回一个 Event 流。
     *
     * @param userId 用户 id（用于会话归属）
     * @param sessionId 会话 id（同会话的历史会一并送入 LLM）
     * @param text 用户输入文本（可为空，只要包含图片）
     * @param imageAttachments 用户选中的图片，作为 ADK inline data 传给模型适配器
     * @param runConfig 可选运行配置；默认开启 SSE 流式
     */
    suspend fun send(
        userId: String,
        sessionId: String,
        text: String,
        imageAttachments: List<ImageAttachment> = emptyList(),
    ): Flow<Event> {
        // 快照当前 runner；中途 recreate() 不会改本次 send 的行为。
        val activeRunner = currentRunnerForNewTurn()
        val parts = buildList {
            text.takeIf(String::isNotBlank)?.let { add(Part(text = it)) }
            imageAttachments.forEach { image ->
                add(Part(inlineData = Blob(mimeType = image.mimeType, data = image.data)))
            }
        }
        val newMessage = Content(
            role = Role.USER,
            parts = parts,
        )
        return activeRunner.runAsync(
            userId = userId,
            sessionId = sessionId,
            invocationId = null,
            newMessage = newMessage,
            stateDelta = null,
            runConfig = RunConfig(streamingMode = StreamingMode.SSE),
        ).flowOn(Dispatchers.IO)
    }

    /** Resumes a paused ADK tool-confirmation request with the user's decision. */
    suspend fun respondToToolConfirmation(
        userId: String,
        sessionId: String,
        confirmationCallId: String,
        confirmed: Boolean,
    ): Flow<Event> {
        val activeRunner = currentRunnerForResume()
        val confirmationResponse = Content(
            role = Role.USER,
            parts = listOf(
                Part(
                    functionResponse = FunctionResponse(
                        name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                        id = confirmationCallId,
                        response = mapOf("confirmed" to confirmed),
                    ),
                ),
            ),
        )
        return activeRunner.runAsync(
            userId = userId,
            sessionId = sessionId,
            invocationId = null,
            newMessage = confirmationResponse,
            stateDelta = null,
            runConfig = RunConfig(streamingMode = StreamingMode.SSE),
        ).flowOn(Dispatchers.IO)
    }

    private suspend fun currentRunnerForNewTurn(): InMemoryRunner {
        val expectedRevision = configurationRevision()
        runner?.takeIf { runnerRevision == expectedRevision }?.let { return it }
        return runnerMutex.withLock {
            runner?.takeIf { runnerRevision == expectedRevision } ?: buildRunner(factory()).also {
                runner = it
                runnerRevision = expectedRevision
            }
        }
    }

    private suspend fun currentRunnerForResume(): InMemoryRunner = runner ?: runnerMutex.withLock {
        runner ?: buildRunner(factory()).also {
            runner = it
            runnerRevision = configurationRevision()
        }
    }

    companion object {
        const val APP_NAME: String = "AsssistantaiApp"
    }
}
