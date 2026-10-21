package github.ponyhuang.gimi.feature.plugin

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.LocalizedText
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogSource
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.ui.preference.PreferenceBanner
import github.ponyhuang.gimi.ui.preference.PreferenceBannerTone
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.PreferenceTextField
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun PluginSettingsRoute(
    onBack: () -> Unit,
    onOpenSubscription: () -> Unit,
    onNavigateToConfig: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onAction(PluginSettingsAction.Refresh) }
    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, R.string.plugin_uninstalled_notice, Toast.LENGTH_SHORT).show()
        }
        viewModel.onAction(PluginSettingsAction.Refresh)
    }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onAction(PluginSettingsAction.Refresh) }

    // 进入页面自动重新发现插件并刷新订阅目录。
    LaunchedEffect(Unit) { viewModel.onAction(PluginSettingsAction.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PluginSettingsEffect.ShowPluginAdded -> Toast.makeText(
                    context,
                    context.getString(R.string.plugin_added_notice, effect.pluginIds.joinToString()),
                    Toast.LENGTH_SHORT,
                ).show()

                is PluginSettingsEffect.RequestSystemUninstall -> uninstallLauncher.launch(
                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${effect.packageName}")),
                )

                is PluginSettingsEffect.LaunchInstaller -> installLauncher.launch(
                    installerIntent(effect.contentUri),
                )

                PluginSettingsEffect.OpenUnknownSourcesSettings -> unknownSourcesLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
    }

    val icons = rememberPluginIcons(state.items)

    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = stringResource(R.string.plugin_screen_title),
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenSubscription) {
                Icon(
                    imageVector = Icons.Outlined.RssFeed,
                    contentDescription = stringResource(R.string.plugin_subscription_entry),
                )
            }
        },
    ) { scaffoldModifier ->
        PluginSettingsScreen(
            state = state,
            icons = icons,
            onAction = viewModel::onAction,
            onNavigateToConfig = onNavigateToConfig,
            onNavigateToDetail = onNavigateToDetail,
            modifier = scaffoldModifier.then(modifier),
        )
    }
}

/** 已安装插件优先使用 APK 自带图标；未安装条目使用内置矢量图。 */
@Composable
internal fun rememberPluginIcons(items: List<PluginListItemUi>): Map<String, ImageBitmap?> {
    val context = LocalContext.current
    val installedPackages = items.mapNotNull { item ->
        item.installed?.let { it.id to it.packageName }
    }
    return remember(installedPackages) {
        installedPackages.associate { (id, packageName) ->
            id to loadPluginIcon(context, packageName)?.toBitmap()?.asImageBitmap()
        }
    }
}

