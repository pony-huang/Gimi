package github.ponyhuang.gimi.data.mobileuse

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

/** 拥有 shell 副屏及其保活任务；所有隐藏平台接口集中在此，不向 domain 暴露。 */
internal class MobileVirtualDisplay private constructor(
    val display: VirtualDisplay,
    private val handler: Handler,
) : AutoCloseable {
    private val awake = MobileDisplayAwake(::poke) { task ->
        val cancelled = AtomicBoolean(false)
        val repeating = object : Runnable {
            override fun run() {
                if (closed || cancelled.get()) return
                task.run()
                if (!closed && !cancelled.get()) handler.postDelayed(this, MobileDisplayAwake.PERIOD_MS)
            }
        }
        handler.post(repeating)
        val cancel: () -> Unit = { cancelled.set(true); handler.removeCallbacks(repeating) }
        cancel
    }
    @Volatile private var closed = false
    private var power: Any? = null
    private var userActivity: java.lang.reflect.Method? = null

    private fun start(flags: Int) {
        val id = display.display.displayId
        // 默认显示组不保活，否则会连带让用户主屏无法息屏。
        if (!MobileDisplayAwake.needed(flags, OWN_DISPLAY_GROUP, groupId(id))) return
        try {
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java).invoke(null, "power")
            power = Class.forName("android.os.IPowerManager\$Stub")
                .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            userActivity = MobileDisplayAwake.userActivityMethod(checkNotNull(power).javaClass)
            if (userActivity != null) awake.start(id)
        } catch (failure: ReflectiveOperationException) {
            Log.w(TAG, "Per-display keep-awake unavailable", failure)
        }
    }

    private fun poke(id: Int): Boolean {
        if (closed) return false
        return try {
            userActivity?.invoke(power, id, SystemClock.uptimeMillis(), MobileDisplayAwake.EVENT_TOUCH, 0)
            userActivity != null
        } catch (failure: ReflectiveOperationException) {
            Log.w(TAG, "Per-display keep-awake stopped", failure)
            false
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        awake.stopAll()
        display.release()
    }

    companion object {
        private const val TAG = "GimiVirtualDisplay"

        // DisplayManager.VIRTUAL_DISPLAY_FLAG_* 常量（部分属于平台隐藏常量）
        private const val FLAG_PUBLIC = 1
        private const val FLAG_AUTO_MIRROR = 1 shl 3
        private const val FLAG_ROTATES_WITH_CONTENT = 1 shl 6
        private const val FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 shl 7
        private const val FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 shl 8
        private const val FLAG_TRUSTED = 1 shl 10
        private const val OWN_DISPLAY_GROUP = 1 shl 11
        private const val FLAG_ALWAYS_UNLOCKED = 1 shl 12
        private const val FLAG_TOUCH_FEEDBACK_DISABLED = 1 shl 13
        private const val FLAG_OWN_FOCUS = 1 shl 14
        private const val FLAG_NO_SYSTEM_DECORATIONS = 1 shl 16

        /** 按平台版本启用可信副屏、独立焦点及禁止抢占主屏焦点；移除时销毁副屏任务。 */
        internal fun flags(sdk: Int, trusted: Boolean): Int {
            var flags = FLAG_PUBLIC or FLAG_AUTO_MIRROR or FLAG_ROTATES_WITH_CONTENT or
                FLAG_DESTROY_CONTENT_ON_REMOVAL or FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
            if (sdk >= 33) {
                flags = flags or FLAG_TOUCH_FEEDBACK_DISABLED
                if (trusted) flags = flags or FLAG_TRUSTED or OWN_DISPLAY_GROUP or FLAG_ALWAYS_UNLOCKED
            }
            if (sdk >= 34 && trusted) flags = flags or FLAG_OWN_FOCUS or FLAG_NO_SYSTEM_DECORATIONS
            return flags
        }

        @JvmStatic
        fun create(context: Context, width: Int, height: Int, dpi: Int, surface: Surface, handler: Handler): MobileVirtualDisplay {
            // getSystemService 缓存可能来自 system context，需显式绑定 shell 包身份。
            val manager = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
                .apply { isAccessible = true }.newInstance(context)
            var displayFlags = flags(Build.VERSION.SDK_INT, trusted = true)
            val display = try {
                manager.createVirtualDisplay("GimiMobileUse", width, height, dpi, surface, displayFlags, null, handler)
            } catch (failure: SecurityException) {
                // 只在缺少可信屏权限时降级，渲染/资源错误不能伪装成权限问题重试。
                Log.w(TAG, "Trusted display refused; using shared-focus display", failure)
                displayFlags = flags(Build.VERSION.SDK_INT, trusted = false)
                manager.createVirtualDisplay("GimiMobileUse", width, height, dpi, surface, displayFlags, null, handler)
            } ?: error("Virtual display creation failed")
            val runtime = MobileVirtualDisplay(display, handler)
            try {
                runtime.start(displayFlags)
                return runtime
            } catch (failure: RuntimeException) {
                runtime.close()
                throw failure
            }
        }

        private fun groupId(id: Int): Int? = try {
            val global = Class.forName("android.hardware.display.DisplayManagerGlobal")
                .getMethod("getInstance").invoke(null)
            val info = global.javaClass.getMethod("getDisplayInfo", Int::class.javaPrimitiveType).invoke(global, id)
            info.javaClass.getField("displayGroupId").getInt(info)
        } catch (failure: ReflectiveOperationException) {
            null
        }
    }
}
