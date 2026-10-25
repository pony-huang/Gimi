package github.ponyhuang.gimi.feature.modelsettings.list

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.feature.modelsettings.R
import github.ponyhuang.gimi.ui.components.GimiListDefaults
import github.ponyhuang.gimi.ui.components.GimiSwitch
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon

/** 单一导航目标的厂商行；状态展示与服务管理操作分开。 */
@Composable
fun ModelServiceCard(
    item: LLMModelSetting,
    onClick: (String) -> Unit,
    showDivider: Boolean = false,
    providerPicker: Boolean = false,
    modifier: Modifier = Modifier,
    onEnabledChange: (String, Boolean) -> Unit = { _, _ -> },
    isSwitchEnabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = GimiListDefaults.MinHeight).clickable(role = Role.Button) { onClick(item.id) }
                .padding(horizontal = 16.dp, vertical = GimiListDefaults.VerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GimiListDefaults.ContentSpacing),
        ) {
            LLMModelServiceIcon(item.id, Modifier.size(GimiListDefaults.LeadingSize), contentPadding = 3.dp)
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val subtitle = when {
                    providerPicker && item.isAdded -> stringResource(R.string.modelsettings_provider_added)
                    providerPicker -> null
                    else -> stringResource(R.string.modelsettings_service_model_count, item.groups.sumOf { it.models.size })
                }
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
            }
            if (providerPicker) Icon(Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                val switchLabel = stringResource(R.string.modelsettings_service_enabled_label, item.name)
                GimiSwitch(item.isEnabled, onCheckedChange = { onEnabledChange(item.id, it) }, enabled = isSwitchEnabled,
                    modifier = Modifier.semantics { contentDescription = switchLabel })
            }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(start = GimiListDefaults.DividerStart, end = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
}
