package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import github.ponyhuang.asssistantai.agent.tools.system.BrightnessTool
import github.ponyhuang.asssistantai.agent.tools.system.CalendarTool
import github.ponyhuang.asssistantai.agent.tools.system.CameraTool
import github.ponyhuang.asssistantai.agent.tools.system.ClockTool
import github.ponyhuang.asssistantai.agent.tools.system.ContactsTool
import github.ponyhuang.asssistantai.agent.tools.system.FileStorageTool
import github.ponyhuang.asssistantai.agent.tools.system.LocalFileSearchTool
import github.ponyhuang.asssistantai.agent.tools.system.LocationTool
import github.ponyhuang.asssistantai.agent.tools.system.MediaPlaybackTool
import github.ponyhuang.asssistantai.agent.tools.system.MediaSessionManagerTool
import github.ponyhuang.asssistantai.agent.tools.system.MessagingTool
import github.ponyhuang.asssistantai.agent.tools.system.PackageManagerTool
import github.ponyhuang.asssistantai.agent.tools.system.PhoneTool
import github.ponyhuang.asssistantai.agent.tools.system.ScreenTimeoutTool
import github.ponyhuang.asssistantai.agent.tools.system.SettingsNavigationTool
import github.ponyhuang.asssistantai.agent.tools.system.VolumeTool
import github.ponyhuang.asssistantai.agent.tools.system.generatedTools
import github.ponyhuang.asssistantai.domain.toolauthorization.model.ToolDefinition
import github.ponyhuang.asssistantai.domain.toolauthorization.repository.LocalToolDefinitionSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalToolCatalog @Inject constructor(
    clockTool: ClockTool,
    volumeTool: VolumeTool,
    brightnessTool: BrightnessTool,
    calendarTool: CalendarTool,
    mediaPlaybackTool: MediaPlaybackTool,
    cameraTool: CameraTool,
    contactsTool: ContactsTool,
    fileStorageTool: FileStorageTool,
    locationTool: LocationTool,
    mediaSessionManagerTool: MediaSessionManagerTool,
    packageManagerTool: PackageManagerTool,
    localFileSearchTool: LocalFileSearchTool,
    screenTimeoutTool: ScreenTimeoutTool,
    messagingTool: MessagingTool,
    phoneTool: PhoneTool,
    settingsNavigationTool: SettingsNavigationTool,
) : LocalToolDefinitionSource {
    private val localTools: List<BaseTool> = buildList {
        addAll(clockTool.generatedTools())
        addAll(volumeTool.generatedTools())
        addAll(brightnessTool.generatedTools())
        addAll(calendarTool.generatedTools())
        addAll(mediaPlaybackTool.generatedTools())
        addAll(cameraTool.generatedTools())
        addAll(contactsTool.generatedTools())
        addAll(fileStorageTool.generatedTools())
        addAll(locationTool.generatedTools())
        addAll(mediaSessionManagerTool.generatedTools())
        addAll(packageManagerTool.generatedTools())
        addAll(localFileSearchTool.generatedTools())
        addAll(screenTimeoutTool.generatedTools())
        addAll(messagingTool.generatedTools())
        addAll(phoneTool.generatedTools())
        addAll(settingsNavigationTool.generatedTools())
    }.also { tools ->
        val duplicateIds = tools.groupingBy(BaseTool::name).eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate local tool ids: ${duplicateIds.joinToString()}" }
    }

    fun tools(): List<BaseTool> = localTools

    /**
     * 需要用户确认的工具 ID 集合。
     *
     * 在目录构建时一次性解析（进程内只跑一次），供按请求过滤使用；
     * 避免在 Agent 创建路径上重复反射。
     */
    val confirmationRequiredToolIds: Set<String> by lazy {
        localTools.mapNotNull { tool ->
            tool.takeIf(::requiresConfirmation)?.name
        }.toSet()
    }

    override fun definitions(): List<ToolDefinition> = localTools.map { tool ->
        ToolDefinition(id = tool.name, name = tool.name, description = tool.description)
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
