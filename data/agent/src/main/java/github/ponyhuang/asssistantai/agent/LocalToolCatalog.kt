package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import github.ponyhuang.asssistantai.agent.tools.system.AudioTool
import github.ponyhuang.asssistantai.agent.tools.system.CalendarTool
import github.ponyhuang.asssistantai.agent.tools.system.ClockTool
import github.ponyhuang.asssistantai.agent.tools.system.CommunicationTool
import github.ponyhuang.asssistantai.agent.tools.system.DeviceTool
import github.ponyhuang.asssistantai.agent.tools.system.FilesTool
import github.ponyhuang.asssistantai.agent.tools.system.LaunchersTool
import github.ponyhuang.asssistantai.agent.tools.system.LocationTool
import github.ponyhuang.asssistantai.agent.tools.system.MediaTool
import github.ponyhuang.asssistantai.agent.tools.system.PeopleTool
import github.ponyhuang.asssistantai.agent.tools.system.SettingsTool
import github.ponyhuang.asssistantai.agent.tools.system.WebTool
import github.ponyhuang.asssistantai.agent.tools.system.generatedTools
import github.ponyhuang.asssistantai.domain.toolauthorization.model.LocalToolCategory
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.LocalToolDefinitionSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地系统工具的统一目录：把每个 [LocalToolCategory] 下的 [AudioTool]/[CalendarTool]…
 * 暴露的工具聚合到一处，并保证跨类别的工具 ID 全局唯一。
 *
 * `XxxTool` 类与 [LocalToolCategory] 一一对应；新增类别只需：
 *   1. 新建 `XxxTool.kt` 并暴露若干 `@Tool` 方法；
 *   2. 在本类的 [toolsByCategory] 中新增一项。
 */
@Singleton
class LocalToolCatalog @Inject constructor(
    audioTool: AudioTool,
    calendarTool: CalendarTool,
    clockTool: ClockTool,
    communicationTool: CommunicationTool,
    deviceTool: DeviceTool,
    filesTool: FilesTool,
    launchersTool: LaunchersTool,
    locationTool: LocationTool,
    mediaTool: MediaTool,
    peopleTool: PeopleTool,
    settingsTool: SettingsTool,
    webTool: WebTool,
) : LocalToolDefinitionSource {

    /**
     * 类别 → 该类别暴露的 ADK [BaseTool] 列表。
     *
     * 顺序既影响 [tools] 的拼接顺序，也是 [LocalToolset] 过滤时的迭代顺序；
     * 不会影响功能正确性。
     */
    private val toolsByCategory: Map<LocalToolCategory, List<BaseTool>> = mapOf(
        LocalToolCategory.AUDIO to audioTool.generatedTools(),
        LocalToolCategory.CALENDAR to calendarTool.generatedTools(),
        LocalToolCategory.CLOCK to clockTool.generatedTools(),
        LocalToolCategory.COMMUNICATION to communicationTool.generatedTools(),
        LocalToolCategory.DEVICE to deviceTool.generatedTools(),
        LocalToolCategory.FILES to filesTool.generatedTools(),
        LocalToolCategory.LAUNCHERS to launchersTool.generatedTools(),
        LocalToolCategory.LOCATION to locationTool.generatedTools(),
        LocalToolCategory.MEDIA to mediaTool.generatedTools(),
        LocalToolCategory.PEOPLE to peopleTool.generatedTools(),
        LocalToolCategory.SETTINGS to settingsTool.generatedTools(),
        LocalToolCategory.WEB to webTool.generatedTools(),
    ).also { byCategory ->
        // 全局 ID 唯一性校验：重复 ID 会让 AmbiguousCandidateSource 在 tool_search 时拒绝整组。
        val duplicateIds = byCategory.values.flatten()
            .groupingBy(BaseTool::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate local tool ids: ${duplicateIds.joinToString()}"
        }
    }

    /** 扁平化视图：所有类别下全部工具，按 [toolsByCategory] 声明顺序拼接。 */
    fun tools(): List<BaseTool> = toolsByCategory.values.flatten()

    /** 按类别暴露的工具表，供 [github.ponyhuang.asssistantai.agent.tools.system.LocalToolset] 按类别过滤。 */
    fun toolsByCategory(): Map<LocalToolCategory, List<BaseTool>> = toolsByCategory

    /**
     * 需要用户确认的工具 ID 集合。
     *
     * 在目录构建时一次性解析（进程内只跑一次），供按请求过滤使用；
     * 避免在 Agent 创建路径上重复反射。
     */
    val confirmationRequiredToolIds: Set<String> by lazy {
        toolsByCategory.values.flatten().mapNotNull { tool ->
            tool.takeIf(::requiresConfirmation)?.name
        }.toSet()
    }

    override fun definitions(): List<ToolDefinition> = toolsByCategory.flatMap { (category, tools) ->
        tools.map { tool ->
            ToolDefinition(id = tool.name, name = tool.name, description = tool.description, category = category)
        }
    }

    /**
     * ADK `FunctionTool.requiresConfirmation` 是 protected 成员，无公开 API 可判定
     * 确认门；这里仅反射读取一次。判定失败时按「需要确认」兜底，宁可误排除也不放行。
     */
    private fun requiresConfirmation(tool: BaseTool): Boolean {
        if (tool !is FunctionTool) return false
        return runCatching {
            val getter = FunctionTool::class.java
                .getDeclaredMethod("getRequiresConfirmation")
                .apply { isAccessible = true }

            @Suppress("UNCHECKED_CAST")
            val requiresConfirmation =
                getter.invoke(tool) as (Map<String, Any>) -> Boolean
            requiresConfirmation(emptyMap())
        }.getOrDefault(true)
    }
}
