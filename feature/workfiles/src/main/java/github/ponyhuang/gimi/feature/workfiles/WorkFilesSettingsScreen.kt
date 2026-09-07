package github.ponyhuang.gimi.feature.workfiles

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.workfiles.model.AppStorageSummary
import github.ponyhuang.gimi.domain.workfiles.model.StorageClearSummary
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectory
import github.ponyhuang.gimi.domain.workfiles.model.WorkDirectoryAccessStatus
import github.ponyhuang.gimi.domain.workfiles.repository.WorkDirectoryOperationResult
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceSectionTitle
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme
import java.util.Locale

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PreferenceSectionTitle(text = stringResource(R.string.workfiles_storage_section)) }
            item {
                StorageSummaryCard(
                    summary = state.storageSummary,
                    isLoading = state.isStorageLoading,
                    isClearing = state.isClearingStorage,
                    loadFailed = state.storageLoadFailed,
                    onRefresh = { onAction(WorkFilesSettingsAction.RefreshStorage) },
                    onClear = { onAction(WorkFilesSettingsAction.ClearReclaimableStorage) },
                )
            }
            state.lastClearResult?.let { result ->
                item { StorageClearBanner(result) }
            }
            state.operationError?.let { error ->
                item { DirectoryOperationErrorBanner(error) }
            }
            item { PreferenceSectionTitle(text = stringResource(R.string.workfiles_section_authorized)) }
            if (state.directories.isEmpty()) {
                item {
                    EmptyDirectoriesCard(
                        onAdd = { onAction(WorkFilesSettingsAction.RequestAddDirectory) },
                    )
                }
            } else {
                items(state.directories, key = WorkDirectory::id) { directory ->
                    WorkDirectoryCard(
                        directory = directory,
                        onEnabledChange = { enabled ->
                            onAction(
                                WorkFilesSettingsAction.SetDirectoryEnabled(
                                    id = directory.id,
                                    enabled = enabled,
                                ),
                            )
                        },
                        onReauthorize = {
                            onAction(WorkFilesSettingsAction.RequestReauthorizeDirectory(directory.id))
                        },
                        onRemove = {
                            onAction(WorkFilesSettingsAction.RemoveDirectory(directory.id))
                        },
                    )
                }
                if (state.directories.any { it.accessStatus != WorkDirectoryAccessStatus.AVAILABLE }) {
                    item {
                        PreferenceBanner(
                            text = stringResource(R.string.workfiles_access_warning),
                            tone = PreferenceBannerTone.Error,
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onAction(WorkFilesSettingsAction.RequestAddDirectory) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.workfiles_add_action),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(
    summary: AppStorageSummary?,
    isLoading: Boolean,
    isClearing: Boolean,
    loadFailed: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
) {
    PreferenceGroupCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary?.let {
                            stringResource(
                                R.string.workfiles_storage_total,
                                formatStorageBytes(it.totalBytes),
                            )
                        } ?: stringResource(R.string.workfiles_storage_loading),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (summary != null) {
                        Text(
                            text = stringResource(
                                R.string.workfiles_storage_breakdown,
                                formatStorageBytes(summary.persistentBytes),
                                formatStorageBytes(summary.reclaimableBytes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("storage_loading"),
                    )
                } else if (loadFailed) {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.workfiles_storage_retry),
                        )
                    }
                }
            }
            summary?.let {
                StorageRow(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.workfiles_storage_cache),
                    subtitle = stringResource(
                        R.string.workfiles_storage_cache_value,
                        formatStorageBytes(it.cacheBytes),
                    ),
                )
                StorageRow(
                    icon = Icons.Default.CleaningServices,
                    title = stringResource(R.string.workfiles_storage_temporary),
                    subtitle = stringResource(
                        R.string.workfiles_storage_temporary_value,
                        formatStorageBytes(it.temporaryBytes),
                    ),
                )
                OutlinedButton(
                    onClick = onClear,
                    enabled = it.reclaimableBytes > 0L && !isClearing,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                        .testTag("storage_clear"),
                ) {
                    if (isClearing) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            stringResource(
                                R.string.workfiles_storage_clear,
                                formatStorageBytes(it.reclaimableBytes),
                            ),
                        )
                    }
                }
                if (it.unreadableDirectoryCount > 0) {
                    Text(
                        text = stringResource(
                            R.string.workfiles_storage_unreadable,
                            it.unreadableDirectoryCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkDirectoryCard(
    directory: WorkDirectory,
    onEnabledChange: (Boolean) -> Unit,
    onReauthorize: () -> Unit,
    onRemove: () -> Unit,
) {
    PreferenceGroupCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("work_directory_${directory.id}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (directory.accessStatus == WorkDirectoryAccessStatus.AVAILABLE) {
                        Icons.Default.Folder
                    } else {
                        Icons.Default.WarningAmber
                    },
                    contentDescription = null,
                    tint = if (directory.accessStatus == WorkDirectoryAccessStatus.AVAILABLE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        directory.displayName,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = directoryStatusText(directory),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (directory.accessStatus == WorkDirectoryAccessStatus.AVAILABLE) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                Switch(
                    checked = directory.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = directory.accessStatus == WorkDirectoryAccessStatus.AVAILABLE,
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.workfiles_remove_action),
                    )
                }
            }
            if (directory.accessStatus != WorkDirectoryAccessStatus.AVAILABLE) {
                TextButton(
                    onClick = onReauthorize,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.workfiles_reauthorize_action))
                }
            }
        }
    }
}

