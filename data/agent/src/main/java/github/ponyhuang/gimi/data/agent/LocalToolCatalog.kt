package github.ponyhuang.gimi.data.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import github.ponyhuang.gimi.data.agent.tools.system.AudioTool
import github.ponyhuang.gimi.data.agent.tools.system.CalendarTool
import github.ponyhuang.gimi.data.agent.tools.system.ClockTool
import github.ponyhuang.gimi.data.agent.tools.system.CommunicationTool
import github.ponyhuang.gimi.data.agent.tools.system.DeviceTool
import github.ponyhuang.gimi.data.agent.tools.system.FlashlightTool
import github.ponyhuang.gimi.data.agent.tools.system.FilesTool
import github.ponyhuang.gimi.data.agent.tools.system.LaunchersTool
import github.ponyhuang.gimi.data.agent.tools.system.LocationTool
import github.ponyhuang.gimi.data.agent.tools.system.MediaTool
import github.ponyhuang.gimi.data.agent.tools.system.PeopleTool
import github.ponyhuang.gimi.data.agent.tools.system.ReadLocalFileTool
import github.ponyhuang.gimi.data.agent.tools.system.SettingsTool
import github.ponyhuang.gimi.data.agent.tools.system.generatedTools
import github.ponyhuang.gimi.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.gimi.domain.toolauthorization.repository.LocalToolDefinitionSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地系统工具的统一目录：以扁平列表聚合全部 ADK 工具，并保证工具 ID 全局唯一。
 * 向量检索始终面对完整扁平目录。
 */
@Singleton
class LocalToolCatalog @Inject constructor(
    audioTool: AudioTool,
    calendarTool: CalendarTool,
    clockTool: ClockTool,
    communicationTool: CommunicationTool,
    deviceTool: DeviceTool,
    filesTool: FilesTool,
    flashlightTool: FlashlightTool,
    launchersTool: LaunchersTool,
    locationTool: LocationTool,
    mediaTool: MediaTool,
    peopleTool: PeopleTool,
    readLocalFileTool: ReadLocalFileTool,
    settingsTool: SettingsTool,
) : LocalToolDefinitionSource {

    private val localTools: List<BaseTool> = buildList {
        addAll(audioTool.generatedTools())
        addAll(calendarTool.generatedTools())
        addAll(clockTool.generatedTools())
        addAll(communicationTool.generatedTools())
        addAll(deviceTool.generatedTools())
        addAll(filesTool.generatedTools())
        addAll(flashlightTool.generatedTools())
        // 手写 FunctionTool（@Tool 注解无法生成 processLlmRequest 注入逻辑）。
        add(readLocalFileTool)
        addAll(launchersTool.generatedTools())
        addAll(locationTool.generatedTools())
        addAll(mediaTool.generatedTools())
        addAll(peopleTool.generatedTools())
        addAll(settingsTool.generatedTools())
    }.also { tools ->
        // 向量命中后仍按工具 ID 解析执行实例，因此目录级 ID 必须唯一。
        val duplicateIds = tools
            .groupingBy(BaseTool::name)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateIds.isEmpty()) {
            "Duplicate local tool ids: ${duplicateIds.joinToString()}"
        }
    }

    /** 所有本地工具的扁平视图，顺序与注册顺序一致。 */
    fun tools(): List<BaseTool> = localTools

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

    override fun definitions(): List<ToolDefinition> = localTools.map { tool ->
        ToolDefinition(
            id = tool.name,
            name = tool.name,
            description = tool.description,
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
}
