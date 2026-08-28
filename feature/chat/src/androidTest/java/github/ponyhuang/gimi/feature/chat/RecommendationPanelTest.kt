package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RecommendationPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun displaysPromptsAsExtendedActionsAndSendsClickedPrompt() {
        val items = (1..5).map { index ->
            AgentRecommendation("id-$index", "推荐任务 $index", RecommendationCategory.REASONING)
        }
        var selected: String? = null
        compose.setContent {
            RecommendationPanel(items, onRecommendationClick = { selected = it })
        }

        items.forEach { item ->
            compose.onNodeWithContentDescription(item.prompt).assertIsDisplayed()
        }
        assertTrue(compose.onAllNodesWithText("推理").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("recommendation-id-3").assertIsDisplayed().performClick()
        assertEquals("推荐任务 3", selected)
    }
}
