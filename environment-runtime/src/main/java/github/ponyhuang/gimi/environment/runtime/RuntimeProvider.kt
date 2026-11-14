package github.ponyhuang.gimi.environment.runtime

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import java.io.IOException

/** call() 显式校验签名，避免其它应用绕过普通 Provider 读写权限。 */
class RuntimeProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val app = requireNotNull(context)
        check(app.packageManager.checkSignatures(Binder.getCallingUid(), app.applicationInfo.uid) == PackageManager.SIGNATURE_MATCH)
        // 签名校验后切回组件自身身份，后台限制查询和服务停止均属于组件 UID。
        val identity = Binder.clearCallingIdentity()
        return try {
            if (method == "requestOperation") {
                // 安装/启用改为预登记：Activity 由宿主（前台）拉起，此处只记录意图。
                RuntimeEngine.get(app).requestOperation(requireNotNull(extras?.getString("operation")))
                Bundle()
            } else RuntimeEngine.get(app).call(method, extras ?: Bundle())
        } catch (error: IOException) {
            Bundle().apply { putBoolean("failed", true) }
        } catch (error: IllegalArgumentException) {
            Bundle().apply { putBoolean("failed", true) }
        } catch (error: IllegalStateException) {
            Bundle().apply { putBoolean("failed", true) }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }
    override fun openFile(uri: Uri, mode: String): android.os.ParcelFileDescriptor {
        val app = requireNotNull(context)
        check(app.packageManager.checkSignatures(Binder.getCallingUid(), app.applicationInfo.uid) == PackageManager.SIGNATURE_MATCH)
        require(mode == "r" && uri.path == "/export")
        val identity = Binder.clearCallingIdentity()
        return try {
            RuntimeEngine.get(app).openExportFile(
                requireNotNull(uri.getQueryParameter("session")),
                requireNotNull(uri.getQueryParameter("path")),
            )
        } finally { Binder.restoreCallingIdentity(identity) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
