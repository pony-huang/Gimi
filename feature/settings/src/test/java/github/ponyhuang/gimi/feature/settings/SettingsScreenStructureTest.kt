package github.ponyhuang.gimi.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenStructureTest {
    @Test
    fun toolCallDetailsIsTheFirstItemInTools() {
        val source = File("src/main/java/github/ponyhuang/gimi/feature/settings/SettingsScreen.kt").readText()
        val toolsGroup = source.indexOf("settings_group_tools")
        val generalGroup = source.indexOf("settings_group_general")
        val toolActivity = source.indexOf("settings_chat_display_title")
        val mcp = source.indexOf("settings_mcp_title")
        val customTools = source.indexOf("settings_tool_authorization_title")

        assertTrue(toolActivity in toolsGroup until generalGroup)
        assertTrue(toolActivity < mcp)
        assertTrue(mcp < customTools)
    }
}
