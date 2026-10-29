package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 旅行方案夹具：验证场景化动作、链接文案与长正文省略。 */
private fun travelOptionDeckFixture(): OptionDeckData = OptionDeckData(
    title = "杭州周末路线提案",
    acceptLabel = "想去",
    rejectLabel = "换个地方",
    items = listOf(
        OptionDeckItem(
            id = "west-lake",
            title = "西湖与九溪的两日慢游路线",
            meta = "2 天 1 夜 · 预算 ¥1,680",
            description = "第一天沿北山街散步，傍晚坐船看落日；第二天去龙井村喝茶，再走九溪烟树。路线避开最拥挤的时段，也预留了雨天替代方案和临时改线余量，适合不想赶景点、希望把时间留给散步和拍照的人。",
            tags = listOf("慢节奏", "西湖周边", "可改线"),
            emoji = "🧭",
            imageUrl = null,
            link = OptionDeckLink(
                label = "查看完整路线",
                url = "https://example.com/routes/west-lake",
            ),
        ),
        OptionDeckItem(
            id = "canal",
            title = "运河与老街的轻松周末",
            meta = "1 天 · 预算 ¥680",
            description = "上午逛运河博物馆，下午沿桥西历史街区慢走，晚饭安排本地小馆。",
            tags = listOf("步行少", "本地生活"),
            emoji = "🚶",
            imageUrl = null,
            link = OptionDeckLink(
                label = "打开行程单",
                url = "https://example.com/routes/canal",
            ),
        ),
    ),
)

/** 设计方案夹具：同一组件通过数据切换为赞同/不赞同和设计稿链接。 */
private fun designOptionDeckFixture(): OptionDeckData = OptionDeckData(
    title = "发布会视觉方向",
    acceptLabel = "赞同",
    rejectLabel = "不赞同",
    items = listOf(
        OptionDeckItem(
            id = "calm-blue",
            title = "克制的深蓝发布会视觉",
            meta = "方向 A · 2 个版本",
            description = "深蓝渐变作为主背景，用高亮青色强调关键数字和行动按钮，现场物料保持大量留白。适合强调技术感和可信度，也便于延展到邀请函、展板和直播画面。",
            tags = listOf("视觉设计", "发布会", "科技感"),
            emoji = "🎨",
            imageUrl = null,
            link = OptionDeckLink(
                label = "查看设计稿",
                url = "https://example.com/design/calm-blue",
            ),
        ),
        OptionDeckItem(
            id = "warm-paper",
            title = "纸张质感的暖色发布会视觉",
            meta = "方向 B · 2 个版本",
            description = "用米白纸张、深棕文字和少量手绘标记突出内容和人本身的温度。",
            tags = listOf("视觉设计", "亲和"),
            emoji = "📐",
            imageUrl = null,
            link = OptionDeckLink(
                label = "打开设计说明",
                url = "https://example.com/design/warm-paper",
            ),
        ),
    ),
)

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OptionDeckTravelPreview() {
    AsssistantaiTheme {
        ChatA2uiTheme {
            OptionDeck(
                data = travelOptionDeckFixture(),
                onDecision = { _, _, _ -> },
                onExhausted = { _, _ -> },
                onOpenUrl = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Preview(showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OptionDeckDesignPreview() {
    AsssistantaiTheme {
        ChatA2uiTheme {
            OptionDeck(
                data = designOptionDeckFixture(),
                onDecision = { _, _, _ -> },
                onExhausted = { _, _ -> },
                onOpenUrl = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 280, fontScale = 1.3f)
@Composable
private fun OptionDeckLargeFontPreview() {
    AsssistantaiTheme {
        ChatA2uiTheme {
            OptionDeck(
                data = travelOptionDeckFixture(),
                onDecision = { _, _, _ -> },
                onExhausted = { _, _ -> },
                onOpenUrl = {},
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                initiallyAccepted = setOf("west-lake"),
                initiallyRejected = setOf("canal"),
                initiallyExhausted = true,
            )
        }
    }
}
