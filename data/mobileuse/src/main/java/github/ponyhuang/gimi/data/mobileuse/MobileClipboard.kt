package github.ponyhuang.gimi.data.mobileuse

import android.app.AppOpsManager
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.os.Process
import android.util.Log

/** 为一次副屏粘贴保存并恢复用户剪贴板；读不到原内容或已有未恢复事务时拒绝覆盖。 */
internal class MobileClipboard(private val context: Context) {
    private val clipboard = MobileShellClipboard(context)
    private var saved = false
    private var previous: ClipData? = null
    private var inserted: String? = null

    @Synchronized
    fun prepare(text: String): Boolean {
        if (saved) return false
        return try {
            val readable = context.checkPermission("android.permission.READ_CLIPBOARD_IN_BACKGROUND", Process.myPid(), Process.myUid()) ==
                PackageManager.PERMISSION_GRANTED && context.getSystemService(AppOpsManager::class.java)
                .unsafeCheckOpNoThrow("android:read_clipboard", Process.myUid(), "com.android.shell") == AppOpsManager.MODE_ALLOWED
            if (!readable) return false
            val prior = clipboard.primaryClip
            if (MobileClipboardPolicy.read(prior != null, clipboard.hasPrimaryClip(), clipboard.primaryClipDescription != null) ==
                MobileClipboardPolicy.Read.Unreadable) return false
            previous = prior
            inserted = text
            saved = true // Binder 写入异常时也保留还原信息，不能假设剪贴板一定未改。
            val clip = ClipData.newPlainText("Gimi", text)
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
                putBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY", true)
            }
            clipboard.setPrimaryClip(clip)
            true
        } catch (failure: ReflectiveOperationException) {
            Log.w("GimiMobileUse", "Clipboard access unavailable", failure)
            restore()
            false
        } catch (failure: RuntimeException) {
            Log.w("GimiMobileUse", "Clipboard access refused", failure)
            restore()
            false
        }
    }

    @Synchronized
    fun restore() {
        if (!saved) return
        try {
            val current = clipboard.primaryClip
            val state = when (MobileClipboardPolicy.read(current != null, clipboard.hasPrimaryClip(), clipboard.primaryClipDescription != null)) {
                MobileClipboardPolicy.Read.Empty -> MobileClipboardPolicy.Now.Empty
                MobileClipboardPolicy.Read.Unreadable -> MobileClipboardPolicy.Now.Unreadable
                MobileClipboardPolicy.Read.Has -> if (current!!.itemCount == 1 && current.getItemAt(0).text?.toString() == inserted)
                    MobileClipboardPolicy.Now.Ours else MobileClipboardPolicy.Now.Other
            }
            when (MobileClipboardPolicy.restore(previous == null, state)) {
                MobileClipboardPolicy.Restore.PutBack -> {
                    val original = checkNotNull(previous)
                    original.description.extras = original.description.extras?.let(::PersistableBundle)?.apply {
                        putBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY", true)
                    } ?: PersistableBundle().apply { putBoolean("com.android.systemui.SUPPRESS_CLIPBOARD_OVERLAY", true) }
                    clipboard.setPrimaryClip(original)
                }
                MobileClipboardPolicy.Restore.Clear -> clipboard.clearPrimaryClip()
                MobileClipboardPolicy.Restore.Leave -> Unit
            }
            if (state == MobileClipboardPolicy.Now.Unreadable) return // 保留事务，停止会话时再尝试恢复。
            previous = null
            inserted = null
            saved = false
        } catch (failure: ReflectiveOperationException) {
            Log.w("GimiMobileUse", "Clipboard restore unavailable", failure)
        } catch (failure: RuntimeException) {
            Log.w("GimiMobileUse", "Clipboard restore refused", failure)
        }
    }
}
