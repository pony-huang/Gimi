package github.ponyhuang.gimi.data.mobileuse

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Process
import java.lang.reflect.Method

/**
 * shell 进程直接访问系统剪贴板。有的系统里 ClipboardManager 读剪贴板前会先读 Settings,
 * 但 app_process 没有普通 App 的进程登记,不能经 ActivityThread 获取 ContentProvider。
 * Binder 仍按包名、uid、用户和设备检查权限;这里只避开 Java 包装层额外的 Settings 查询。
 * 认不出的接口(例如更早版本的签名)退回 ClipboardManager,行为与以前一样。
 */
@SuppressLint("PrivateApi")
internal class MobileShellClipboard(private val context: Context) {
    private val manager: ClipboardManager by lazy {
        ClipboardManager::class.java.getDeclaredConstructor(Context::class.java, Handler::class.java)
            .apply { isAccessible = true }.newInstance(context, Handler(shellLooper))
    }
    private val service: Any? by lazy {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java).invoke(null, "clipboard") as IBinder
            Class.forName("android.content.IClipboard\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        }.getOrNull()
    }
    private val getClip by lazy { method("getPrimaryClip") }
    private val getDescription by lazy { method("getPrimaryClipDescription") }
    private val hasClip by lazy { method("hasPrimaryClip") }
    private val callerLabel by lazy {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo("com.android.shell", 0)).toString()
        }.getOrDefault("com.android.shell")
    }

    val primaryClip: ClipData?
        get() = getClip.let { if (it == null) manager.primaryClip else call(it) as ClipData? }
    val primaryClipDescription: ClipDescription?
        get() = getDescription.let { if (it == null) manager.primaryClipDescription else call(it) as ClipDescription? }
    fun hasPrimaryClip(): Boolean = hasClip.let { if (it == null) manager.hasPrimaryClip() else call(it) as Boolean }
    // 写入仍走框架原来的 ClipData 准备和清空流程,只替换有额外 Settings 查询的读取路径。
    fun setPrimaryClip(clip: ClipData) = manager.setPrimaryClip(clip)
    fun clearPrimaryClip() = manager.clearPrimaryClip()

    /** 按能力找接口:先认原版签名,再认已知的扩展签名;都不是就返回 null,由调用方退回 ClipboardManager。 */
    private fun method(name: String): Method? {
        if (service == null) return null
        val type = runCatching { Class.forName("android.content.IClipboard") }.getOrNull() ?: return null
        val s = String::class.java
        val i = Int::class.javaPrimitiveType!!
        val candidates = listOf(
            // Android 14+:包名、attributionTag、userId、deviceId
            arrayOf(s, s, i, i),
            // Android 12–13:包名、attributionTag、userId
            arrayOf(s, s, i),
            // 有的系统把读取扩成:包名、应用名、隐私栈、attributionTag、userId、deviceId、是否用户操作
            arrayOf(s, s, s, s, i, i, Boolean::class.javaPrimitiveType!!),
        )
        return candidates.firstNotNullOfOrNull { runCatching { type.getMethod(name, *it) }.getOrNull() }
    }

    private fun call(method: Method): Any? {
        val userId = Process.myUid() / 100_000
        val args: Array<Any?> = when (method.parameterTypes.size) {
            // 后台保存/还原不是用户点「粘贴」,不伪造用户操作;隐私栈与框架默认值一样为空。
            7 -> arrayOf("com.android.shell", callerLabel, "", null, userId, 0, false)
            // ShellContext 是默认设备的系统上下文,与原 ClipboardManager 使用的设备一致。
            4 -> arrayOf("com.android.shell", null, userId, 0)
            else -> arrayOf("com.android.shell", null, userId)
        }
        return method.invoke(service, *args)
    }

    private companion object {
        // app_process 里没有 prepareMainLooper,Looper.getMainLooper() 可能返回 null;
        // Binder 线程也没有自己的 Looper。ClipboardManager 需要非空 Handler,
        // 用进程级单例 HandlerThread 兜底,避免 NPE 让剪贴板写入静默恒失败。
        private val shellLooper: Looper by lazy {
            Looper.getMainLooper() ?: HandlerThread("GimiMobileUseClipboard").apply { start() }.looper
        }
    }
}