@Composable
private fun directoryStatusText(directory: WorkDirectory): String = when (directory.accessStatus) {
    WorkDirectoryAccessStatus.AVAILABLE -> if (directory.enabled) {
        stringResource(R.string.workfiles_status_available)
    } else {
        stringResource(R.string.workfiles_status_disabled)
    }
    WorkDirectoryAccessStatus.PERMISSION_LOST ->
        stringResource(R.string.workfiles_status_permission_lost)
    WorkDirectoryAccessStatus.PROVIDER_UNAVAILABLE ->
        stringResource(R.string.workfiles_status_provider_unavailable)
}

@Composable
private fun EmptyDirectoriesCard(onAdd: () -> Unit) {
    PreferenceGroupCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.workfiles_empty_state),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.workfiles_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    stringResource(R.string.workfiles_add_action),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StorageClearBanner(result: StorageClearSummary) {
    PreferenceBanner(
        text = if (result.failedDirectoryCount == 0) {
            stringResource(
                R.string.workfiles_storage_clear_success,
                formatStorageBytes(result.reclaimedBytes),
            )
        } else {
            stringResource(
                R.string.workfiles_storage_clear_partial,
                result.clearedDirectoryCount,
                result.failedDirectoryCount,
            )
        },
        tone = if (result.failedDirectoryCount == 0) {
            PreferenceBannerTone.Info
        } else {
            PreferenceBannerTone.Error
        },
    )
}

@Composable
private fun DirectoryOperationErrorBanner(error: WorkDirectoryOperationResult.Failure) {
    val message = when (error) {
        WorkDirectoryOperationResult.Failure.InvalidDirectory -> R.string.workfiles_error_invalid
        WorkDirectoryOperationResult.Failure.PermissionDenied -> R.string.workfiles_error_permission
        WorkDirectoryOperationResult.Failure.PersistenceFailed -> R.string.workfiles_error_persistence
        WorkDirectoryOperationResult.Failure.DuplicateDirectory -> R.string.workfiles_error_duplicate
        is WorkDirectoryOperationResult.Failure.OverlappingDirectory -> R.string.workfiles_error_overlap
        WorkDirectoryOperationResult.Failure.NotFound -> R.string.workfiles_error_not_found
    }
    PreferenceBanner(text = stringResource(message), tone = PreferenceBannerTone.Error)
}

internal fun formatStorageBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = if (value % 1.0 == 0.0 || value >= 10.0) {
        String.format(Locale.US, "%.0f", value)
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}

@Preview(name = "Empty - Light", showBackground = true)
@Preview(name = "Empty - Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WorkFilesSettingsEmptyPreview() {
    AsssistantaiTheme {
        Surface {
            WorkFilesSettingsScreen(
                state = WorkFilesSettingsUiState(storageSummary = previewStorageSummary()),
                onAction = {},
            )
        }
    }
}

@Preview(name = "Mixed states - Light", showBackground = true)
@Preview(
    name = "Mixed states - Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun WorkFilesSettingsMixedStatesPreview() {
    AsssistantaiTheme {
        Surface {
            WorkFilesSettingsScreen(
                state = WorkFilesSettingsUiState(
                    storageSummary = previewStorageSummary(),
                    directories = listOf(
                        previewDirectory("documents", "项目资料"),
                        previewDirectory("downloads", "下载", enabled = false),
                        previewDirectory(
                            "archive",
                            "归档",
                            status = WorkDirectoryAccessStatus.PERMISSION_LOST,
                        ),
                        previewDirectory(
                            "cloud",
                            "云端资料",
                            status = WorkDirectoryAccessStatus.PROVIDER_UNAVAILABLE,
                        ),
                    ),
                ),
                onAction = {},
            )
        }
    }
}

private fun previewStorageSummary() = AppStorageSummary(
    totalBytes = 286L * 1024 * 1024,
    persistentBytes = 214L * 1024 * 1024,
    cacheBytes = 48L * 1024 * 1024,
    temporaryBytes = 24L * 1024 * 1024,
    unreadableDirectoryCount = 0,
)

private fun previewDirectory(
    id: String,
    displayName: String,
    enabled: Boolean = true,
    status: WorkDirectoryAccessStatus = WorkDirectoryAccessStatus.AVAILABLE,
) = WorkDirectory(
    id = id,
    treeUri = "content://documents/tree/$id",
    displayName = displayName,
    authority = "com.android.externalstorage.documents",
    enabled = enabled,
    accessStatus = status,
    addedAtEpochMillis = 0L,
)
