package github.ponyhuang.gimi.feature.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateState
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.feature.settings.update.UpdateAction
import github.ponyhuang.gimi.feature.settings.update.UpdateUiState
import github.ponyhuang.gimi.ui.preference.PreferenceGroupCard
import github.ponyhuang.gimi.ui.preference.PreferenceListItem
import github.ponyhuang.gimi.ui.preference.PreferenceNavigationCard
import github.ponyhuang.gimi.ui.preference.PreferencePageContainer
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold
import github.ponyhuang.gimi.ui.theme.AsssistantaiTheme

/**
 * 「关于」页：顶部展示应用名称与当前版本，下方列表提供检查更新（内联状态）与项目主页入口。
 * 后续新增功能（导出日志等）追加到 [PreferenceGroupCard] 内即可。
 */
@Composable
fun AboutScreen(
    appVersionName: String,
    updateState: UpdateUiState,
    onUpdateAction: (UpdateAction) -> Unit,
    onOpenProjectPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferencePageContainer(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.update_subtitle_current, appVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            PreferenceGroupCard {
                PreferenceListItem(
                    icon = Icons.Default.SystemUpdate,
                    title = stringResource(R.string.settings_update_title),
                    subtitle = updateSubtitle(updateState),
                    showDivider = true,
                    onClick = { onUpdateAction(UpdateAction.CheckNow) },
                )
                PreferenceNavigationCard(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.about_project_title),
                    subtitle = stringResource(R.string.about_project_subtitle),
                    onClick = onOpenProjectPage,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 检查更新行的内联状态副标题。 */
@Composable
private fun updateSubtitle(updateState: UpdateUiState): String = when (val status = updateState.status) {
    is AppUpdateState.Checking -> stringResource(R.string.update_checking)
    is AppUpdateState.Available ->
        stringResource(R.string.update_available_subtitle, status.info.tagName)
    is AppUpdateState.Downloading ->
        stringResource(R.string.update_downloading_subtitle, (status.progress * 100).toInt())
    is AppUpdateState.Downloaded ->
        stringResource(R.string.update_downloaded_subtitle, status.info.tagName)
    else -> stringResource(R.string.update_subtitle_current, updateState.currentVersionName)
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun AboutScreenPreview() {
    AsssistantaiTheme {
        PreferenceScaffold(title = stringResource(R.string.settings_about_title), onBack = {}) { modifier ->
            AboutScreen(
                appVersionName = "1.0.0",
                updateState = UpdateUiState(currentVersionName = "1.0.0"),
                onUpdateAction = {},
                onOpenProjectPage = {},
                modifier = modifier,
            )
        }
    }
}

@Preview(name = "Phone-发现新版本", device = Devices.PHONE, showBackground = true)
@Composable
private fun AboutScreenAvailablePreview() {
    AsssistantaiTheme {
        PreferenceScaffold(title = stringResource(R.string.settings_about_title), onBack = {}) { modifier ->
            AboutScreen(
                appVersionName = "1.0.0",
                updateState = UpdateUiState(
                    status = AppUpdateState.Checking,
                    currentVersionName = "1.0.0",
                ),
                onUpdateAction = {},
                onOpenProjectPage = {},
                modifier = modifier,
            )
        }
    }
}
