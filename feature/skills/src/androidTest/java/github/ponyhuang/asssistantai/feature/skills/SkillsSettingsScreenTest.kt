package github.ponyhuang.asssistantai.feature.skills

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import github.ponyhuang.asssistantai.domain.skills.model.InstalledSkill
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SkillsSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun importActionsAreExposed() {
        var action: SkillsSettingsAction? = null
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            SkillsSettingsScreen(
                state = SkillsSettingsUiState(),
                onAction = { action = it },
            )
        }

        composeRule.onNodeWithText(context.getString(R.string.skills_import_url)).performClick()
        assertEquals(SkillsSettingsAction.OpenUrlDialog, action)
        composeRule.onNodeWithText(context.getString(R.string.skills_import_local)).performClick()
        assertEquals(SkillsSettingsAction.RequestLocalArchive, action)
    }

    @Test
    fun installedSkillAndDeleteConfirmationAreShown() {
        val skill = InstalledSkill("demo", "Demo description")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            SkillsSettingsScreen(
                state = SkillsSettingsUiState(
                    skills = listOf(skill),
                    pendingRemoval = skill,
                ),
                onAction = {},
            )
        }

        composeRule.onNodeWithText("demo").assertIsDisplayed()
        composeRule.onNodeWithText("Demo description").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.skills_delete_title)).assertIsDisplayed()
    }
}
