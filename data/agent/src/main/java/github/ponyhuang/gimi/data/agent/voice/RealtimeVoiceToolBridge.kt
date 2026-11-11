@file:OptIn(com.google.adk.kt.annotations.ExperimentalLiveApi::class)

package github.ponyhuang.gimi.data.agent.voice

import android.util.Log
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.InvocationContext
import com.google.adk.kt.agents.ReadonlyContext
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.EventActions
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.sessions.Session
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.State
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.gson.Gson
import github.ponyhuang.gimi.core.common.concurrent.cancellationAwareRunCatching
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContributionRegistry
import github.ponyhuang.gimi.data.agent.ModelRuntimeMetadata
import github.ponyhuang.gimi.data.agent.contribution.GuardedAppFunctionToolset
import github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata
import github.ponyhuang.gimi.domain.conversation.model.ToolAccessMode
import github.ponyhuang.gimi.domain.conversation.runtime.AgentSessionIdentity
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Realtime 协议的工具回写通道：由各厂商 [com.google.adk.kt.models.LiveConnection] 实现，
 * 门面的工具轮据此把执行结果写回服务端上下文并触发二轮推理。
 *
 * 两条厂商链路的事件语义一致（GA Realtime 与 Qwen-Audio 同源）：先
 * `conversation.item.create`（`function_call_output`）写回结果，再 `response.create`
 * 触发模型基于结果继续生成；发送失败静默降级并记日志，不向会话层暴露瞬时抖动。
 */
internal interface RealtimeFunctionCallSink {
    /** 写回一次工具执行结果；[outputJson] 为 JSON 字符串（含错误对象）。 */
    suspend fun sendFunctionResult(callId: String, outputJson: String)

    /** 触发服务端基于已写回的工具结果继续生成（二轮推理）。 */
    suspend fun sendResponseTrigger()
}

/**
 * 实时助手模式的工具桥（已解析、可直接下发与执行）。
 *
 * 由 [RealtimeAssistantTools] 在会话开启时一次性解析完成：工具声明经门面注入
 * `session.update` 下发，模型发起的函数调用由 [execute] 在裸 ADK [ToolContext]
 * 上执行——实时全双工链路没有 Runner 与会话持久化，上下文只承载 RunConfig
 * metadata（模型运行信息、mobile_use 归属）。确认门策略与语音 Chat 模式一致：
 * 语音通话内全部自动批准，不弹确认。
 *
 * 桥与一次通话绑定：重连复用同一份声明（工具面在通话内不变）；会话关闭时
 * [close] 释放桥自建的资源（应用函数工具集），共享单例资源不在此列。
 */
class RealtimeVoiceToolBridge(
    private val toolsByName: Map<String, BaseTool>,
    private val invocation: InvocationContext,
    private val ownedResources: List<AutoCloseable>,
) {
    private val gson = Gson()

    /** 当前可执行工具的声明列表；顺序与解析顺序一致，重名工具保留首个。 */
    val declarations: List<FunctionDeclaration> by lazy {
        toolsByName.values.mapNotNull { it.declaration() }
    }

    /** 会话结束释放桥自建资源；单个资源释放失败不影响其余资源。 */
    fun close() {
        for (resource in ownedResources) runCatching { resource.close() }
    }

    /**
     * 执行一次工具调用并返回 JSON 字符串结果（协议层 `function_call_output.output`）。
     * 未知工具、执行失败、结果为空都映射为 `{"error": ...}` 回给模型继续说话，
     * 不中断全双工会话；取消异常原样传播。
     */
    suspend fun execute(callId: String, name: String, args: Map<String, Any?>): String {
        val tool = toolsByName[name] ?: return errorJson("Unknown tool: $name")
        return try {
            // 确认门统一放行：语音通话内不弹确认，与语音 Chat 模式「完全批准」一致。
            val context = ToolContext(
                invocation,
                EventActions(),
                callId,
                ToolConfirmation(confirmed = true),
                null,
            )
            // run 的返回按 ADK 契约非空；Gson toJson(Object) 收到 null 时也会输出
            // 不带引号的 JSON null，供非契约实现兜底，不会变成带引号的 "null" 字符串。
            gson.toJson(tool.run(context, args))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            errorJson(failure.message ?: failure.javaClass.simpleName)
        }
    }

    private fun errorJson(message: String): String = gson.toJson(mapOf("error" to message))
}

