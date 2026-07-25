package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.asssistantai.feature.modelsettings.R
import github.ponyhuang.asssistantai.ui.settings.llmmodel.LLMModelServiceIcon

@Composable
fun HeaderSection(
    service: LLMModelSetting,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenHomepage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LLMModelServiceIcon(serviceId = service.id, modifier = Modifier.size(48.dp))
        Text(
            text = service.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Switch(
            checked = service.isEnabled,
            enabled = service.apiKey.isNotBlank(),
            onCheckedChange = onToggleEnabled,
        )
        IconButton(onClick = onOpenHomepage, modifier = Modifier.padding(start = 4.dp)) {
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = stringResource(R.string.modelsettings_open_homepage),
            )
        }
    }
}
