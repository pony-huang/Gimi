package github.ponyhuang.asssistantai.ui.settings.llmmodel.detail

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.data.ApiBaseType
import github.ponyhuang.asssistantai.data.LLMModelProvider

/**
 * API 地址下拉 + 输入框 + 灰色预览。
 *
 * - 输入框始终显示并编辑当前协议对应的地址。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiBaseUrlSection(
    service: LLMModelProvider,
    onBaseTypeChange: (ApiBaseType) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember {
        listOf(
            ApiBaseType.Standard to "标准 API 地址",
            ApiBaseType.Anthropic to "Anthropic API 地址"
        )
    }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                value = options.firstOrNull { it.first == service.baseType }?.second
                    ?: "标准 API 地址",
                onValueChange = {},
                readOnly = true,
                label = { Text("API 地址") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (type, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onBaseTypeChange(type)
                            expanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = service.activeApiBaseUrl,
            onValueChange = onBaseUrlChange,
            singleLine = true,
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
