package github.ponyhuang.gimi.data.agent.contribution

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.GetUserChoiceTool
import com.google.adk.kt.tools.LoadMemoryTool
import com.google.adk.kt.tools.PreloadMemoryTool
import com.google.adk.kt.tools.RequestInputTool
import github.ponyhuang.gimi.data.agent.AgentBuildSpec
import github.ponyhuang.gimi.data.agent.AgentContribution
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基础组件工具
 */
@Singleton
class BaseToolContribution @Inject constructor() : AgentContribution {

    override val id: String = ID

    override fun revision(): Any? = null

    override fun tools(spec: AgentBuildSpec): List<BaseTool> =
        listOf(PreloadMemoryTool(), LoadMemoryTool(), RequestInputTool(), GetUserChoiceTool())

    private companion object {
        const val ID: String = "base"
    }
}
