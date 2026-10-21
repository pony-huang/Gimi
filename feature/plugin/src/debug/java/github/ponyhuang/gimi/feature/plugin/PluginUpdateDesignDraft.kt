package github.ponyhuang.gimi.feature.plugin

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 仅用于设计审阅的 debug 预览草稿，不接入导航或业务状态。
 *
 * 所有文案、版本和回调均为预览夹具；设计确认后删除草稿，
 * 在正式界面使用领域状态和中英文资源实现。
 */
private enum class UpdateDraftState { Available, Resolving, Downloading, Failed }

@Composable
private fun PluginUpdateDesignDraft(state: UpdateDraftState, detail: Boolean = false) {
    AsssistantaiTheme {
        PreferenceScaffold(
            style = PreferenceStyle.Compact,
            title = if (detail) "知乎" else "插件",
            onBack = {},
        ) { scaffoldModifier ->
            PreferencePageContainer(modifier = scaffoldModifier, style = PreferenceStyle.Compact) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (detail) {
                        item {
                            DraftCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PluginIcon(iconKey = "zhihu", size = 72.dp)
                                    Spacer(Modifier.width(16.dp))
                                    Text("知乎", style = MaterialTheme.typography.headlineSmall)
                                }
                                Text(
                                    "站内搜索、热榜、直答与知识库",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item {
                            DraftCard {
                                Text("版本", style = MaterialTheme.typography.titleSmall)
                                DraftVersionRow("当前版本", "1.0.0")
                                DraftVersionRow("最新版本", "1.1.0")
                                DraftUpdateAction(state)
                                OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                                    Text("管理插件")
                                }
                            }
                        }
                        item {
                            DraftCard {
                                Text("插件介绍", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "接入知乎开放平台，支持站内与全网搜索、热榜、直答，以及知识库检索。",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        item {
                            DraftCard {
                                Text("能力要点", style = MaterialTheme.typography.titleSmall)
                                Text("•  站内与全网搜索\n•  热榜与直答\n•  知识库检索与文档任务")
                            }
                        }
                    } else {
                        item {
                            PreferenceTextField(
                                value = "",
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("搜索插件") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                singleLine = true,
                            )
                        }
                        item { DraftPluginRow("zhihu", "知乎", "站内搜索、热榜、直答与知识库", state) }
                        item { DraftPluginRow("spotify", "Spotify", "搜索、播放、歌单和音乐库", null) }
                        item {
                            DraftCard {
                                DraftPluginHeading("v2ex", "V2EX", "节点、主题、回复、通知与账号信息")
                                FilledTonalButton(
                                    onClick = {},
                                    enabled = state != UpdateDraftState.Resolving && state != UpdateDraftState.Downloading,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("安装") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = preferenceGroupCardColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun DraftPluginRow(key: String, title: String, summary: String, state: UpdateDraftState?) {
    DraftCard {
        DraftPluginHeading(key, title, summary)
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (state == null) "已安装" else "有更新",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Switch(checked = true, onCheckedChange = {})
        }
        if (state != null) DraftUpdateAction(state)
    }
}

@Composable
private fun DraftPluginHeading(key: String, title: String, summary: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PluginIcon(iconKey = key, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = {}) {
            Icon(Icons.Default.MoreVert, contentDescription = "更多")
        }
    }
}

@Composable
private fun DraftVersionRow(label: String, version: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(version, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DraftUpdateAction(state: UpdateDraftState) {
    when (state) {
        UpdateDraftState.Available -> FilledTonalButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        ) { Text("更新") }

        UpdateDraftState.Resolving -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("正在准备更新…", style = MaterialTheme.typography.labelSmall)
        }

        UpdateDraftState.Downloading -> {
            LinearProgressIndicator(
                progress = { 0.42f },
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "正在下载更新 42%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        UpdateDraftState.Failed -> {
            Text(
                "更新下载失败，请检查网络后重试",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = {}) { Text("重试更新") }
        }
    }
}

@Preview(name = "有更新 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "有更新 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateAvailableDraftPreview() {
    PluginUpdateDesignDraft(state = UpdateDraftState.Available, detail = false)
}

@Preview(name = "准备更新 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "准备更新 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateResolvingDraftPreview() {
    PluginUpdateDesignDraft(state = UpdateDraftState.Resolving, detail = false)
}

@Preview(name = "下载中 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "下载中 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateDownloadingDraftPreview() {
    PluginUpdateDesignDraft(state = UpdateDraftState.Downloading, detail = false)
}

@Preview(name = "更新失败 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "更新失败 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateFailedDraftPreview() {
    PluginUpdateDesignDraft(state = UpdateDraftState.Failed, detail = false)
}

@Preview(name = "详情更新 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "详情更新 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateDetailDraftPreview() {
    PluginUpdateDesignDraft(state = UpdateDraftState.Available, detail = true)
}

@Preview(name = "详情失败 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "详情失败 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateDetailFailedDraftPreview() {
    PluginUpdateDesignDraft(state = UpdateDraftState.Failed, detail = true)
}
