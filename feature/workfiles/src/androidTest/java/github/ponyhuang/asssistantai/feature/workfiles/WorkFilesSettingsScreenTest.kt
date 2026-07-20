package github.ponyhuang.asssistantai.feature.workfiles

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkFilesSettingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingWorkFolderRaisesPickerAction() {
        var action: WorkFilesSettingsAction? = null
        composeRule.setContent {
            AsssistantaiTheme {
                WorkFilesSettingsScreen(
                    state = WorkFilesSettingsUiState(),
                    onAction = { action = it },
                )
            }
        }

        composeRule.onNodeWithText("工作文件夹").performClick()

        assertEquals(WorkFilesSettingsAction.RequestAddDirectory, action)
    }
}
