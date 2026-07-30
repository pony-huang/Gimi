package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.gimi.domain.modelcatalog.model.CatalogLoadState
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LLMModelSettingListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingServiceRaisesNavigationEvent() {
        var selectedId: String? = null
        composeRule.setContent {
            AsssistantaiTheme {
                LLMModelServiceListScreen(
                    state = ModelServiceListUiState(
                        loadState = CatalogLoadState.Ready,
                        items = listOf(service()),
                    ),
                    onAction = {},
                    onNavigateToDetail = { selectedId = it },
                )
            }
        }

        composeRule.onNodeWithText("深度求索").performClick()

        assertEquals("deepseek", selectedId)
    }

    private fun service() = LLMModelSetting(
        id = "deepseek",
        name = "深度求索",
        isEnabled = true,
        apiKey = "key",
        apiBaseUrl = "https://api.deepseek.com",
        apiProtocol = ApiProtocol.Standard,
        anthropicBaseUrl = "https://api.deepseek.com/anthropic",
        groups = emptyList(),
    )
}
