package github.ponyhuang.gimi.data.mobileuse

import java.lang.reflect.Method

/**
 * 让 Gimi 的虚拟屏一直亮着。带 OWN_DISPLAY_GROUP 建的屏(安卓 13 起的可信屏)自成一个显示组,有自己的息屏计时;
 * 注入的输入不算用户操作,放着不管,到了息屏时间这组屏就睡了:画面不再合成、截图不变、点按没反应。
 * 屏在的时候每 [PERIOD_MS] 替它报一次用户操作(比系统允许的最短息屏时间短得多),屏放掉就停。
 * [poke] 报不了(系统没有按屏报的接口)时不再试,屏照常用。
 */
internal class MobileDisplayAwake(
    /** 替这块屏报一次用户操作;报不了返回 false。 */
    private val poke: (Int) -> Boolean,
    /** 马上跑一次、之后每隔 [PERIOD_MS] 跑一次;返回停下它的办法。 */
    private val repeat: (Runnable) -> () -> Unit,
) {
    private val running = HashMap<Int, () -> Unit>()
    @Volatile private var unavailable = false

    fun start(displayId: Int) {
        if (unavailable) return
        val cancel = repeat(Runnable { if (!poke(displayId)) giveUp() })
        synchronized(running) { running.put(displayId, cancel) }?.invoke()
        // 第一次就报不了时 giveUp 可能早于登记跑完:补停一次
        if (unavailable) stopAll()
    }

    fun stop(displayId: Int) {
        synchronized(running) { running.remove(displayId) }?.invoke()
    }

    fun stopAll() {
        synchronized(running) { running.values.toList().also { running.clear() } }.forEach { it() }
    }

    private fun giveUp() {
        unavailable = true
        stopAll()
    }

    companion object {
        const val PERIOD_MS = 4_000L
        // PowerManager.USER_ACTIVITY_EVENT_TOUCH
        const val EVENT_TOUCH = 2
        // Display.DEFAULT_DISPLAY_GROUP(隐藏常量)
        private const val DEFAULT_DISPLAY_GROUP = 0

        /**
         * 要不要替这块屏报:只有它真的自成一组时才报。报到默认组(用户主屏所在的组)会让用户的主屏也一直不灭。
         * [groupId] 读不到为 null,这时按建屏时带没带 [ownGroupFlag] 算。
         */
        fun needed(flags: Int, ownGroupFlag: Int, groupId: Int?): Boolean =
            flags and ownGroupFlag != 0 && groupId != DEFAULT_DISPLAY_GROUP

        /**
         * IPowerManager 里按屏报用户操作的那个重载 `userActivity(int displayId, long time, int event, int flags)`(安卓 12 起);
         * 只有不带屏号的老重载时为 null:它报给默认组,会让用户的主屏不灭,不用。
         */
        fun userActivityMethod(cls: Class<*>): Method? = cls.methods.firstOrNull {
            it.name == "userActivity" && it.parameterTypes.contentEquals(PER_DISPLAY)
        }

        private val PER_DISPLAY = arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
    }
}
