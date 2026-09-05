package github.ponyhuang.gimi.feature.workfiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.feature.workfiles.R
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun WorkFilesSettingsScreen(
    state: WorkFilesSettingsUiState,
    onAction: (WorkFilesSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                PreferenceGroupCard {
                    PreferenceListItem(
                        icon = Icons.Default.Folder,
                        title = stringResource(R.string.workfiles_title),
                        subtitle = if (state.directories.isEmpty()) {
                            stringResource(R.string.workfiles_subtitle)
                        } else {
                            stringResource(R.string.workfiles_auth_count, state.directories.size)
                        },
                        onClick = {
                            onAction(WorkFilesSettingsAction.RequestAddDirectory)
                        },
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    onAction(WorkFilesSettingsAction.RequestAddDirectory)
                                },
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.workfiles_add_action),
                                )
                            }
                        },
                    )
                }
            }
            state.operationError?.let { error ->
                item {
                    PreferenceBanner(
                        text = stringResource(
                            when (error) {
                                WorkDirectoryOperationResult.Failure.InvalidDirectory ->
                                    R.string.workfiles_error_invalid
                                WorkDirectoryOperationResult.Failure.PermissionDenied ->
                                    R.string.workfiles_error_permission
                                WorkDirectoryOperationResult.Failure.PersistenceFailed ->
                                    R.string.workfiles_error_persistence
                            },
                        ),
                        tone = PreferenceBannerTone.Error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            item { PreferenceSectionTitle(text = stringResource(R.string.workfiles_section_authorized)) }
            if (state.directories.isEmpty()) {
                item {
                    // 空态是行动邀请：说明 + 与右上角 + 等价的按钮，而不是一行死文字。
                    PreferenceGroupCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.workfiles_empty_state),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    onAction(WorkFilesSettingsAction.RequestAddDirectory)
                                },
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.workfiles_add_action),
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    // 已授权目录数量有限，整组渲染进同一张卡片。
                    PreferenceGroupCard {
                        state.directories.forEachIndexed { index, directory ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = directory.displayName,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = directory.authority,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                },
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            onAction(WorkFilesSettingsAction.RemoveDirectory(directory.uri))
                                        },
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.workfiles_remove_action),
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (index < state.directories.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkFilesSettingsScreenEmptyPreview() {
    AsssistantaiTheme {
        WorkFilesSettingsScreen(
            state = WorkFilesSettingsUiState(),
            onAction = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WorkFilesSettingsScreenWithDirectoriesPreview() {
    AsssistantaiTheme {
        WorkFilesSettingsScreen(
            state = WorkFilesSettingsUiState(
                directories = listOf(
                    WorkDirectory(
                        uri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments",
                        displayName = "Documents",
                        authority = "com.android.externalstorage.documents",
                    ),
                    WorkDirectory(
                        uri = "content://com.android.externalstorage.documents/tree/primary%3ADownload",
                        displayName = "Download",
                        authority = "com.android.externalstorage.documents",
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
