package github.ponyhuang.asssistantai.agent

import com.google.adk.kt.tools.BaseTool
import github.ponyhuang.asssistantai.agent.tools.WebSearchTool
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
        // Web Search
        add(WebSearchTool())
    }.also { tools ->
        val duplicateIds = tools.groupingBy(BaseTool::name).eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "Duplicate local tool ids: ${duplicateIds.joinToString()}" }
    }

    fun tools(): List<BaseTool> = localTools

    override fun definitions(): List<ToolDefinition> = localTools.map { tool ->
        ToolDefinition(id = tool.name, name = tool.name, description = tool.description)
    }
}
