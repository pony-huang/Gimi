package github.ponyhuang.asssistantai.agent

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
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalToolCatalogTest {
    @Test
    fun catalogContainsExactlyRegisteredLocalToolsWithUniqueAdkIds() {
        val definitions = catalog().definitions()

        assertEquals(52, definitions.size)
        assertEquals(52, definitions.map { it.id }.distinct().size)
        assertEquals(definitions.map { it.id }, definitions.map { it.name })
        assertFalse(definitions.any { it.id in setOf("compose_email", "create_note", "request_ride") })
    }

    @Test
    fun catalogExcludesModelProviderOfficialTools() {
        val toolIds = catalog().definitions().mapTo(mutableSetOf()) { it.id }

        assertFalse("web_search must be contributed per model service", "web_search" in toolIds)
    }

    private fun catalog() = LocalToolCatalog(
        clockTool = mockk<ClockTool>(relaxed = true),
        volumeTool = mockk<VolumeTool>(relaxed = true),
        brightnessTool = mockk<BrightnessTool>(relaxed = true),
        calendarTool = mockk<CalendarTool>(relaxed = true),
        mediaPlaybackTool = mockk<MediaPlaybackTool>(relaxed = true),
        cameraTool = mockk<CameraTool>(relaxed = true),
        contactsTool = mockk<ContactsTool>(relaxed = true),
        fileStorageTool = mockk<FileStorageTool>(relaxed = true),
        locationTool = mockk<LocationTool>(relaxed = true),
        mediaSessionManagerTool = mockk<MediaSessionManagerTool>(relaxed = true),
        packageManagerTool = mockk<PackageManagerTool>(relaxed = true),
        localFileSearchTool = mockk<LocalFileSearchTool>(relaxed = true),
        screenTimeoutTool = mockk<ScreenTimeoutTool>(relaxed = true),
        messagingTool = mockk<MessagingTool>(relaxed = true),
        phoneTool = mockk<PhoneTool>(relaxed = true),
        settingsNavigationTool = mockk<SettingsNavigationTool>(relaxed = true),
    )
}
