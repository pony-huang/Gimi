package github.ponyhuang.gimi.plugin.xiaohongshu

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type

/** 参考项目公开能力对应的工具目录。 */
internal object XiaohongshuToolCatalog {
    val names: List<String> = listOf(
        "check_login_status",
        "get_login_qrcode",
        "delete_cookies",
        "publish_content",
        "list_feeds",
        "search_feeds",
        "get_feed_detail",
        "user_profile",
        "post_comment_to_feed",
        "reply_comment_in_feed",
        "publish_with_video",
        "like_feed",
        "favorite_feed",
        "get_my_profile",
        "get_unread_count",
        "list_notifications",
        "reply_notification",
        "like_notification",
    )

    fun create(service: () -> XiaohongshuService): List<FunctionTool> = names.map { name ->
        XiaohongshuFunctionTool(name, service)
    }
}

/** 把一个小红书能力声明成 ADK 可调用工具，并统一把异常变成页面可读错误。 */
private class XiaohongshuFunctionTool(
    name: String,
    private val service: () -> XiaohongshuService,
) : FunctionTool(name = name, description = descriptionOf(name)) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = schemaOf(name),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        runCatching { service().invoke(name, args) }
            .getOrElse { mapOf(ERROR_KEY to (it.message ?: "小红书操作失败")) }

    companion object {
        const val ERROR_KEY: String = "error"
    }
}

private fun schemaOf(name: String): Schema = when (name) {
    "publish_content" -> objectSchema(
        listOf("title", "content", "images"),
        "title" to string("标题，最多 20 个中文字或英文单词"),
        "content" to string("正文；话题标签请放入 tags"),
        "images" to array("图片的 HTTP/HTTPS URL、本地绝对路径或 content URI"),
        "tags" to array("话题标签，最多 10 个"),
        "schedule_at" to string("可选 ISO8601 定时发布时间，支持 1 小时至 14 天内"),
        "is_original" to boolean("是否声明原创"),
        "visibility" to string(
            "可见范围，默认公开可见",
            listOf("公开可见", "仅自己可见", "仅互关好友可见"),
        ),
        "products" to array("要绑定的商品关键词或商品 ID"),
    )
    "search_feeds" -> objectSchema(
        listOf("keyword"),
        "keyword" to string("搜索关键词"),
        "filters" to objectSchema(
            "sort_by" to string("排序依据", listOf("综合", "最新", "最多点赞", "最多评论", "最多收藏")),
            "note_type" to string("笔记类型", listOf("不限", "视频", "图文")),
            "publish_time" to string("发布时间", listOf("不限", "一天内", "一周内", "半年内")),
            "search_scope" to string("搜索范围", listOf("不限", "已看过", "未看过", "已关注")),
            "location" to string("位置距离", listOf("不限", "同城", "附近")),
        ),
    )
    "get_feed_detail" -> objectSchema(
        listOf("feed_id", "xsec_token"),
        "feed_id" to string("从 Feed 列表获取的笔记 ID"),
        "xsec_token" to string("从 Feed 列表获取的 xsecToken"),
        "load_all_comments" to boolean("是否滚动加载更多评论"),
        "limit" to integer("最多加载的一级评论数，默认 20"),
        "click_more_replies" to boolean("是否展开二级回复"),
        "reply_limit" to integer("跳过回复数超过此值的评论，默认 10"),
        "scroll_speed" to string("滚动速度", listOf("slow", "normal", "fast")),
    )
    "user_profile" -> objectSchema(
        listOf("user_id", "xsec_token"),
        "user_id" to string("小红书用户 ID"),
        "xsec_token" to string("用户主页访问令牌"),
        "tab" to profileTabSchema(),
    )
    "get_my_profile" -> objectSchema("tab" to profileTabSchema())
    "post_comment_to_feed" -> feedSchema("content" to string("评论内容"), extraRequired = listOf("content"))
    "reply_comment_in_feed" -> feedSchema(
        "comment_id" to string("目标评论 ID；与 user_id 至少提供一个"),
        "user_id" to string("目标评论用户 ID；与 comment_id 至少提供一个"),
        "content" to string("回复内容"),
        extraRequired = listOf("content"),
    )
    "like_feed" -> feedSchema("unlike" to boolean("true 表示取消点赞"))
    "favorite_feed" -> feedSchema("unfavorite" to boolean("true 表示取消收藏"))
    "publish_with_video" -> objectSchema(
        listOf("title", "content", "video"),
        "title" to string("标题，最多 20 个中文字或英文单词"),
        "content" to string("正文"),
        "video" to string("本地视频路径或 content URI"),
        "tags" to array("话题标签"),
        "schedule_at" to string("可选 ISO8601 定时发布时间"),
        "visibility" to string("可见范围", listOf("公开可见", "仅自己可见", "仅互关好友可见")),
        "products" to array("商品关键词或商品 ID"),
    )
    "list_notifications" -> objectSchema(
        "tab" to string("通知分区", listOf("mentions", "likes", "connections")),
        "limit" to integer("返回条数上限，默认 20"),
    )
    "reply_notification" -> objectSchema(
        listOf("comment_id", "content"),
        "comment_id" to string("从通知列表获取的评论 ID"),
        "content" to string("回复内容"),
    )
    "like_notification" -> objectSchema(
        listOf("comment_id"),
        "comment_id" to string("从通知列表获取的评论 ID"),
        "unlike" to boolean("true 表示取消点赞"),
    )
    else -> objectSchema()
}

