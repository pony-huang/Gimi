package github.ponyhuang.gimi.feature.skills

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import github.ponyhuang.gimi.feature.skills.R
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle

@Composable
fun SkillsSettingsScreen(
    state: SkillsSettingsUiState,
    onAction: (SkillsSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.skills_import_section)) }
            item {
                PreferenceListItem(
                    icon = Icons.Default.Link,
                    title = stringResource(R.string.skills_import_url),
                    subtitle = stringResource(R.string.skills_import_url_subtitle),
                    onClick = { onAction(SkillsSettingsAction.OpenUrlDialog) },
                )
            }
            item {
                PreferenceListItem(
                    icon = Icons.Default.FileOpen,
                    title = stringResource(R.string.skills_import_local),
                    subtitle = stringResource(R.string.skills_import_local_subtitle),
                    onClick = { onAction(SkillsSettingsAction.RequestLocalArchive) },
                )
            }
            if (state.isImporting) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.skills_importing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
            item { PreferenceSectionTitle(stringResource(R.string.skills_installed_section)) }
            if (state.skills.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.skills_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(state.skills, key = { it.name }) { skill ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = skill.name,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = skill.description,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                onAction(SkillsSettingsAction.RequestRemove(skill))
                            },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(
                                    R.string.skills_remove,
                                    skill.name,
                                ),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }

    if (state.isUrlDialogVisible) {
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.DismissUrlDialog) },
            title = { Text(stringResource(R.string.skills_url_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = state.urlDraft,
                    onValueChange = { onAction(SkillsSettingsAction.UrlChanged(it)) },
                    label = { Text(stringResource(R.string.skills_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = state.urlDraft.isNotBlank() && !state.isImporting,
                    onClick = { onAction(SkillsSettingsAction.SubmitUrl) },
                ) {
                    Text(stringResource(R.string.skills_install))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.DismissUrlDialog) },
                ) {
                    Text(stringResource(R.string.skills_cancel))
                }
            },
        )
    }

    state.pendingReplacement?.let { pending ->
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.CancelReplacement) },
            title = { Text(stringResource(R.string.skills_replace_title)) },
            text = {
                Text(stringResource(R.string.skills_replace_message, pending.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.ConfirmReplacement) },
                ) {
                    Text(stringResource(R.string.skills_replace_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.CancelReplacement) },
                ) {
                    Text(stringResource(R.string.skills_cancel))
                }
            },
        )
    }

    state.pendingRemoval?.let { pending ->
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.CancelRemoval) },
            title = { Text(stringResource(R.string.skills_delete_title)) },
            text = {
                Text(stringResource(R.string.skills_delete_message, pending.name))
            },
            confirmButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.ConfirmRemoval) },
                ) {
                    Text(stringResource(R.string.skills_delete_action))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onAction(SkillsSettingsAction.CancelRemoval) },
                ) {
                    Text(stringResource(R.string.skills_cancel))
                }
            },
        )
    }
}
