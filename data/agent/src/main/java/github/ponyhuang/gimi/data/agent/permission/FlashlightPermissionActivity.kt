package github.ponyhuang.gimi.data.agent.permission

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** 为手电筒控制发起系统相机权限申请。 */
class FlashlightPermissionActivity : ComponentActivity() {
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            finish()
        } else if (savedInstanceState == null) {
            permissionRequest.launch(Manifest.permission.CAMERA)
        }
    }
}
