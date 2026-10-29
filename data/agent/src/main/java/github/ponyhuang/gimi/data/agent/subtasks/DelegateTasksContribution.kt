package github.ponyhuang.gimi.data.agent.subtasks

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import github.ponyhuang.gimi.data.agent.AgentLLMModelFactory
import github.ponyhuang.gimi.data.agent.ModelConfig
import github.ponyhuang.gimi.domain.modelcatalog.repository.AgentModelConfigurationSource
import javax.inject.Inject
import javax.inject.Singleton

/** 委派能力常驻声明；模型配置及快速模型选择变化时重建快照，不在运行中偷偷换模型。 */
@Singleton
class DelegateTasksContribution @Inject constructor(
    private val models: AgentLLMModelFactory,
    private val modelSource: AgentModelConfigurationSource,
    private val executor: SubtaskBatchExecutor,
) : AgentContribution {
    override val id = "subtasks"
    override fun revision(): Any = listOf(modelSource.configurationRevision.value, modelSource.fastSelection.value)
    override fun tools(spec: AgentBuildSpec): List<BaseTool> {
        val primary = models.selectModelConfig(spec.selection)
        val selected = models.selectFastModelConfig() ?: primary
        return listOf(DelegateTasksTool(executor, selected))
    }
}

/** 一次调用即一批独立只读任务；返回结构化结果供主助手统一汇总。 */
internal class DelegateTasksTool(
    private val executor: SubtaskBatchExecutor,
    private val model: ModelConfig,
) : BaseTool(
    name = "delegate_tasks",
    description = "Delegate 1 to 6 independent analysis tasks in parallel (at most 3 running). " +
        "Pass explicit text materials. Workers have only reviewed read tools, no parent chat history, " +
        "new file permissions or device actions. Use ordinary tools for simple requests. " +
        "Summarize successful results and disclose failures; handle needs_parent_read through your normal file confirmation flow.",
) {
    override fun declaration() = FunctionDeclaration(name, description, parameters = Schema(
        type = Type.OBJECT,
        properties = mapOf("tasks" to Schema(type = Type.ARRAY, items = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "title" to Schema(type = Type.STRING, description = "Short task title in the user's language"),
                "instruction" to Schema(type = Type.STRING, description = "Self-contained analysis instruction"),
                "materials" to Schema(type = Type.STRING, description = "Explicit text evidence only; parent must interpret non-text attachments first"),
            ),
            required = listOf("title", "instruction"),
        ))),
        required = listOf("tasks"),
    ))

    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any {
        val requests = SubtaskPolicy.parse(args)
        val invocation = context.invocationContext
        val batch = executor.execute(
            sessionId = requireNotNull(invocation.session.key.id),
            userId = invocation.session.key.userId,
            invocationId = invocation.invocationId,
            callId = requireNotNull(context.functionCallId),
            requests = requests,
            model = model,
            parentMetadata = context.runConfig?.customMetadata,
        )
        return mapOf(
            "batch_id" to batch.id,
            "tasks" to batch.tasks.map { task -> mapOf(
                "id" to task.id,
                "title" to task.title,
                "status" to task.status.name,
                "result" to task.result,
                "error" to task.error,
            ) },
        )
    }
}
