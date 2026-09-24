package github.ponyhuang.gimi.feature.modelsettings.detail

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Rule
import org.junit.Test

class LLMModelManagementSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modelListOmitsGroupNameAndShowsModelsDirectly() {
        composeRule.setContent {
            AsssistantaiTheme {
                LLMModelManagementSection(
                    service = service(),
                    rows = listOf(
                        LLMModelSettingDetailRow.LLMModelItem(
                            groupId = "minimax",
                            model = Model("MiniMax-M2.7", "MiniMax-M2.7"),
                        ),
                    ),
                    isRefreshing = false,
                    isAddDialogVisible = false,
                    newModelId = "",
                    newModelKind = NewModelKind.Chat,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("MiniMax-M2.7").assertExists()
        composeRule.onNodeWithText("MiniMax").assertDoesNotExist()
    }

    private fun service() = LLMModelSetting(
        id = "minimax",
        name = "MiniMax",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://example.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://example.com/anthropic",
        groups = emptyList(),
    )
}
