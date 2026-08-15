package github.ponyhuang.gimi.data.appupdate.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.appupdate.apk.ApkInstaller
import github.ponyhuang.gimi.domain.appupdate.repository.AppUpdateRepository
import java.io.File

/** 更新通知上的操作：取消下载 / 点击安装。 */
class UpdateActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UpdateActionEntryPoint {
        fun appUpdateRepository(): AppUpdateRepository

        fun apkInstaller(): ApkInstaller
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UpdateActionEntryPoint::class.java,
        )
        when (intent.action) {
            ACTION_CANCEL_DOWNLOAD -> entryPoint.appUpdateRepository().cancelDownload()
            ACTION_INSTALL -> {
                val path = intent.getStringExtra(EXTRA_APK_PATH) ?: return
                val installer = entryPoint.apkInstaller()
                val target = if (installer.canRequestPackageInstalls()) {
                    installer.installIntent(File(path))
                } else {
                    installer.unknownSourcesSettingsIntent()
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(target)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD =
            "github.ponyhuang.gimi.appupdate.action.CANCEL_DOWNLOAD"
        const val ACTION_INSTALL =
            "github.ponyhuang.gimi.appupdate.action.INSTALL"
        const val EXTRA_APK_PATH = "apk_path"
    }
}