/**
 * 实时助手（RealtimeAssistant）工具桥的解析入口。
 *
 * 工具面与文字/语音 Chat 的完整 Agent 同源（同一批 [github.ponyhuang.gimi.data.agent.AgentContribution]），
 * 但只保留无 Runner 也能独立执行的能力来源，显式排除依赖 ADK 会话环境与厂商远程
 * 执行的来源（白名单）：会话/工件/记忆与用户输入挂起（base）、技能沙盒编排（skill）、
 * 厂商远程声明/厂商侧执行工具（official）、模型目录工具（model）。保留来源：
 * 本地系统工具（按工具授权页开关过滤，确认类照常声明）+ 副屏 mobile_use（local）、
 * MCP 工具集（mcp，无会话勾选时按全局启用集合解析）、插件（plugin，直接工具 +
 * 工具集）、应用函数（appfunction，按启用包隔离）与 E2B 沙盒（sandbox，配置就绪时）。
 *
 * 解析整体失败（模型配置缺失等）返回 null——实时助手当次退化为无工具的实时轻聊，
 * 不阻断通话；个别来源内部失败已由各自降级逻辑处理（丢弃该来源）。
 */
@Singleton
class RealtimeAssistantTools @Inject constructor(
    private val contributions: AgentContributionRegistry,
    private val modelServices: AgentModelConfigurationSource,
    private val pluginRuntimeProvider: PluginRuntimeProvider<AgentPlugin>,
) {

    /** 按通话解析工具桥；null 表示本次通话不携带工具。 */
    suspend fun resolve(selection: ModelSelection?): RealtimeVoiceToolBridge? {
        return try {
            resolveInternal(selection)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "解析实时助手工具桥整体失败，本次通话退化为无工具", failure)
            null
        }
    }

    private suspend fun resolveInternal(selection: ModelSelection?): RealtimeVoiceToolBridge {
        // 模型运行信息不含凭据；mobile_use 归属按通话粒度分配，语义与 chat 执行句柄一致。
        // 元数据只服务于工具执行上下文，解析失败不得连带抹掉整个工具面（见 realtimeModelRuntime）。
        val modelRuntime = realtimeModelRuntime(selection)
        val pluginRuntime = pluginRuntimeProvider.runtime.value
        val metadata = ToolRunMetadata.of(
            modelRuntime = modelRuntime,
            toolConfiguration = null,
            allowConfirmationRequiredTools = true,
            mobileUseOwner = UUID.randomUUID().toString(),
        )
        val invocation = invocationContext(metadata)
        val readOnly = toolContext(invocation, callId = null)
        val spec = AgentBuildSpec(
            selection = selection,
            toolAccessMode = ToolAccessMode.ALWAYS_AVAILABLE,
            pluginRuntime = pluginRuntime,
            a2uiSupported = false,
        )
        // 白名单复用贡献方的 Agent 装配语义：本地/副屏（local）、MCP（mcp）、插件（plugin）、
        // 应用函数（appfunction）、E2B 沙盒（sandbox）。确认续接等桩工具不在工具集内，
        // 无需额外排除；依赖 Runner 会话环境或厂商远程执行的来源一律不进实时链路。
        // 装配只经 toolsets(spec)：各贡献方 tools(spec) 里的直连工具（MCP 配置/授权等
        // 会话管理类）刻意不进实时链路。插件直连工具是唯一的显式例外——插件能力只经
        // plugin.tools() 直连暴露，须在此单独取；新增来源若经 tools(spec) 声明能力型
        // 工具，须有意识地决定是否同样例外，不能被静默并入或漏掉。
        //
        // 故障隔离是关键：任一来源（MCP 探测、插件 SDK、应用函数平台查询、沙盒配置）抛错
        // 只丢弃该来源，绝不能让整座工具桥连同默认全量启用的本地工具一起消失——那会让
        // 助手模式退化成「没有工具」且界面毫无提示。取消异常始终原样传播。
        val toolsets = contributions.all
            .filter { it.id in INCLUDED_CONTRIBUTIONS }
            .flatMap { contribution ->
                cancellationAwareRunCatching { contribution.toolsets(spec) }
                    .onFailure { Log.w(TAG, "工具来源 ${contribution.id} 装配失败，跳过该来源", it) }
                    .getOrDefault(emptyList())
            }
        val pluginTools = pluginRuntime.enabledPlugins.flatMap { plugin ->
            cancellationAwareRunCatching { plugin.tools() }
                .onFailure { Log.w(TAG, "插件工具 ${plugin.pluginId} 装配失败，跳过该插件", it) }
                .getOrDefault(emptyList())
        }
        val ownedResources = toolsets.filterIsInstance<GuardedAppFunctionToolset>()
        val resolvedTools = toolsets.flatMap { toolset ->
            cancellationAwareRunCatching { toolset.getTools(readOnly) }
                .onFailure { Log.w(TAG, "工具集 ${toolset.javaClass.simpleName} 解析失败，跳过该来源", it) }
                .getOrDefault(emptyList())
        }
        val tools = (pluginTools + resolvedTools)
            .filter { tool ->
                cancellationAwareRunCatching { tool.declaration() != null }
                    .onFailure { Log.w(TAG, "工具 ${tool.name} 声明解析失败，跳过该工具", it) }
                    .getOrDefault(false)
            }
            // 重名保留首个：与本地目录「工具 ID 全局唯一」的约定一致，防御异常来源。
            .associateBy { it.name }
        Log.i(
            TAG,
            "实时助手工具面解析完成: ${tools.size} 个工具, ${toolsets.size} 个工具集, " +
                "${pluginTools.size} 个插件直连工具",
        )
        return RealtimeVoiceToolBridge(tools, invocation, ownedResources)
    }

    /**
     * 按 Realtime 通话的模型选择解析运行元数据（best-effort）。
     *
     * 不能走 `AgentLLMModelFactory.selectModelConfig`：它按「聊天模型」过滤
     * （ModelCapability.LLM），Realtime 模型只带 Realtime 标记，会被静默回退到聊天页
     * 当前选择或默认聊天模型——元数据归属到另一条模型上。这里按选择直查服务目录。
     *
     * 元数据只服务于工具执行上下文（模型运行信息、mobile_use 归属），并不是下发工具
     * 声明的前提。目录未就绪、服务被移除或模型 ID 变化时，用选择本身兜出最小元数据，
     * 不再抛错让 [resolve] 把整个工具面退化成「无工具」。
     */
    private suspend fun realtimeModelRuntime(selection: ModelSelection?): ModelRuntimeMetadata {
        // 兜底只作标识，不据此发起聊天请求；协议取 OpenAI 兼容枚举的默认值即可。
        val fallback = ModelRuntimeMetadata(
            serviceId = selection?.serviceId.orEmpty(),
            baseType = ApiProtocol.OpenaiChatcompletions,
            modelId = selection?.modelId.orEmpty(),
            fullBaseUrl = "",
        )
        val chosen = selection ?: return fallback
        val resolved = cancellationAwareRunCatching {
            modelServices.awaitReady()
            val service = modelServices.currentServices().firstOrNull { it.id == chosen.serviceId }
            val model = service?.groups
                ?.firstOrNull { it.id == chosen.groupId }?.models
                ?.firstOrNull { it.id == chosen.modelId }
            if (service != null && model != null) {
                ModelRuntimeMetadata(
                    serviceId = service.id,
                    baseType = service.apiProtocol,
                    modelId = model.id,
                    // 语音服务的 apiBaseUrl 是 wss 接入端点，不能直接当 http(s) 端点写进工具元数据。
                    fullBaseUrl = realtimeModelBaseUrl(service.apiBaseUrl),
                    supportsImages = model.multimodal.supportsImages,
                )
            } else {
                null
            }
        }.onFailure {
            Log.w(TAG, "Realtime 模型运行元数据解析失败，工具仍按选择下发", it)
        }.getOrNull()
        return resolved ?: fallback
    }

    /** 构造一次通话专用的最小 InvocationContext；无 Runner、无会话持久化。 */
    private fun invocationContext(metadata: Map<String, Any>): InvocationContext {
        val invocationId = UUID.randomUUID().toString()
        val session = Session(
            SessionKey(
                AgentSessionIdentity.APP_NAME,
                AgentSessionIdentity.DEFAULT_USER_ID,
                "realtime-assistant-$invocationId",
            ),
            State(),
            mutableListOf(),
        )
        return InvocationContext(
            session = session,
            runConfig = RunConfig(customMetadata = metadata),
            agent = ToolHostAgent,
            invocationId = invocationId,
        )
    }

    private fun toolContext(invocation: InvocationContext, callId: String?): ToolContext =
        ToolContext(invocation, EventActions(), callId, null, null)

    /** 工具桥宿主：占位 agent，只满足 InvocationContext 的装配约束，永不运行。 */
    private object ToolHostAgent : BaseAgent(
        "gimi-realtime-tool-host",
        "Hosts tool execution for the realtime voice assistant",
    ) {
        override fun runAsyncImpl(context: InvocationContext): Flow<Event> = emptyFlow()
    }

    private companion object {
        const val TAG = "RealtimeAssistantTools"

        /** 实时工具桥保留的来源白名单；排除依赖 Runner 会话环境或厂商远程执行的贡献方。 */
        private val INCLUDED_CONTRIBUTIONS = setOf("local", "mcp", "plugin", "appfunction", "sandbox")
    }
}

/**
 * Realtime 服务目录里的 `apiBaseUrl` 保存的是 `wss://` 接入端点，而
 * [ModelRuntimeMetadata.fullBaseUrl] 的契约是可按 URI 解析的 http(s) 端点
 * （[github.ponyhuang.gimi.data.agent.tools.ToolRunMetadata] 会把它写进工具上下文）。
 * 这里做与协议层 `connectRequest` 相同的 scheme 规范化，并去掉首尾空白与末尾斜杠。
 * 实时链路的工具不据此发起聊天请求，该值仅作模型运行信息的标识。
 */
internal fun realtimeModelBaseUrl(apiBaseUrl: String): String {
    val raw = apiBaseUrl.trim().trimEnd('/')
    return when {
        raw.startsWith("wss://", ignoreCase = true) -> "https://" + raw.substring(6)
        raw.startsWith("ws://", ignoreCase = true) -> "http://" + raw.substring(5)
        else -> raw
    }
}
