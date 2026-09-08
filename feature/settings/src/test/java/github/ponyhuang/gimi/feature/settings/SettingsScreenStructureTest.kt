package github.ponyhuang.gimi.feature.settings

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenStructureTest {
    @Test
    fun mcpPrecedesToolAuthorizationWithinToolsGroup() {
        val source = File("src/main/java/github/ponyhuang/gimi/feature/settings/SettingsScreen.kt").readText()
        val toolsGroup = source.indexOf("settings_group_tools")
        val generalGroup = source.indexOf("settings_group_general")
        val mcp = source.indexOf("settings_mcp_title")
        val customTools = source.indexOf("settings_tool_authorization_title")

        assertTrue(mcp in toolsGroup until generalGroup)
        assertTrue(customTools in toolsGroup until generalGroup)
        assertTrue(mcp < customTools)
    }
}
