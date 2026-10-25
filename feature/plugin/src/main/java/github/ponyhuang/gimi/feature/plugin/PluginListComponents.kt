package github.ponyhuang.gimi.feature.plugin

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.plugin.model.LocalizedText
import github.ponyhuang.gimi.domain.plugin.model.PluginInstallFailure
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/** 目录 iconKey 到宿主内置矢量图的映射；未知 key 回退通用插件图标。 */
internal fun pluginStoreIconResource(iconKey: String): Int = when (iconKey) {
    "netease" -> R.drawable.ic_plugin_netease
    "spotify" -> R.drawable.ic_plugin_spotify
    "zhihu" -> R.drawable.ic_plugin_zhihu
    "v2ex" -> R.drawable.ic_plugin_v2ex
    "weibo" -> R.drawable.ic_plugin_weibo
    "xiaohongshu" -> R.drawable.ic_plugin_xiaohongshu
    else -> R.drawable.ic_plugin_default
}

/** 按当前界面语言选择目录文案。 */
@Composable
internal fun localized(text: LocalizedText): String =
    text.resolve(LocalConfiguration.current.locales[0].language)

/**
 * 插件图标容器：统一圆角方片，宽版品牌图等比缩放。
 *
 * [installedIcon] 来自已安装插件 APK 的真实图标；未安装条目使用内置矢量图。
 */
@Composable
internal fun PluginIcon(
    iconKey: String,
    size: Dp,
    installedIcon: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (installedIcon != null) {
            Image(
                bitmap = installedIcon,
                contentDescription = null,
                modifier = Modifier.size(size * 0.72f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Image(
                painter = painterResource(pluginStoreIconResource(iconKey)),
                contentDescription = null,
                modifier = Modifier.size(size * 0.68f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/**
 * 未安装条目的安装操作区，以及已安装条目的更新操作区。
 *
 * 按条目状态显示安装/更新按钮；下载/解析中显示进度；失败显示可重试的错误。
 */
@Composable
internal fun PluginInstallAction(
    entry: PluginListItemUi,
    enabled: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val state = entry.installState) {
        PluginInstallUiState.Idle -> {
            FilledTonalButton(
                onClick = onInstall,
                enabled = enabled,
                modifier = modifier.heightIn(min = 36.dp),
            ) {
                Text(stringResource(if (entry.isInstalled) R.string.plugin_list_update else R.string.plugin_list_install))
            }
        }

        PluginInstallUiState.Resolving -> InstallProgress(
            progress = null,
            label = stringResource(if (entry.isInstalled) R.string.plugin_list_update_resolving else R.string.plugin_list_installing),
            modifier = modifier,
        )

        is PluginInstallUiState.Downloading -> InstallProgress(
            progress = state.progress,
            label = stringResource(
                if (entry.isInstalled) R.string.plugin_list_update_downloading else R.string.plugin_list_downloading,
                (state.progress * 100).toInt().coerceIn(0, 100),
            ),
            modifier = modifier,
        )

        is PluginInstallUiState.Failed -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(
                    if (entry.isInstalled && state.failure == PluginInstallFailure.NETWORK)
                        R.string.plugin_list_update_network
                    else pluginInstallFailureMessage(state.failure),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
            )
            TextButton(onClick = onInstall, enabled = enabled) {
                Text(stringResource(if (entry.isInstalled) R.string.plugin_list_update_retry else R.string.plugin_list_retry))
            }
        }
    }
}

/** 进度条 + 文案；[progress] 为 null 时使用不确定进度。 */
@Composable
private fun InstallProgress(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (progress == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { progress },
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 安装失败原因到可本地化文案的映射。 */
internal fun pluginInstallFailureMessage(failure: PluginInstallFailure): Int = when (failure) {
    PluginInstallFailure.NETWORK -> R.string.plugin_list_install_network
    PluginInstallFailure.CHECKSUM_MISMATCH -> R.string.plugin_list_install_checksum
    PluginInstallFailure.NO_RELEASE_ASSET -> R.string.plugin_list_install_no_asset
    PluginInstallFailure.PACKAGE_TOO_LARGE -> R.string.plugin_list_install_too_large
    PluginInstallFailure.STORAGE -> R.string.plugin_list_install_storage
    PluginInstallFailure.INVALID_PACKAGE -> R.string.plugin_list_install_invalid_package
    PluginInstallFailure.NOT_AN_UPDATE -> R.string.plugin_list_not_an_update
    PluginInstallFailure.UNKNOWN -> R.string.plugin_list_install_unknown
}

@Preview(name = "网易云品牌图标 · 浅色", showBackground = true)
@Preview(name = "网易云品牌图标 · 深色", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NeteasePluginIconPreview() {
    AsssistantaiTheme {
        Surface {
            PluginIcon(iconKey = "netease", size = 56.dp)
        }
    }
}
