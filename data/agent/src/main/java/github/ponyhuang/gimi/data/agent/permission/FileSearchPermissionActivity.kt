package github.ponyhuang.gimi.data.agent.permission

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** 中转系统所有文件访问授权；返回时读取实际权限，不依赖设置页的 resultCode。 */
class FileSearchPermissionActivity : ComponentActivity() {
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { finishWithAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Environment.isExternalStorageManager()) {
            finishWithAccess()
        } else if (savedInstanceState == null) {
            try {
                settingsLauncher.launch(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        .setData(Uri.parse("package:$packageName")),
                )
            } catch (_: ActivityNotFoundException) {
                try {
                    settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                } catch (_: ActivityNotFoundException) {
                    finishWithAccess()
                }
            }
        }
    }

    private fun finishWithAccess() {
        setResult(if (Environment.isExternalStorageManager()) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}
