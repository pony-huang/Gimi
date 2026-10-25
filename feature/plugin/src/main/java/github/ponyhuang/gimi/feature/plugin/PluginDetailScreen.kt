package github.ponyhuang.gimi.feature.plugin

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.domain.plugin.model.LocalizedText
import github.ponyhuang.gimi.domain.plugin.model.PluginCatalogEntry
import github.ponyhuang.gimi.domain.plugin.model.PluginDescriptor
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.preference.PreferenceStyle
import github.ponyhuang.gimi.ui.preference.preferenceGroupCardColor
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

@Composable
fun PluginDetailRoute(
    entryKey: String,
    onBack: () -> Unit,
    onNavigateToConfig: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PluginSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.key == entryKey }
    val icons = rememberPluginIcons(state.items)
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val installLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onAction(PluginSettingsAction.Refresh) }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onAction(PluginSettingsAction.Refresh) }

    LaunchedEffect(Unit) { viewModel.onAction(PluginSettingsAction.Refresh) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is PluginSettingsEffect.LaunchInstaller ->
                    installLauncher.launch(installerIntent(effect.contentUri))

                PluginSettingsEffect.OpenUnknownSourcesSettings ->
                    unknownSourcesLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )

                // 详情页不处理卸载/新装通知；列表页会消费对应事件。
                is PluginSettingsEffect.RequestSystemUninstall,
                is PluginSettingsEffect.ShowPluginAdded,
                -> Unit
            }
        }
    }

    PreferenceScaffold(
        style = PreferenceStyle.Compact,
        title = item?.title ?: stringResource(R.string.plugin_detail_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        PluginDetailScreen(
            item = item,
            installedIcon = item?.let { icons[it.key] },
            installEnabled = state.installingEntryKey == null,
            isLoading = state.isLoadingCatalog,
            onAction = viewModel::onAction,
            onNavigateToConfig = onNavigateToConfig,
            onOpenUrl = { url -> runCatching { uriHandler.openUri(url) } },
            modifier = scaffoldModifier.then(modifier),
        )
    }
}

/**
 * 插件详细信息：介绍、能力与仓库链接；已安装时进入配置，未安装时可直接安装。
 *
 * 展示 APK 当前/最新版本，更新操作与列表共用安装状态。
 */
