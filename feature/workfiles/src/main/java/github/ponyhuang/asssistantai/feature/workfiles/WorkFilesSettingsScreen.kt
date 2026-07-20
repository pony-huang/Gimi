package github.ponyhuang.asssistantai.feature.workfiles

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import github.ponyhuang.asssistantai.ui.settings.SettingsListItem
import github.ponyhuang.asssistantai.ui.settings.SettingsPageContainer
import github.ponyhuang.asssistantai.ui.settings.SettingsSectionTitle

@Composable
fun WorkFilesSettingsScreen(
    state: WorkFilesSettingsUiState,
    onAction: (WorkFilesSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SettingsListItem(
                    icon = Icons.Default.Folder,
                    title = "工作文件夹",
                    subtitle = if (state.directories.isEmpty()) {
                        "添加允许助手搜索的本机文件夹"
                    } else {
                        "已授权 ${state.directories.size} 个文件夹"
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
                            Icon(Icons.Default.Add, contentDescription = "添加工作文件夹")
                        }
                    },
                )
            }
            item { SettingsSectionTitle(text = "已授权文件夹") }
            if (state.directories.isEmpty()) {
                item {
                    Text(
                        text = "尚未添加工作文件夹",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(state.directories, key = { it.uri }) { directory ->
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
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                onAction(WorkFilesSettingsAction.RemoveDirectory(directory.uri))
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "移除工作文件夹")
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
}
