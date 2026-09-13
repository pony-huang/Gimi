package github.ponyhuang.gimi.data.appupdate

/**
 * 清洗 GitHub Release body，供应用内更新弹窗展示。
 *
 * GitHub 生成的 release notes 会在 body 末尾追加 "**Full Changelog**: <compare 链接>"，
 * 该行在应用内弹窗里不可点击、无信息量，统一剔除；其余 markdown 内容原样保留，
 * 由弹窗按平铺列表渲染。
 */
internal object ReleaseBodyCleaner {

    // 兼容加粗/未加粗、多余空白等写法，如 "**Full Changelog**: https://..."。
    private val fullChangelogLine =
        Regex("""^\s*\*{0,2}Full Changelog\*{0,2}\s*:.*$""", RegexOption.IGNORE_CASE)

    /** 剔除 Full Changelog 行，压缩连续空行并裁掉首尾空白；null/空白 body 返回空串。 */
    fun clean(body: String?): String {
        if (body.isNullOrBlank()) return ""
        val kept = body.lines().filterNot { fullChangelogLine.containsMatchIn(it) }
        val collapsed = StringBuilder()
        // 起始视为「上一行是空行」，顺带丢弃开头空行。
        var previousBlank = true
        for (line in kept) {
            val blank = line.isBlank()
            if (blank && previousBlank) continue
            collapsed.appendLine(line)
            previousBlank = blank
        }
        return collapsed.toString().trim()
    }
}
