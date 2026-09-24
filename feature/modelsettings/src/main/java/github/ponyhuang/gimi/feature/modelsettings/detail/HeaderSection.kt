package github.ponyhuang.gimi.feature.modelsettings.detail

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import github.ponyhuang.gimi.domain.modelcatalog.model.LLMModelSetting
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import github.ponyhuang.gimi.ui.settings.llmmodel.LLMModelServiceIcon

@Composable
fun HeaderSection(
    service: LLMModelSetting,
    onToggleEnabled: (Boolean) -> Unit,
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
    }
}

@Preview(showBackground = true)
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