/**
 * 插件列表：搜索、订阅目录与已安装插件合并后的单列列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSettingsScreen(
    state: PluginSettingsUiState,
    icons: Map<String, ImageBitmap?>,
    onAction: (PluginSettingsAction) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(PluginSettingsAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PluginListSearchField(
                        query = state.query,
                        onQueryChange = { onAction(PluginSettingsAction.QueryChanged(it)) },
                    )
                }
                if (state.catalogError) {
                    item {
                        PreferenceBanner(
                            text = stringResource(
                                if (state.catalogSource == PluginCatalogSource.BUNDLED) {
                                    R.string.plugin_list_catalog_error
                                } else {
                                    R.string.plugin_list_catalog_partial_error
                                },
                            ),
                            tone = PreferenceBannerTone.Info,
                        )
                    }
                }
                if (!state.canRequestPackageInstalls) {
                    item {
                        PreferenceBanner(
                            text = stringResource(R.string.plugin_list_unknown_sources),
                            tone = PreferenceBannerTone.Info,
                        )
                    }
                }
                when {
                    state.isLoadingCatalog && state.items.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    state.items.isEmpty() -> item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.query.isBlank()) R.string.plugin_empty
                                    else R.string.plugin_list_empty,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> items(state.items, key = { it.key }) { item ->
                        PluginRowCard(
                            item = item,
                            installedIcon = icons[item.key],
                            mutationEnabled = !state.isRefreshing,
                            installEnabled = state.installingEntryKey == null,
                            onToggleEnabled = { enabled ->
                                item.installed?.let {
                                    onAction(PluginSettingsAction.SetEnabled(it.id, enabled))
                                }
                            },
                            onOpenDetail = { onNavigateToDetail(item.key) },
                            onConfigure = { onNavigateToConfig(item.key) },
                            onUninstall = {
                                item.installed?.let {
                                    onAction(PluginSettingsAction.RequestUninstall(it.id))
                                }
                            },
                            onInstall = {
                                onAction(
                                    if (item.isInstalled) PluginSettingsAction.Update(item.key)
                                    else PluginSettingsAction.Install(item.key),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    state.pendingUninstallPluginId
        ?.let { id -> state.items.firstOrNull { it.installed?.id == id }?.installed }
        ?.let { plugin ->
            UninstallConfirmDialog(
                plugin = plugin,
                onConfirm = { onAction(PluginSettingsAction.ConfirmUninstall(plugin.id)) },
                onDismiss = { onAction(PluginSettingsAction.DismissUninstall) },
            )
        }
}

@Composable
private fun PluginListSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    PreferenceTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.plugin_list_search_label)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.plugin_list_search_clear),
                    )
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun PluginRowCard(
    item: PluginListItemUi,
    installedIcon: ImageBitmap?,
    mutationEnabled: Boolean,
    installEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onOpenDetail: () -> Unit,
    onConfigure: () -> Unit,
    onUninstall: () -> Unit,
    onInstall: () -> Unit,
) {
    Surface(
        onClick = if (item.isInstalled) onConfigure else onOpenDetail,
        shape = RoundedCornerShape(20.dp),
        color = preferenceGroupCardColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PluginIcon(
                    iconKey = item.catalog?.iconKey ?: item.key,
                    size = 44.dp,
                    installedIcon = installedIcon.takeIf { item.isInstalled },
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val summary = item.catalog?.let { localized(it.summary) }
                    if (summary != null) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PluginRowMenu(
                    installed = item.isInstalled,
                    enabled = mutationEnabled,
                    onOpenDetail = onOpenDetail,
                    onUninstall = onUninstall,
                )
            }
            if (item.isInstalled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(if (item.isUpdateAvailable) R.string.plugin_list_update_available else R.string.plugin_list_installed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Switch(
                        checked = item.installed?.isEnabled == true,
                        onCheckedChange = onToggleEnabled,
                        enabled = mutationEnabled,
                    )
                }
            }
            if (!item.isInstalled || item.isUpdateAvailable) {
                PluginInstallAction(
                    entry = item,
                    enabled = installEnabled,
                    onInstall = onInstall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PluginRowMenu(
    installed: Boolean,
    enabled: Boolean,
    onOpenDetail: () -> Unit,
    onUninstall: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.plugin_row_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.plugin_detail_menu)) },
                onClick = {
                    expanded = false
                    onOpenDetail()
                },
            )
            if (installed) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.plugin_uninstall_menu),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        expanded = false
                        onUninstall()
                    },
                )
            }
        }
    }
}

@Composable
private fun UninstallConfirmDialog(
    plugin: PluginDescriptor,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.plugin_uninstall_title, plugin.name)) },
        text = {
            Text(text = stringResource(R.string.plugin_uninstall_message))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.plugin_uninstall_confirm),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.plugin_uninstall_cancel))
            }
        },
    )
}

internal fun installerIntent(contentUri: String): Intent = Intent(Intent.ACTION_VIEW)
    .setDataAndType(Uri.parse(contentUri), "application/vnd.android.package-archive")
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

// ---------------------------------------------------------------------------
// Preview fixtures
// ---------------------------------------------------------------------------

private fun previewCatalogEntry(
    key: String,
    title: String,
    summaryZh: String,
    summaryEn: String,
) = PluginCatalogEntry(
    key = key,
    packageName = "github.ponyhuang.gimi.plugin.$key",
    title = title,
    summary = LocalizedText(summaryZh, summaryEn),
    description = LocalizedText(summaryZh, summaryEn),
    highlights = emptyList(),
    sortOrder = 0,
    pluginVersion = 1,
    releaseAssetPrefix = "Gimi-plugin-$key-v",
    repoUrl = "https://github.com/pony-huang/Gimi",
    iconKey = key,
    tags = emptyList(),
)

private fun previewItem(
    key: String,
    title: String,
    summaryZh: String,
    summaryEn: String,
    installed: Boolean = false,
    installState: PluginInstallUiState = PluginInstallUiState.Idle,
) = PluginListItemUi(
    catalog = previewCatalogEntry(key, title, summaryZh, summaryEn),
    installed = if (installed) {
        PluginDescriptor(
            id = key,
            name = title,
            packageName = "github.ponyhuang.gimi.plugin.$key",
            version = 1,
            toolCount = 3,
            isEnabled = true,
        )
    } else {
        null
    },
    installState = installState,
)

private fun previewState(
    items: List<PluginListItemUi> = listOf(
        previewItem("zhihu", "知乎", "站内搜索、热榜、直答与知识库", "Search, hot lists, answers, and knowledge bases", installed = true),
        previewItem("xiaohongshu", "小红书", "登录后浏览推荐、搜索、互动与发布", "Browse, search, interact, and publish", installed = true),
        previewItem("spotify", "Spotify", "搜索、播放、歌单和音乐库", "Search, playback, playlists, and your library"),
        previewItem("v2ex", "V2EX", "节点、主题、回复、通知与账号信息", "Nodes, topics, replies, notifications, and account info"),
        previewItem("weibo", "微博", "热搜、智搜、超话与账号微博", "Trending, AI search, Super Topics, and posts"),
    ),
) = PluginSettingsUiState(
    items = items,
    isLoadingCatalog = false,
    catalogSource = PluginCatalogSource.REMOTE,
)

@Preview(name = "插件列表 · 浅色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "插件列表 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginSettingsScreenPreview() {
    AsssistantaiTheme {
        PluginSettingsScreen(
            state = previewState(),
            icons = emptyMap(),
            onAction = {},
            onNavigateToConfig = {},
            onNavigateToDetail = {},
        )
    }
}

@Preview(name = "插件列表 · 安装中", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Composable
private fun PluginSettingsScreenInstallingPreview() {
    AsssistantaiTheme {
        val items = previewState().items.map { item ->
            if (item.key == "v2ex") {
                item.copy(installState = PluginInstallUiState.Downloading(0.42f))
            } else {
                item
            }
        }
        PluginSettingsScreen(
            state = previewState(items = items).copy(installingEntryKey = "v2ex"),
            icons = emptyMap(),
            onAction = {},
            onNavigateToConfig = {},
            onNavigateToDetail = {},
        )
    }
}

@Preview(name = "更新状态 · 浅色", widthDp = 390, heightDp = 1100, locale = "zh")
@Preview(name = "更新状态 · 深色", widthDp = 390, heightDp = 1100, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginUpdateStatesPreview() {
    val catalog = previewCatalogEntry("zhihu", "知乎", "站内搜索、热榜、直答与知识库", "Search and knowledge bases").copy(versionCode = 61L, versionName = "1.1.0")
    val installed = PluginDescriptor("zhihu", "知乎", catalog.packageName, 1, 4, true, 60L, "1.0.0")
    AsssistantaiTheme {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(listOf(
                PluginInstallUiState.Idle,
                PluginInstallUiState.Resolving,
                PluginInstallUiState.Downloading(0.42f),
                PluginInstallUiState.Failed(github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure.NETWORK),
            )) { state ->
                PluginRowCard(
                    item = PluginListItemUi(catalog, installed, state),
                    installedIcon = null,
                    mutationEnabled = true,
                    installEnabled = !state.isInProgress,
                    onToggleEnabled = {}, onOpenDetail = {}, onConfigure = {}, onUninstall = {}, onInstall = {},
                )
            }
        }
    }
}
