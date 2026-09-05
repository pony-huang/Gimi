package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.PreloadMemoryTool
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆贡献方：挂载 ADK 内置 [PreloadMemoryTool]，每轮模型调用前自动从当前
 * MemoryService 召回相关记忆。记忆服务本身附着在 Runner 上，无构建期 revision，
 * 工具也不向模型声明函数（declaration 为 null）。
 */
@Singleton
class MemoryToolContribution @Inject constructor() : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun tools(spec: AgentBuildSpec): List<BaseTool> = listOf(PreloadMemoryTool())

    private companion object {
        const val ID: String = "memory"
    }
}
