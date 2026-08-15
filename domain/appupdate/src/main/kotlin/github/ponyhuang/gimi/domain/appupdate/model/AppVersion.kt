package github.ponyhuang.gimi.domain.appupdate.model

/**
 * 语义化版本号，用于比较应用更新。
 *
 * versionCode 在本地构建固定为 1、CI 注入 run_number，跨版本不可靠，
 * 因此更新检查统一解析 versionName / git tag 走语义化比较。
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    /** 预发布标识，如 "alpha"、"alpha.2"；null 表示正式版。 */
    val preRelease: String?,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
            .takeIf { it != 0 }
            ?.let { return it }
        return comparePreRelease(preRelease, other.preRelease)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    companion object {
        private val PATTERN = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?$""")

        /**
         * 宽容解析版本串：允许 "v" 前缀、缺 minor/patch（补 0）、可选预发布后缀。
         * 无法解析时返回 null。
         */
        fun parse(raw: String): AppVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            return AppVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                preRelease = match.groupValues[4].takeIf { it.isNotEmpty() },
            )
        }

        /**
         * semver 预发布比较：正式版（null）高于任何预发布版；
         * 标识符逐段比较，纯数字段按数值比较且数字段低于非数字段。
         */
        private fun comparePreRelease(left: String?, right: String?): Int = when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            else -> {
                val leftParts = left.split('.')
                val rightParts = right.split('.')
                for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
                    val l = leftParts.getOrNull(index) ?: return -1
                    val r = rightParts.getOrNull(index) ?: return 1
                    val lNum = l.toLongOrNull()
                    val rNum = r.toLongOrNull()
                    val result = when {
                        lNum != null && rNum != null -> lNum.compareTo(rNum)
                        lNum != null -> -1
                        rNum != null -> 1
                        else -> l.compareTo(r)
                    }
                    if (result != 0) return result
                }
                0
            }
        }
    }
}
