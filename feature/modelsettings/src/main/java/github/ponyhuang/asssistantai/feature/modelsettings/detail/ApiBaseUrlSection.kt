package github.ponyhuang.asssistantai.feature.modelsettings.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ApiProtocol
import github.ponyhuang.asssistantai.domain.modelcatalog.model.ModelService
import github.ponyhuang.asssistantai.feature.modelsettings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiBaseUrlSection(
    service: ModelService,
    isMenuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onProtocolChange: (ApiProtocol) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ApiProtocol.Standard to stringResource(R.string.modelsettings_api_protocol_standard),
        ApiProtocol.Anthropic to stringResource(R.string.modelsettings_api_protocol_anthropic),
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = isMenuExpanded,
            onExpandedChange = { onToggleMenu() },
        ) {
            OutlinedTextField(
                value = options.first { it.first == service.apiProtocol }.second,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.modelsettings_api_url_label)) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMenuExpanded)
                },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = onDismissMenu,
            ) {
                options.forEach { (protocol, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onProtocolChange(protocol) },
                    )
                }
            }
        }

        OutlinedTextField(
            value = service.activeApiBaseUrl,
            onValueChange = onBaseUrlChange,
            singleLine = true,
            label = { Text(stringResource(R.string.modelsettings_api_url_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
