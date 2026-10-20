package github.ponyhuang.gimi.feature.chat.a2ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Preview(name = "数据表格 · 浅色", widthDp = 390, heightDp = 820, showBackground = true)
@Composable
fun DataTableLightPreview() = DataTablePreviewCanvas(dark = false)

@Preview(name = "数据表格 · 深色", widthDp = 390, heightDp = 820, showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DataTableDarkPreview() = DataTablePreviewCanvas(dark = true)

@Preview(name = "数据表格 · 窄屏大字", widthDp = 280, heightDp = 1000, fontScale = 1.3f)
@Composable
private fun DataTableNarrowPreview() = DataTablePreviewCanvas(dark = false)

@Composable
private fun DataTablePreviewCanvas(dark: Boolean) {
    AsssistantaiTheme(darkTheme = dark) {
        ChatA2uiTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier.safeDrawingPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DataTableCard(DataTableData(
                        heading = "模型用量",
                        columns = listOf(DataTableColumn("模型"), DataTableColumn("请求数", true), DataTableColumn("费用", true)),
                        rows = listOf(
                            listOf("DeepSeek V3.2", "128", "¥ 2.56"),
                            listOf("GPT-5", "86", "¥ 12.90"),
                            listOf("Gemini 2.5 Pro", "64", "¥ 6.40"),
                            listOf("Claude Sonnet", "42", "¥ 8.40"),
                            listOf("Qwen3", "36", "¥ 0.72"),
                            listOf("Kimi K2", "28", "¥ 0.56"),
                            listOf("其他", "12", "¥ 0.24"),
                        ),
                    ))
                    DataTableCard(DataTableData(
                        heading = "任务进度",
                        columns = listOf("任务", "负责人", "状态", "完成率", "截止日期").map { DataTableColumn(it) },
                        rows = listOf(
                            listOf("数据整理与校验", "小林", "进行中", "75%", "10 月 22 日"),
                            listOf("界面设计", "小陈", "已完成", "100%", "10 月 23 日"),
                        ),
                    ))
                    DataTableCard(DataTableData(
                        heading = "查询结果",
                        columns = listOf(DataTableColumn("名称"), DataTableColumn("数量", true), DataTableColumn("金额", true)),
                        rows = emptyList(),
                    ))
                }
            }
        }
    }
}
