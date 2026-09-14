package github.ponyhuang.gimi.feature.toolauthorization

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAuthorizationScreenStructureTest {
    private val screenBody = File(
        "src/main/java/github/ponyhuang/gimi/feature/toolauthorization/ToolAuthorizationScreen.kt",
    ).readText()
        .substringAfter("fun ToolAuthorizationScreen(")
        .substringBefore("internal fun ToolAuthorizationEffects")

    @Test
    fun `base tool settings share one group card`() {
        assertEquals(
            1,
            Regex("PreferenceGroupCard \\{").findAll(screenBody).count(),
        )
    }

    @Test
    fun `tool loading row separates the next row inside its group`() {
        val toolLoadingRow = screenBody
            .substringAfter("title = stringResource(R.string.toolauth_tool_access_label)")
            .substringBefore("trailingContent")

        assertTrue(toolLoadingRow.contains("showDivider = true"))
    }
}
