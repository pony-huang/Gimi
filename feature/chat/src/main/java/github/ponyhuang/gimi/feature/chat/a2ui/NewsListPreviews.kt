package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Preview(name = "新闻资讯 · 浅色", showBackground = true, widthDp = 390, heightDp = 520)
@Composable
private fun NewsListLightPreview() {
    NewsPreviewCanvas(darkTheme = false) {
        NewsListCard(data = newsListPreviewData(), onOpenArticle = {})
    }
}

@Preview(
    name = "新闻资讯 · 深色",
    showBackground = true,
    widthDp = 390,
    heightDp = 520,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NewsListDarkPreview() {
    NewsPreviewCanvas(darkTheme = true) {
        NewsListCard(data = newsListPreviewData(), onOpenArticle = {})
    }
}

@Preview(
    name = "新闻资讯 · 窄屏滚动",
    showBackground = true,
    widthDp = 280,
    heightDp = 560,
    fontScale = 1.3f,
)
@Composable
private fun NewsListNarrowPreview() {
    NewsPreviewCanvas(darkTheme = false) {
        NewsListCard(
            data = newsListPreviewData().copy(
                icon = NewsListIcon.GLOBE,
                items = newsListPreviewData().items.map { item ->
                    item.copy(title = "${item.title} · 更多相关报道与背景信息")
                },
            ),
            onOpenArticle = {},
        )
    }
}

@Composable
private fun NewsPreviewCanvas(darkTheme: Boolean, content: @Composable () -> Unit) {
    AsssistantaiTheme(darkTheme = darkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

private fun newsListPreviewData(): NewsListData = NewsListData(
    heading = "今日资讯",
    updatedAt = "12:11",
    items = listOf(
        NewsArticle(
            title = "全球科技公司加快端侧 AI 布局，新一轮设备升级周期开始",
            source = "路透社",
            timeLabel = "12:08",
            category = "科技",
            imageUrl = null,
            url = "https://example.com/news/1",
        ),
        NewsArticle(
            title = "城市轨道交通新线路今日开通，首日客流预计超过二十万人次",
            source = "新华社",
            timeLabel = "11:42",
            category = "城市",
            imageUrl = null,
            url = "https://example.com/news/2",
        ),
        NewsArticle(
            title = "国际油价小幅回落，市场继续关注供应端变化",
            source = "彭博社",
            timeLabel = "11:20",
            category = "财经",
            imageUrl = null,
            url = "https://example.com/news/3",
        ),
        NewsArticle(
            title = "秋季电影市场回暖，多部现实题材作品口碑走高",
            source = "界面新闻",
            timeLabel = "10:56",
            category = "文化",
            imageUrl = null,
            url = "https://example.com/news/4",
        ),
        NewsArticle(
            title = "新一轮冷空气影响北方地区，多地发布大风降温提示",
            source = "中国气象局",
            timeLabel = "10:31",
            category = "生活",
            imageUrl = null,
            url = "https://example.com/news/5",
        ),
        NewsArticle(
            title = "制造业采购经理指数回升，市场预期改善",
            source = "第一财经",
            timeLabel = "10:12",
            category = "财经",
            imageUrl = null,
            url = "https://example.com/news/6",
        ),
        NewsArticle(
            title = "多所高校公布研究生招生计划，新增交叉学科方向",
            source = "中国教育报",
            timeLabel = "09:48",
            category = "教育",
            imageUrl = null,
            url = "https://example.com/news/7",
        ),
        NewsArticle(
            title = "国际体育赛事进入关键阶段，多个项目迎来焦点对决",
            source = "央视体育",
            timeLabel = "09:30",
            category = "体育",
            imageUrl = null,
            url = "https://example.com/news/8",
        ),
    ),
)
