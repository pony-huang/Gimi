package github.ponyhuang.gimi

/** 合并升级提示中的插件名称，保留最多 24 个字符（含省略号）。 */
internal fun pluginUpgradeNameSummary(names: List<String>, separator: String): String {
    val summary = names.joinToString(separator)
    if (summary.codePointCount(0, summary.length) <= 24) return summary
    // 按 Unicode 码点截断，避免将插件名称中的 emoji 拆成无效代理字符。
    return summary.substring(0, summary.offsetByCodePoints(0, 23)) + "…"
}