private fun feedSchema(
    vararg extra: Pair<String, Schema>,
    extraRequired: List<String> = emptyList(),
): Schema = objectSchema(
    listOf("feed_id", "xsec_token") + extraRequired,
    *arrayOf(
        "feed_id" to string("从 Feed 列表获取的笔记 ID"),
        "xsec_token" to string("从 Feed 列表获取的 xsecToken"),
        *extra,
    ),
)

private fun objectSchema(vararg properties: Pair<String, Schema>): Schema =
    objectSchema(emptyList(), *properties)

private fun objectSchema(
    required: List<String>,
    vararg properties: Pair<String, Schema>,
): Schema = Schema(type = Type.OBJECT, properties = properties.toMap(), required = required)

private fun string(description: String, values: List<String> = emptyList()): Schema =
    Schema(type = Type.STRING, description = description, enum = values)

private fun boolean(description: String): Schema = Schema(type = Type.BOOLEAN, description = description)

private fun integer(description: String): Schema = Schema(type = Type.INTEGER, description = description)

private fun array(description: String): Schema = Schema(
    type = Type.ARRAY,
    description = description,
    items = Schema(type = Type.STRING),
)

private fun profileTabSchema(): Schema =
    string("主页分区，默认 note", listOf("note", "fav", "liked"))

private fun descriptionOf(name: String): String = when (name) {
    "check_login_status" -> "检查小红书网页登录状态。"
    "get_login_qrcode" -> "获取小红书登录二维码。"
    "delete_cookies" -> "清除小红书网页登录状态。"
    "publish_content" -> "发布小红书图文笔记。"
    "list_feeds" -> "获取小红书首页推荐笔记。"
    "search_feeds" -> "按关键词和筛选条件搜索小红书笔记。"
    "get_feed_detail" -> "读取小红书笔记详情与评论。"
    "user_profile" -> "读取指定小红书用户主页。"
    "post_comment_to_feed" -> "给小红书笔记发表评论。"
    "reply_comment_in_feed" -> "回复小红书笔记中的评论。"
    "publish_with_video" -> "发布小红书视频笔记。"
    "like_feed" -> "点赞或取消点赞小红书笔记。"
    "favorite_feed" -> "收藏或取消收藏小红书笔记。"
    "get_my_profile" -> "读取当前登录用户的小红书主页。"
    "get_unread_count" -> "读取小红书通知未读数。"
    "list_notifications" -> "读取小红书通知列表。"
    "reply_notification" -> "回复小红书通知中的评论。"
    "like_notification" -> "点赞或取消点赞通知中的评论。"
    else -> name
}
