package github.ponyhuang.gimi.feature.skills

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.feature.skills.R
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun SkillsSettingsScreen(
    state: SkillsSettingsUiState,
    onAction: (SkillsSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item { PreferenceSectionTitle(stringResource(R.string.skills_import_section)) }
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Link,
                        title = stringResource(R.string.skills_import_url),
                        subtitle = stringResource(R.string.skills_import_url_subtitle),
                        showDivider = true,
                        onClick = { onAction(SkillsSettingsAction.OpenUrlDialog) },
                    )
                    PreferenceListItem(
                        icon = Icons.Default.FileOpen,
                        title = stringResource(R.string.skills_import_local),
                        subtitle = stringResource(R.string.skills_import_local_subtitle),
                        onClick = { onAction(SkillsSettingsAction.RequestLocalArchive) },
                    )
                    if (state.isImporting) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            }
            item { PreferenceSectionTitle(stringResource(R.string.skills_installed_section)) }
            if (state.skills.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.skills_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
            } else {
                item {
                    // 技能数量有限，整组渲染进同一张卡片，行间使用内缩分隔线。
                    PreferenceGroupCard {
                        state.skills.forEachIndexed { index, skill ->
                            PreferenceListItem(
                                icon = Icons.Default.Extension,
                                title = skill.name,
                                subtitle = skill.description,
                                showDivider = index < state.skills.lastIndex,
                                trailingContent = {
                                    IconButton(
                                        onClick = { onAction(SkillsSettingsAction.RequestRemove(skill)) },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.skills_remove, skill.name),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isUrlDialogVisible) {
        AlertDialog(
            onDismissRequest = { onAction(SkillsSettingsAction.DismissUrlDialog) },
            title = { Text(stringResource(R.string.skills_url_dialog_title)) },
            text = {
                PreferenceTextField(
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

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkillsSettingsScreenEmptyPreview() {
    AsssistantaiTheme {
        SkillsSettingsScreen(
            state = SkillsSettingsUiState(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SkillsSettingsScreenWithSkillsPreview() {
    AsssistantaiTheme {
        SkillsSettingsScreen(
            state = SkillsSettingsUiState(
                skills = listOf(
                    InstalledSkill(
                        name = "网页摘要",
                        description = "抓取指定网页内容并生成中文要点摘要，支持长文分段。",
                    ),
                    InstalledSkill(
                        name = "代码评审",
                        description = "按团队规范评审 diff，输出问题清单与修改建议。检查架构边界、状态流转、异常处理与测试覆盖，并给出可以直接执行的修改意见。",
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
