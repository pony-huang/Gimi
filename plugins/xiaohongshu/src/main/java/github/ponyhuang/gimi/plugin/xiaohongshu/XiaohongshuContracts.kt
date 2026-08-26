package github.ponyhuang.gimi.plugin.xiaohongshu

/** 用户主页要读取的内容分区。 */
internal enum class ProfileTab(val wireValue: String) {
    NOTE("note"),
    FAVORITE("fav"),
    LIKED("liked"),
    ;

    companion object {
        fun parse(value: String?): ProfileTab = when (value.orEmpty().trim().lowercase()) {
            "", "note", "notes", "笔记" -> NOTE
            "fav", "favorite", "favorites", "收藏" -> FAVORITE
            "like", "liked", "点赞" -> LIKED
            else -> throw IllegalArgumentException("未知的主页 tab：$value，可选 note / fav / liked")
        }
    }
}

/**
 * 小红书搜索页的筛选条件。
 *
 * @property sortBy 排序依据。
 * @property noteType 笔记类型。
 * @property publishTime 发布时间范围。
 * @property searchScope 搜索范围。
 * @property location 位置范围。
 */
internal data class SearchFilters(
    val sortBy: String? = null,
    val noteType: String? = null,
    val publishTime: String? = null,
    val searchScope: String? = null,
    val location: String? = null,
) {
    fun validate() {
        requireAllowed("排序依据", sortBy, setOf("综合", "最新", "最多点赞", "最多评论", "最多收藏"))
        requireAllowed("笔记类型", noteType, setOf("不限", "视频", "图文"))
        requireAllowed("发布时间", publishTime, setOf("不限", "一天内", "一周内", "半年内"))
        requireAllowed("搜索范围", searchScope, setOf("不限", "已看过", "未看过", "已关注"))
        requireAllowed("位置距离", location, setOf("不限", "同城", "附近"))
    }

    private fun requireAllowed(label: String, value: String?, allowed: Set<String>) {
        if (!value.isNullOrBlank()) {
            require(value in allowed) { "$label 不支持 $value，可选：${allowed.joinToString("、")}" }
        }
    }
}

/** 小红书插件对页面自动化实现暴露的统一调用边界。 */
internal interface XiaohongshuService {
    suspend fun invoke(operation: String, args: Map<String, Any?>): Any =
        throw IllegalStateException("小红书浏览器服务尚未初始化")
}
