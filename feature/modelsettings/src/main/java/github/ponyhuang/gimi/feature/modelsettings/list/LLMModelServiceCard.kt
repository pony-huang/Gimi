package github.ponyhuang.gimi.feature.modelsettings.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon

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
                .heightIn(min = 60.dp)
                .clickable { onClick(item.id) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LLMModelServiceIcon(
                serviceId = item.id,
                modifier = Modifier.size(40.dp),
                contentPadding = 8.dp,
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = item.isEnabled,
                // 没有 apiKey 时不允许启用；详情页 Key 区填好后会自动重新可点。
                enabled = mutationEnabled && item.apiKey.isNotBlank(),
                onCheckedChange = { onToggleEnabled(item.id, it) },
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}
