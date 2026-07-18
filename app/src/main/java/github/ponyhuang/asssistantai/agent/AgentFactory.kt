package github.ponyhuang.asssistantai.agent

import android.util.Log
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.callbacks.BeforeToolCallback
import com.google.adk.kt.callbacks.CallbackChoice
import com.google.adk.kt.models.Model
import com.google.adk.kt.tools.BaseTool
import com.openai.client.okhttp.OpenAIOkHttpClient
import github.ponyhuang.asssistantai.agent.tools.system.ClockTool
import github.ponyhuang.asssistantai.agent.tools.system.BrightnessTool
import github.ponyhuang.asssistantai.agent.tools.system.CalendarTool
import github.ponyhuang.asssistantai.agent.tools.system.MediaPlaybackTool
import github.ponyhuang.asssistantai.agent.tools.system.CameraTool
import github.ponyhuang.asssistantai.agent.tools.system.ContactsTool
import github.ponyhuang.asssistantai.agent.tools.system.EmailTool
import github.ponyhuang.asssistantai.agent.tools.system.FileStorageTool
import github.ponyhuang.asssistantai.agent.tools.system.MapsTool
import github.ponyhuang.asssistantai.agent.tools.system.MessagingTool
import github.ponyhuang.asssistantai.agent.tools.system.NotesTool
import github.ponyhuang.asssistantai.agent.tools.system.PhoneTool
import github.ponyhuang.asssistantai.agent.tools.system.RideHailingTool
import github.ponyhuang.asssistantai.agent.tools.system.SearchTool
import github.ponyhuang.asssistantai.agent.tools.system.SettingsNavigationTool
import github.ponyhuang.asssistantai.agent.tools.system.VolumeTool
import github.ponyhuang.asssistantai.agent.tools.system.MediaSessionManagerTool
import github.ponyhuang.asssistantai.agent.tools.system.PackageManagerTool
import github.ponyhuang.asssistantai.agent.tools.system.generatedTools
import github.ponyhuang.asssistantai.agent.tools.system.LocationTool
import github.ponyhuang.asssistantai.agent.tools.system.LocalFileSearchTool
import github.ponyhuang.asssistantai.agent.tools.system.ScreenTimeoutTool
import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelSelection
import github.ponyhuang.asssistantai.data.ModelServiceRepository
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AgentFactory @Inject constructor(
    private val modelServices: ModelServiceRepository,
    private val clockTool: ClockTool,
    private val brightnessTool: BrightnessTool,
    private val calendarTool: CalendarTool,
    private val mediaPlaybackTool: MediaPlaybackTool,
    private val cameraTool: CameraTool,
    private val contactsTool: ContactsTool,
    private val emailTool: EmailTool,
    private val fileStorageTool: FileStorageTool,
    private val mapsTool: MapsTool,
    private val messagingTool: MessagingTool,
    private val notesTool: NotesTool,
    private val phoneTool: PhoneTool,
    private val rideHailingTool: RideHailingTool,
    private val searchTool: SearchTool,
    private val settingsNavigationTool: SettingsNavigationTool,
    private val volumeTool: VolumeTool,
    private val locationTool: LocationTool,
    private val mediaSessionManagerTool: MediaSessionManagerTool,
    private val packageManagerTool: PackageManagerTool,
    private val localFileSearchTool: LocalFileSearchTool,
    private val screenTimeoutTool: ScreenTimeoutTool,
    private val mcpToolRegistry: McpToolRegistry,
) {
    suspend fun create(selection: LLMModelSelection? = null): BaseAgent {
        val cfg = selectModelConfig(selection)
        val model = createModel(cfg)
        val titleModelConfig = selectFastModelConfig() ?: cfg
        val titleModel = if (titleModelConfig == cfg) model else createModel(titleModelConfig)
        val tools: List<BaseTool> = buildList {
            addAll(clockTool.generatedTools())
            addAll(volumeTool.generatedTools())
            addAll(brightnessTool.generatedTools())
            addAll(calendarTool.generatedTools())
            addAll(mediaPlaybackTool.generatedTools())
            addAll(cameraTool.generatedTools())
            addAll(contactsTool.generatedTools())
//            addAll(emailTool.generatedTools())
            addAll(fileStorageTool.generatedTools())
            addAll(locationTool.generatedTools())
            addAll(mediaSessionManagerTool.generatedTools())
            addAll(packageManagerTool.generatedTools())
            addAll(localFileSearchTool.generatedTools())
            addAll(screenTimeoutTool.generatedTools())
            addAll(messagingTool.generatedTools())
//            addAll(notesTool.generatedTools())
            addAll(phoneTool.generatedTools())
//            addAll(rideHailingTool.generatedTools())
            addAll(searchTool.generatedTools())
            addAll(settingsNavigationTool.generatedTools())
            addAll(mcpToolRegistry.tools())
        }
        val titleCallbacks = ConversationTitleCallbacks(titleModel)
        return LlmAgent(
            name = "DefaultAssistant",
            model = model,
            instruction = Instruction(AgentPrompts.DEFAULT_ASSISTANT_INSTRUCTION),
            tools = tools,
            beforeModelCallbacks = listOf(titleCallbacks.beforeModel()),
            afterModelCallbacks = listOf(titleCallbacks.afterModel()),
            afterAgentCallbacks = listOf(titleCallbacks.afterAgent()),
            // ADK invokes this immediately before BaseTool.run(). The resulting synthetic
            // confirmation request is automatically approved by ChatViewModel in the current
            // development/testing mode.
            beforeToolCallbacks = listOf(
                BeforeToolCallback { context, tool, args ->
                    when (context.toolConfirmation?.confirmed) {
                        true -> {
                            Log.i(TAG, "Tool confirmed: ${tool.name}, args=$args")
                            CallbackChoice.Continue(args)
                        }

                        false -> {
                            Log.i(TAG, "Tool rejected: ${tool.name}, args=$args")
                            CallbackChoice.Break(
                                mapOf("status" to "cancelled", "message" to "The user cancelled this tool call."),
                            )
                        }

                        null -> {
                            context.requestConfirmation(
                                hint = "Allow ${tool.name} to run with $args?",
                                payload = args,
                            )
                            context.actions.skipSummarization = true
                            CallbackChoice.Break(mapOf("status" to "awaiting_confirmation"))
                        }
                    }
                },
            ),
        )
    }

    private fun createModel(cfg: ModelConfig): Model = when (cfg.baseType) {
        ApiBaseType.Standard -> Openai(
            name = cfg.modelId,
            client = OpenAIOkHttpClient.builder()
                .baseUrl(cfg.fullBaseUrl)
                .apiKey(cfg.apiKey)
                .build(),
        )

        ApiBaseType.Anthropic -> Claude(
            name = cfg.modelId,
            client = AnthropicOkHttpClient.builder()
                .baseUrl(cfg.fullBaseUrl)
                .apiKey(cfg.apiKey)
                .build(),
        )
    }

    /**
     * 从 [ModelServiceRepository] 选当前模型配置。
     *
     * 优先用用户在聊天 TopAppBar 中央显式选择的模型；
     * 若选择为空 / 指向的服务被禁用 / 组或模型已不存在，自动回退到"第一个启用服务
     * + 第一个非空组的第一个模型"的旧逻辑。
     *
     * 没有可用配置时抛 [IllegalStateException]，由 UI 层提示用户在
     * Settings → Model Service 启用至少一个服务。不再使用任何硬编码兜底配置——
     * 模型密钥与地址必须来自 Store，由 [SeedData] 在 `AsssistantaiApp.onCreate` 注入。
     */
    private fun selectModelConfig(explicitSelection: LLMModelSelection?): ModelConfig {
        // 1. Explicit callers (for example the detached Bluetooth voice runner) do not mutate
        // the chat screen's process-wide current selection.
        val explicit = modelServices.resolveChatSelection(explicitSelection)
        if (explicit != null) {
            val svc = explicit.provider
            return ModelConfig(
                serviceId = svc.serviceId,
                baseType = svc.baseType,
                modelId = explicit.model.modelId,
                apiKey = svc.apiKey,
                fullBaseUrl = composeUrl(svc.activeApiBaseUrl),
            )
        }
        // 2. Prefer the model explicitly selected in the chat screen.
        val resolved = modelServices.resolveChatSelection(
            modelServices.currentSelection.value
        )
        if (resolved != null) {
            val svc = resolved.provider
            return ModelConfig(
                serviceId = svc.serviceId,
                baseType = svc.baseType,
                modelId = resolved.model.modelId,
                apiKey = svc.apiKey,
                fullBaseUrl = composeUrl(svc.activeApiBaseUrl),
            )
        }
        // 3. Fall back to the configured default/first available chat model.
        val fallback = modelServices.defaultSelection()
            ?: error("No enabled model service with a configured model. Enable one in Settings → Model Service.")
        val resolvedFallback = modelServices.resolveChatSelection(fallback)
            ?: error("The default model selection is unavailable.")
        val svc = resolvedFallback.provider
        return ModelConfig(
            serviceId = svc.serviceId,
            baseType = svc.baseType,
            modelId = resolvedFallback.model.modelId,
            apiKey = svc.apiKey,
            fullBaseUrl = composeUrl(svc.activeApiBaseUrl),
        )
    }

    /** Returns the configured low-latency model, or null so callers can use the chat model. */
    private fun selectFastModelConfig(): ModelConfig? {
        val resolved = modelServices.resolveChatSelection(modelServices.fastModelSelection.value)
            ?: return null
        val svc = resolved.provider
        return ModelConfig(
            serviceId = svc.serviceId,
            baseType = svc.baseType,
            modelId = resolved.model.modelId,
            apiKey = svc.apiKey,
            fullBaseUrl = composeUrl(svc.activeApiBaseUrl),
        )
    }

    private fun composeUrl(apiBaseUrl: String): String = apiBaseUrl.trimEnd('/')

    /**
     * 解析后的模型配置（[create] 用）。
     */
    private data class ModelConfig(
        val serviceId: String,
        val baseType: ApiBaseType,
        val modelId: String,
        val apiKey: String,
        val fullBaseUrl: String,
    )

    private companion object {
        const val TAG = "AgentFactory"
    }

}
