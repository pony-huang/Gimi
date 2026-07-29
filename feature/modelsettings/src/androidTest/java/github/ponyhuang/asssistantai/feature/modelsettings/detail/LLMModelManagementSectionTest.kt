package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.ui.theme.AsssistantaiTheme
import org.junit.Rule
import org.junit.Test

class LLMModelManagementSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modelGroupHeaderUsesProminentFullHeightClickTarget() {
        composeRule.setContent {
            AsssistantaiTheme {
                LLMModelManagementSection(
                    service = service(),
                    rows = listOf(
                        LLMModelSettingDetailRow.GroupHeader(
                            groupId = "minimax",
                            groupName = "MiniMax",
                            isExpanded = true,
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

        composeRule
            .onNodeWithText("MiniMax")
            .assertHasClickAction()
            .assertHeightIsAtLeast(56.dp)
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
