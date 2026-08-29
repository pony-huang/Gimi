package github.ponyhuang.gimi.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun MemorySettingsScreen(
    state: MemorySettingsUiState,
    onAction: (MemorySettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tokenVisible by remember { mutableStateOf(false) }
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceListItem(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.memory_enabled_title),
                    subtitle = stringResource(R.string.memory_enabled_subtitle),
                    trailingContent = {
                        Switch(
                            checked = state.memoryEnabled,
                            onCheckedChange = {
                                onAction(MemorySettingsAction.SetMemoryEnabled(it))
                            },
                        )
                    },
                    onClick = {
                        onAction(MemorySettingsAction.SetMemoryEnabled(!state.memoryEnabled))
                    },
                )
            }
            item { PreferenceSectionTitle(stringResource(R.string.memory_section_provider)) }
            item {
                PreferenceListItem(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.memory_mem0_enabled_title),
                    subtitle = stringResource(R.string.memory_mem0_enabled_subtitle),
                    trailingContent = {
                        Switch(
                            checked = state.mem0Enabled,
                            enabled = state.memoryEnabled,
                            onCheckedChange = {
                                onAction(MemorySettingsAction.SetMem0Enabled(it))
                            },
                        )
                    },
                    onClick = {
                        if (state.memoryEnabled) {
                            onAction(MemorySettingsAction.SetMem0Enabled(!state.mem0Enabled))
                        }
                    },
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = state.token,
                        onValueChange = { onAction(MemorySettingsAction.SetToken(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.memory_token_label)) },
                        singleLine = true,
                        enabled = state.memoryEnabled,
                        isError = state.tokenError,
                        supportingText = if (state.tokenError) {
                            { Text(stringResource(R.string.memory_token_required)) }
                        } else if (state.hasStoredToken) {
                            { Text(stringResource(R.string.memory_token_stored_placeholder)) }
                        } else {
                            null
                        },
                        visualTransformation = if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                Icon(
                                    imageVector = if (tokenVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = stringResource(
                                        if (tokenVisible) {
                                            R.string.memory_hide_token
                                        } else {
                                            R.string.memory_show_token
                                        },
                                    ),
                                )
                            }
                        },
                    )
                    Button(
                        onClick = { onAction(MemorySettingsAction.Save) },
                        enabled = !state.saving && state.memoryEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text(
                            text = stringResource(
                                if (state.saving) R.string.memory_saving else R.string.memory_save,
                            ),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MemorySettingsScreenPreview() {
    AsssistantaiTheme {
        MemorySettingsScreen(
            state = MemorySettingsUiState(hasStoredToken = true),
            onAction = {},
        )
    }
}
