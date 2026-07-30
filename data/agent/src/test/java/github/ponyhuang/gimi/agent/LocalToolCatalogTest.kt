package github.ponyhuang.gimi.agent

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
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalToolCatalogTest {
    @Test
    fun catalogContainsExactlyRegisteredLocalToolsWithUniqueAdkIds() {
        val definitions = catalog().definitions()

        assertTrue(
            "expected a positive number of tools, got ${definitions.size}",
            definitions.size > 0,
        )
        assertEquals(definitions.size, definitions.map { it.id }.distinct().size)
        assertEquals(definitions.map { it.id }, definitions.map { it.name })
        assertFalse(definitions.any { it.id in setOf("compose_email", "create_note", "request_ride") })
    }

    @Test
    fun catalogExcludesModelProviderOfficialTools() {
        val toolIds = catalog().definitions().mapTo(mutableSetOf()) { it.id }

        assertFalse("web_search must be contributed per model service", "web_search" in toolIds)
    }

    @Test
    fun sensitiveReadToolsRequireConfirmation() {
        val toolsByName = catalog().tools().associateBy { it.name }
        val sensitiveNames = listOf(
            "list_calendars",
            "get_upcoming_calendar_events",
            "get_current_location",
            "list_active_media_sessions",
        )

        sensitiveNames.forEach { name ->
            val tool = toolsByName.getValue(name) as FunctionTool
            val getter = FunctionTool::class.java
                .getDeclaredMethod("getRequiresConfirmation")
                .apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val requiresConfirmation =
                getter.invoke(tool) as (Map<String, Any>) -> Boolean
            assertTrue(
                "$name must require confirmation",
                requiresConfirmation(emptyMap()),
            )
        }
    }

    @Test
    fun confirmationRequiredToolIdsAreResolvedOnceAtCatalogLevel() {
        val confirmationIds = catalog().confirmationRequiredToolIds

        assertTrue("list_calendars" in confirmationIds)
        assertTrue("get_current_location" in confirmationIds)
        assertFalse("get_current_time" in confirmationIds)
    }

    @Test
    fun catalogDoesNotExposeCategoryBasedToolLookup() {
        assertFalse(
            LocalToolCatalog::class.java.declaredMethods.any { method ->
                method.name == "toolsByCategory"
            },
        )
    }

    private fun catalog() = LocalToolCatalog(
        audioTool = mockk<AudioTool>(relaxed = true),
        calendarTool = mockk<CalendarTool>(relaxed = true),
        clockTool = mockk<ClockTool>(relaxed = true),
        communicationTool = mockk<CommunicationTool>(relaxed = true),
        deviceTool = mockk<DeviceTool>(relaxed = true),
        filesTool = mockk<FilesTool>(relaxed = true),
        launchersTool = mockk<LaunchersTool>(relaxed = true),
        locationTool = mockk<LocationTool>(relaxed = true),
        mediaTool = mockk<MediaTool>(relaxed = true),
        peopleTool = mockk<PeopleTool>(relaxed = true),
        settingsTool = mockk<SettingsTool>(relaxed = true),
        webTool = mockk<WebTool>(relaxed = true),
    )
}
