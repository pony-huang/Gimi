package github.ponyhuang.gimi.feature.settings.about

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import github.ponyhuang.gimi.feature.settings.R
import github.ponyhuang.gimi.feature.settings.update.UpdateAction
import github.ponyhuang.gimi.feature.settings.update.UpdateDialog
import github.ponyhuang.gimi.feature.settings.update.UpdateEffect
import github.ponyhuang.gimi.feature.settings.update.UpdateViewModel
import github.ponyhuang.gimi.ui.preference.PreferenceScaffold

private const val PROJECT_URL = "https://github.com/pony-huang/Gimi"

/** APK 安装包 MIME 类型，调起系统安装器时使用。 */
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** 关于页路由：拥有更新状态 ViewModel、下载/安装对话框与项目主页外链等 Android 副作用。 */
@Composable
fun AboutRoute(
    appVersionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(updateViewModel) {
        updateViewModel.onAction(UpdateAction.ScreenEntered)
        updateViewModel.effects.collect { effect ->
            when (effect) {
                UpdateEffect.OpenAppDetails -> context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri(),
                    ),
                )

                UpdateEffect.OpenUnknownSourceSettings -> context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${context.packageName}".toUri(),
                    ),
                )

                is UpdateEffect.InstallApk -> context.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(effect.apkContentUri.toUri(), APK_MIME_TYPE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )

                is UpdateEffect.ShowToast ->
                    Toast.makeText(context, effect.messageRes, Toast.LENGTH_SHORT).show()
            }
        }
    }

    PreferenceScaffold(
        title = stringResource(R.string.settings_about_title),
        onBack = onBack,
    ) { scaffoldModifier ->
        AboutScreen(
            appVersionName = appVersionName,
            updateState = updateState,
            onUpdateAction = updateViewModel::onAction,
            onOpenProjectPage = {
                context.startActivity(Intent(Intent.ACTION_VIEW, PROJECT_URL.toUri()))
            },
            modifier = scaffoldModifier,
        )

        UpdateDialog(
            state = updateState,
            onAction = updateViewModel::onAction,
        )
    }
}
