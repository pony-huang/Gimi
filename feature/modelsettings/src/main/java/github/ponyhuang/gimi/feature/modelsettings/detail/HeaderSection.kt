package github.ponyhuang.gimi.feature.modelsettings.detail

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun HeaderSection(
    service: LLMModelSetting,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    mutationEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LLMModelServiceIcon(serviceId = service.id, modifier = Modifier.size(32.dp), contentPadding = 4.dp)
        Text(
            text = service.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        Switch(
            checked = service.isEnabled,
            enabled = mutationEnabled && service.apiKey.isNotBlank(),
            onCheckedChange = onToggleEnabled,
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HeaderSectionPreview() {
    AsssistantaiTheme {
        HeaderSection(
            service = LLMModelSetting(
                id = "openai",
                name = "OpenAI",
                isEnabled = true,
                apiKey = "sk-test",
                apiBaseUrl = "https://api.openai.com/v1",
                apiProtocol = github.ponyhuang.gimi.domain.modelcatalog.model.ApiProtocol.Standard,
                anthropicBaseUrl = "https://api.anthropic.com",
                groups = emptyList(),
                keyHelpUrl = "https://help.openai.com",
            ),
            onToggleEnabled = {},
        )
    }
}
