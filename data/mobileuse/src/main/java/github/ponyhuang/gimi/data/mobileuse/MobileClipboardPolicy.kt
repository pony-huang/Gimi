package github.ponyhuang.gimi.data.mobileuse

/**
 * 剪贴板换出与还原的判断,不碰系统剪贴板,方便单测。
 * 读剪贴板可能被系统悄悄拦下:取到的内容为空,但「有没有内容」「内容描述」显示有。这时分不清「原来是空的」
 * 和「有内容但看不到」,一律当读不到,不能把它当成空的去清空或覆盖。
 */
internal object MobileClipboardPolicy {
    enum class Read {
        /** 确实是空的(内容、有无、描述都说没有)。 */
        Empty,
        /** 读到了内容。 */
        Has,
        /** 有内容但读不到。 */
        Unreadable,
    }

    fun read(clipPresent: Boolean, hasPrimaryClip: Boolean, descriptionPresent: Boolean): Read = when {
        clipPresent -> Read.Has
        hasPrimaryClip || descriptionPresent -> Read.Unreadable
        else -> Read.Empty
    }

    /** 还原前剪贴板此刻的样子。 */
    enum class Now { Ours, Empty, Other, Unreadable }

    enum class Restore {
        /** 把用户原来的放回去。 */
        PutBack,
        /** 原来就是空的,把 Gimi 的清掉。 */
        Clear,
        /** 不动:用户中途复制了别的、或此刻读不到。 */
        Leave,
    }

    /**
     * [priorEmpty] 为 true 表示换出前读到的确实是空的(读不到的情况根本不会换出)。
     * 剪贴板被别人清空了(比如系统定时自动清空)而原来有内容时也放回去:空的剪贴板不是用户新复制的东西,
     * 放回去不会盖掉用户的内容;不放回去用户原来的就丢了。
     */
    fun restore(priorEmpty: Boolean, now: Now): Restore = when (now) {
        Now.Ours -> if (priorEmpty) Restore.Clear else Restore.PutBack
        Now.Empty -> if (priorEmpty) Restore.Leave else Restore.PutBack
        Now.Other, Now.Unreadable -> Restore.Leave
    }
}
