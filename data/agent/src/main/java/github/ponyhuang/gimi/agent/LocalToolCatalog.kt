package github.ponyhuang.gimi.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import github.ponyhuang.gimi.agent.tools.system.AudioTool
import github.ponyhuang.gimi.agent.tools.system.CalendarTool
import github.ponyhuang.gimi.agent.tools.system.ClockTool
import github.ponyhuang.gimi.agent.tools.system.CommunicationTool
import github.ponyhuang.gimi.agent.tools.system.DeviceTool
import github.ponyhuang.gimi.agent.tools.system.FilesTool
import github.ponyhuang.gimi.agent.tools.system.LaunchersTool
import github.ponyhuang.gimi.agent.tools.system.LocationTool
import github.ponyhuang.gimi.agent.tools.system.MediaTool
import github.ponyhuang.gimi.agent.tools.system.PeopleTool
import github.ponyhuang.gimi.agent.tools.system.SettingsTool
import github.ponyhuang.gimi.agent.tools.system.WebTool
import github.ponyhuang.gimi.agent.tools.system.generatedTools
import github.ponyhuang.gimi.domain.toolauthorization.model.LocalToolCategory
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.gimi.domain.toolauthorization.repository.LocalToolDefinitionSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地系统工具的统一目录：以扁平列表聚合全部 ADK 工具，并保证工具 ID 全局唯一。
 *
 * [LocalToolCategory] 仅作为设置页所需的展示元数据保存在每个注册项上，不再参与
 * Agent 的工具发现和检索。向量检索始终面对完整扁平目录。
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

    private val registeredTools: List<RegisteredLocalTool> = buildList {
        addAll(audioTool.generatedTools().registeredAs(LocalToolCategory.AUDIO))
        addAll(calendarTool.generatedTools().registeredAs(LocalToolCategory.CALENDAR))
        addAll(clockTool.generatedTools().registeredAs(LocalToolCategory.CLOCK))
        addAll(communicationTool.generatedTools().registeredAs(LocalToolCategory.COMMUNICATION))
        addAll(deviceTool.generatedTools().registeredAs(LocalToolCategory.DEVICE))
        addAll(filesTool.generatedTools().registeredAs(LocalToolCategory.FILES))
        addAll(launchersTool.generatedTools().registeredAs(LocalToolCategory.LAUNCHERS))
        addAll(locationTool.generatedTools().registeredAs(LocalToolCategory.LOCATION))
        addAll(mediaTool.generatedTools().registeredAs(LocalToolCategory.MEDIA))
        addAll(peopleTool.generatedTools().registeredAs(LocalToolCategory.PEOPLE))
        addAll(settingsTool.generatedTools().registeredAs(LocalToolCategory.SETTINGS))
        addAll(webTool.generatedTools().registeredAs(LocalToolCategory.WEB))
    }.also { registrations ->
        // 向量命中后仍按工具 ID 解析执行实例，因此目录级 ID 必须唯一。
        val duplicateIds = registrations.map(RegisteredLocalTool::tool)
            .groupingBy(BaseTool::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate local tool ids: ${duplicateIds.joinToString()}"
        }
    }

    /** 所有本地工具的扁平视图，顺序与注册顺序一致。 */
    fun tools(): List<BaseTool> = registeredTools.map(RegisteredLocalTool::tool)

    /**
     * 需要用户确认的工具 ID 集合。
     *
     * 在目录构建时一次性解析（进程内只跑一次），供按请求过滤使用；
     * 避免在 Agent 创建路径上重复反射。
     */
    val confirmationRequiredToolIds: Set<String> by lazy {
        tools().mapNotNull { tool ->
            tool.takeIf(::requiresConfirmation)?.name
        }.toSet()
    }

    override fun definitions(): List<ToolDefinition> = registeredTools.map { registration ->
        val tool = registration.tool
        ToolDefinition(
            id = tool.name,
            name = tool.name,
            description = tool.description,
            category = registration.category,
        )
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

    private fun List<BaseTool>.registeredAs(
        category: LocalToolCategory,
    ): List<RegisteredLocalTool> = map { tool ->
        RegisteredLocalTool(category = category, tool = tool)
    }

    /**
     * 扁平目录中的单个本地工具注册项。
     *
     * @property category 仅供权限设置界面分组展示的业务元数据。
     * @property tool Agent 实际声明和执行的 ADK 工具实例。
     */
    private data class RegisteredLocalTool(
        val category: LocalToolCategory,
        val tool: BaseTool,
    )
}