@Composable
fun PluginDetailScreen(
    item: PluginListItemUi?,
    installedIcon: ImageBitmap?,
    installEnabled: Boolean,
    isLoading: Boolean,
    onAction: (PluginSettingsAction) -> Unit,
    onNavigateToConfig: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier, style = PreferenceStyle.Compact) {
        when {
            item == null && isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            item == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.plugin_detail_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = preferenceGroupCardColor(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PluginIcon(
                                    iconKey = item.catalog?.iconKey ?: item.key,
                                    size = 72.dp,
                                    installedIcon = installedIcon.takeIf { item.isInstalled },
                                )
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            val summary = item.catalog?.let { localized(it.summary) }
                            if (summary != null) {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item {
                    DetailSection(title = stringResource(R.string.plugin_detail_version)) {
                        item.installed?.let { installed ->
                            VersionRow(
                                stringResource(R.string.plugin_detail_current_version),
                                installed.versionName?.takeIf(String::isNotBlank)
                                    ?: installed.versionCode.takeIf { it > 0L }?.toString()
                                    ?: stringResource(R.string.plugin_detail_version_unknown),
                            )
                        }
                        item.catalog?.let { catalog ->
                            VersionRow(
                                stringResource(R.string.plugin_detail_latest_version),
                                catalog.versionName?.takeIf(String::isNotBlank)
                                    ?: catalog.versionCode?.toString()
                                    ?: stringResource(R.string.plugin_detail_version_unknown),
                            )
                        }
                        if (!item.isInstalled || item.isUpdateAvailable) {
                            PluginInstallAction(
                                entry = item,
                                enabled = installEnabled,
                                onInstall = {
                                    onAction(
                                        if (item.isInstalled) PluginSettingsAction.Update(item.key)
                                        else PluginSettingsAction.Install(item.key),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (item.isInstalled) {
                            OutlinedButton(
                                onClick = { onNavigateToConfig(item.key) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.plugin_detail_manage)) }
                        }
                    }
                }
                item {
                    DetailSection(title = stringResource(R.string.plugin_detail_description)) {
                        Text(
                            text = item.catalog?.let { localized(it.description) }
                                ?: stringResource(R.string.plugin_detail_no_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                val highlights = item.catalog?.highlights.orEmpty()
                if (highlights.isNotEmpty()) {
                    item {
                        DetailSection(title = stringResource(R.string.plugin_detail_highlights)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                highlights.forEach { highlight ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = localized(highlight),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                val repoUrl = item.catalog?.repoUrl?.takeIf(String::isNotBlank)
                if (repoUrl != null) {
                    item {
                        TextButton(onClick = { onOpenUrl(repoUrl) }) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.width(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.plugin_detail_repo))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionRow(label: String, version: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(version, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = preferenceGroupCardColor(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Preview fixtures
// ---------------------------------------------------------------------------

private fun previewDetailItem(installed: Boolean) = PluginListItemUi(
    catalog = PluginCatalogEntry(
        key = "zhihu",
        packageName = "github.ponyhuang.gimi.plugin.zhihu",
        title = "知乎",
        summary = LocalizedText(
            "站内搜索、热榜、直答与知识库",
            "Search, hot lists, answers, and knowledge bases",
        ),
        description = LocalizedText(
            "接入知乎开放平台，支持站内与全网搜索、热榜、直答，以及知识库检索、PDF 解析和 PPT 生成等异步任务。",
            "Connects to the Zhihu Open Platform for on-site and web search, hot lists, synthesized answers, knowledge-base retrieval, PDF parsing, and PPT generation.",
        ),
        highlights = listOf(
            LocalizedText("站内与全网搜索", "On-site and web search"),
            LocalizedText("热榜与直答", "Hot lists and synthesized answers"),
            LocalizedText("知识库检索与文档任务", "Knowledge bases and document tasks"),
        ),
        sortOrder = 10,
        pluginVersion = 2,
        releaseAssetPrefix = "Gimi-plugin-zhihu-v",
        repoUrl = "https://github.com/pony-huang/Gimi",
        iconKey = "zhihu",
        tags = listOf("知乎", "问答"),
    ),
    installed = if (installed) {
        PluginDescriptor(
            id = "zhihu",
            name = "知乎",
            packageName = "github.ponyhuang.gimi.plugin.zhihu",
            version = 2,
            toolCount = 12,
            isEnabled = true,
        )
    } else {
        null
    },
    installState = PluginInstallUiState.Idle,
)

@Preview(name = "详情 · 未安装", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "详情 · 深色", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginDetailScreenPreview() {
    AsssistantaiTheme {
        PluginDetailScreen(
            item = previewDetailItem(installed = false),
            installedIcon = null,
            installEnabled = true,
            isLoading = false,
            onAction = {},
            onNavigateToConfig = {},
            onOpenUrl = {},
        )
    }
}

@Preview(name = "详情 · 已安装", showBackground = true, widthDp = 390, heightDp = 900, locale = "zh")
@Composable
private fun PluginDetailScreenInstalledPreview() {
    AsssistantaiTheme {
        PluginDetailScreen(
            item = previewDetailItem(installed = true),
            installedIcon = null,
            installEnabled = true,
            isLoading = false,
            onAction = {},
            onNavigateToConfig = {},
            onOpenUrl = {},
        )
    }
}

@Preview(name = "详情更新 · 浅色", widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "详情更新 · 深色", widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginDetailUpdatePreview() {
    PluginDetailUpdateFixture(PluginInstallUiState.Idle)
}

@Preview(name = "详情更新失败 · 浅色", widthDp = 390, heightDp = 900, locale = "zh")
@Preview(name = "详情更新失败 · 深色", widthDp = 390, heightDp = 900, locale = "zh", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PluginDetailUpdateFailedPreview() {
    PluginDetailUpdateFixture(PluginInstallUiState.Failed(github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure.NETWORK))
}

@Composable
private fun PluginDetailUpdateFixture(installState: PluginInstallUiState) {
    val fixture = previewDetailItem(installed = true)
    AsssistantaiTheme {
        PluginDetailScreen(
            item = fixture.copy(
                catalog = fixture.catalog?.copy(versionCode = 61L, versionName = "1.1.0"),
                installed = fixture.installed?.copy(versionCode = 60L, versionName = "1.0.0"),
                installState = installState,
            ),
            installedIcon = null, installEnabled = true, isLoading = false,
            onAction = {}, onNavigateToConfig = {}, onOpenUrl = {},
        )
    }
}
