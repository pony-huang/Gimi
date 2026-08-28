package github.ponyhuang.gimi.feature.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import github.ponyhuang.gimi.domain.recommendation.model.AgentRecommendation
import github.ponyhuang.gimi.domain.recommendation.model.RecommendationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun longPressRevealsFullPromptWithoutSendingIt() {
        // 卡片正文截断到两行，长按用 tooltip 补全；此时不应该顺带把任务发出去。
        val item = AgentRecommendation(
            id = "id-1",
            prompt = "我想在睡前听点轻松的白噪音助眠，帮我从网上找一个合适的纯音频链接，并把它加入今晚的播放列表",
            category = RecommendationCategory.RESEARCH,
        )
        var selected: String? = null
        compose.setContent {
            RecommendationPanel(listOf(item), onRecommendationClick = { selected = it })
        }

        // 卡片本身把 prompt 渲染成一个（截断的）文本节点。
        assertEquals(1, promptNodeCount(item.prompt))

        compose.onNodeWithTag("recommendation-id-1").performTouchInput { longClick() }
        compose.waitForIdle()

        // 浮层展开后同一段 prompt 出现两次：卡片正文 + tooltip 正文。
        assertEquals(2, promptNodeCount(item.prompt))
        assertNull(selected)
    }

    /**
     * 卡片上显式设置了 `contentDescription`，合并语义树会用它顶掉子节点的 `Text`，
     * 所以按文本计数必须走未合并树。
     */
    private fun promptNodeCount(prompt: String): Int =
        compose.onAllNodesWithText(prompt, useUnmergedTree = true).fetchSemanticsNodes().size
}
