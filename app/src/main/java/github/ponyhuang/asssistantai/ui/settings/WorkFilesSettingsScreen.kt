package github.ponyhuang.asssistantai.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import github.ponyhuang.asssistantai.data.DocumentDirectoryRepository
import javax.inject.Inject

@HiltViewModel
class WorkFilesSettingsViewModel @Inject constructor(
    private val documentDirectories: DocumentDirectoryRepository,
) : ViewModel() {
    val directories = documentDirectories.directories

    fun addDirectory(uri: Uri) = documentDirectories.addDirectory(uri)

    fun removeDirectory(uri: Uri) = documentDirectories.removeDirectory(uri)
}

@Composable
fun WorkFilesSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkFilesSettingsViewModel = hiltViewModel(),
) {
    val directories by viewModel.directories.collectAsStateWithLifecycle()
    val documentTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::addDirectory) }

    SettingsPageContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            item {
                SettingsListItem(
                    icon = Icons.Default.Folder,
                    title = "工作文件夹",
                    subtitle = if (directories.isEmpty()) {
                        "添加允许助手搜索的本机文件夹"
                    } else {
                        "已授权 ${directories.size} 个文件夹"
                    },
                    onClick = { documentTreeLauncher.launch(null) },
                    trailingContent = {
                        IconButton(onClick = { documentTreeLauncher.launch(null) }) {
                            Icon(Icons.Default.Add, contentDescription = "添加工作文件夹")
                        }
                    },
                )
            }
            item { SettingsSectionTitle(text = "已授权文件夹") }
            if (directories.isEmpty()) {
                item {
                    Text(
                        text = "尚未添加工作文件夹",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
            }
            items(directories, key = Uri::toString) { uri ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = uri.lastPathSegment ?: uri.toString(),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = uri.authority.orEmpty(),
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
                        IconButton(onClick = { viewModel.removeDirectory(uri) }) {
                            Icon(Icons.Default.Delete, contentDescription = "移除工作文件夹")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }
        }
    }
}
