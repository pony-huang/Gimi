package github.ponyhuang.gimi.feature.modelsettings.list

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 列表中的单个供应商行。
 *
 * 视觉：One UI 风格行——品牌圆角图标 + 服务名 + 启用开关，应放在 [PreferenceGroupCard] 内，
 * 行间分隔线由 [showDivider] 控制。
 * - 整行可点击 → [onClick]，进入详情页。
 * - 末尾 Switch 直接翻转 [item.isEnabled]，调用 [onToggleEnabled]。Switch 自带 hit-testing
 *   消费，不会冒泡到外层行的 `onClick`，所以切换开关不会顺带跳详情页（与详情页
 *   `HeaderSection` 里的 Switch 行为一致）。
 */
@Composable
fun ModelServiceCard(
    item: LLMModelSetting,
    onClick: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    mutationEnabled: Boolean = true,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable { onClick(item.id) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LLMModelServiceIcon(
                serviceId = item.id,
                modifier = Modifier.size(32.dp),
                contentPadding = 4.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.apiKey.isBlank()) stringResource(R.string.modelsettings_service_unconfigured)
                        else stringResource(R.string.modelsettings_service_configured_count, item.groups.sumOf { it.models.size }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = item.isEnabled,
                // 没有 apiKey 时不允许启用；详情页 Key 区填好后会自动重新可点。
                enabled = mutationEnabled && item.apiKey.isNotBlank(),
                onCheckedChange = { onToggleEnabled(item.id, it) },
            )
            IconButton(onClick = { onClick(item.id) }) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.modelsettings_service_open, item.name), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 62.dp, end = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
        }
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelServiceCardPreview() {
    AsssistantaiTheme {
        ModelServiceCard(
            item = LLMModelSetting(
                id = "openai",
                name = "OpenAI",
                isEnabled = true,
                apiKey = "sk-test",
                apiBaseUrl = "https://api.openai.com/v1",
                apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.OpenaiChatcompletions,
                anthropicBaseUrl = "https://api.anthropic.com",
                groups = emptyList(),
            ),
            onClick = {},
            onToggleEnabled = { _, _ -> },
        )
    }
}
