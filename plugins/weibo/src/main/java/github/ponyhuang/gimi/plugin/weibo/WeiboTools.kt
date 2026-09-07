package github.ponyhuang.gimi.plugin.weibo

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 微博工具基类 — 持有 API 客户端，统一 IO 调度、凭据校验与错误处理。
 *
 * App ID / App Secret 经 [WeiboPlugin.configure] 写入 [WeiboApi]，token 由客户端内部
 * 换取并缓存，工具层不接触凭据本体。
 */
internal abstract class WeiboTool(
    name: String,
    description: String,
    protected val api: WeiboApi,
) : FunctionTool(name = name, description = description) {

    protected suspend fun call(block: suspend () -> Map<String, Any?>): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!api.hasCredentials()) throw IllegalStateException(WeiboApi.MISSING_CREDENTIALS_MESSAGE)
                block()
            }.getOrElse { throwable ->
                // 协程取消原样上抛，不能被当成业务失败吞掉。
                if (throwable is CancellationException) throw throwable
                mapOf(ERROR_KEY to (throwable.message ?: "微博 API 调用失败"))
            }
        }

    companion object {
        const val ERROR_KEY: String = "error"
    }
}

private fun intArg(args: Map<String, Any?>, key: String): Int? = when (val value = args[key]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull()
    else -> null
}

private fun strArg(args: Map<String, Any?>, key: String): String? =
    (args[key] as? String)?.takeIf(String::isNotBlank)

/**
 * 微博 id / 评论 id 一律按字符串取。
 *
 * 微博雪花 id 超过 2^53，模型若以 JSON number 传入会在浮点里丢精度，因此 Number 分支
 * 先归整成 Long 再转字符串。
 */
private fun idArg(args: Map<String, Any?>, key: String): String? = when (val value = args[key]) {
    is String -> value.takeIf(String::isNotBlank)
    is Long, is Int, is Short, is Byte -> value.toString()
    is Number -> value.toLong().toString()
    else -> null
}

/** 微博把开关类参数定义为 0/1；对模型暴露成 boolean，映射后再发出去。 */
private fun flagArg(args: Map<String, Any?>, key: String): Int? = when (val value = args[key]) {
    is Boolean -> if (value) 1 else 0
    is Number -> if (value.toInt() != 0) 1 else 0
    is String -> when (value.lowercase()) {
        "true", "1" -> 1
        "false", "0" -> 0
        else -> null
    }
    else -> null
}

private fun idParam(description: String): Schema =
    Schema(type = Type.STRING, description = "$description（以字符串传，避免大整数精度丢失）")

/** 凭据自检。 */
internal class WeiboCredentialStatusTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT, properties = emptyMap()),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { api.tokenStatus() }

    companion object {
        const val NAME: String = "weibo_credential_status"
        private const val DESCRIPTION: String =
            "自检微博插件凭据是否可用：尝试换取访问令牌并回传有效期，不返回令牌本体。" +
                "仅在其他微博工具报凭据错误、需要确认配置是否生效时使用。"
    }
}

/** 热搜榜。 */
internal class WeiboHotSearchTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "category" to Schema(
                    type = Type.STRING,
                    description = "榜单类型，默认主榜",
                    enum = HOT_SEARCH_CATEGORIES.keys.toList(),
                ),
                "count" to Schema(type = Type.INTEGER, description = "返回条数，1-50，默认 50"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val category = strArg(args, "category") ?: DEFAULT_CATEGORY
            val sid = HOT_SEARCH_CATEGORIES[category] ?: throw IllegalStateException(
                "不支持的榜单：$category，可选：${HOT_SEARCH_CATEGORIES.keys.joinToString("、")}",
            )
            val count = intArg(args, "count")?.coerceIn(1, MAX_COUNT) ?: MAX_COUNT
            projectHotSearch(api.hotSearch(sid, count), category, count)
        }

    companion object {
        const val NAME: String = "weibo_hot_search"
        private const val DESCRIPTION: String =
            "获取微博热搜榜（主榜/文娱榜/社会榜/生活榜/acg榜/科技榜/体育榜），含名次、热搜词、热度值与跳转链接。"
        private const val DEFAULT_CATEGORY: String = "主榜"
        private const val MAX_COUNT: Int = 50
    }
}

/** 微博智搜。 */
internal class WeiboSearchTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "query" to Schema(type = Type.STRING, description = "搜索关键词或问题"),
            ),
            required = listOf("query"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val query = strArg(args, "query") ?: throw IllegalStateException("缺少参数 query")
            projectSearch(api.search(query))
        }

    companion object {
        const val NAME: String = "weibo_search"
        private const val DESCRIPTION: String =
            "微博智搜：按关键词检索全站微博并返回官方生成的 AI 摘要（不是原始微博列表），" +
                "适合了解某话题在微博上的讨论概况。"
    }
}

/** 授权账号自己的微博。 */
internal class WeiboStatusTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "count" to Schema(type = Type.INTEGER, description = "每页条数，1-100，默认 20"),
                "page" to Schema(type = Type.INTEGER, description = "页码，默认 1"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val count = intArg(args, "count")?.coerceIn(1, MAX_COUNT) ?: DEFAULT_COUNT
            val page = intArg(args, "page")?.coerceAtLeast(1)
            projectStatuses(api.userStatus(count, page), count)
        }

    companion object {
        const val NAME: String = "weibo_status"
        private const val DESCRIPTION: String =
            "获取当前授权账号自己发布的微博列表（含正文、发布时间、转评赞数、转发原文），不能查他人微博。"
        private const val DEFAULT_COUNT: Int = 20
        private const val MAX_COUNT: Int = 100
    }
}

/** 可互动的超话列表。 */
internal class WeiboCrowdTopicsTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT, properties = emptyMap()),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { projectTopicNames(api.crowdTopicNames()) }

    companion object {
        const val NAME: String = "weibo_crowd_topics"
        private const val DESCRIPTION: String =
            "列出当前账号可互动的微博超话社区名称；其余超话工具的 topic_name 必须取自本列表。"
    }
}

/** 超话帖子流。 */
internal class WeiboCrowdTimelineTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "topic_name" to Schema(type = Type.STRING, description = "超话社区名，取自 weibo_crowd_topics"),
                "count" to Schema(type = Type.INTEGER, description = "每页条数，1-200，默认 20"),
                "page" to Schema(type = Type.INTEGER, description = "页码，默认 1"),
                "since_id" to idParam("只取该微博 id 之后的内容"),
                "max_id" to idParam("只取该微博 id 之前的内容，用于翻页"),
                "sort_type" to Schema(
                    type = Type.INTEGER,
                    description = "排序：0 按时间，1 按热度",
                    enum = listOf("0", "1"),
                ),
            ),
            required = listOf("topic_name"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val topicName = strArg(args, "topic_name") ?: throw IllegalStateException("缺少参数 topic_name")
            val count = intArg(args, "count")?.coerceIn(1, MAX_COUNT) ?: DEFAULT_COUNT
            val timeline = api.crowdTimeline(
                topicName = topicName,
                page = intArg(args, "page")?.coerceAtLeast(1),
                count = count,
                sinceId = idArg(args, "since_id"),
                maxId = idArg(args, "max_id"),
                sortType = intArg(args, "sort_type")?.takeIf { it == 0 || it == 1 },
            )
            mapOf("topic_name" to topicName) + projectStatuses(timeline, count)
        }

    companion object {
        const val NAME: String = "weibo_crowd_timeline"
        private const val DESCRIPTION: String = "读取指定微博超话社区的帖子流（分页，可按时间或热度排序）。"
        private const val DEFAULT_COUNT: Int = 20
        private const val MAX_COUNT: Int = 200
    }
}

/** 超话发帖。 */
internal class WeiboCrowdPostTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "topic_name" to Schema(type = Type.STRING, description = "超话社区名，取自 weibo_crowd_topics"),
                "status" to Schema(type = Type.STRING, description = "帖子正文"),
            ),
            required = listOf("topic_name", "status"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val topicName = strArg(args, "topic_name") ?: throw IllegalStateException("缺少参数 topic_name")
            val status = strArg(args, "status") ?: throw IllegalStateException("缺少参数 status")
            projectWriteResult(api.crowdPost(topicName, status))
        }

    companion object {
        const val NAME: String = "weibo_crowd_post"
        private const val DESCRIPTION: String =
            "以当前账号在指定超话发帖（真实公开发布，每日上限 10 条）。仅在用户明确要求发帖时调用。"
    }
}

/** 评论微博。 */
internal class WeiboCrowdCommentTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "id" to idParam("要评论的微博 id"),
                "comment" to Schema(type = Type.STRING, description = "评论正文"),
                "comment_ori" to Schema(type = Type.BOOLEAN, description = "转发微博时是否同时评论原微博"),
                "is_repost" to Schema(type = Type.BOOLEAN, description = "是否同时转发该微博"),
            ),
            required = listOf("id", "comment"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = idArg(args, "id") ?: throw IllegalStateException("缺少参数 id")
            val comment = strArg(args, "comment") ?: throw IllegalStateException("缺少参数 comment")
            projectWriteResult(
                api.crowdComment(
                    id = id,
                    comment = comment,
                    commentOri = flagArg(args, "comment_ori"),
                    isRepost = flagArg(args, "is_repost"),
                ),
            )
        }

    companion object {
        const val NAME: String = "weibo_crowd_comment"
        private const val DESCRIPTION: String =
            "以当前账号评论指定微博（真实公开发布，评论与回复每日共 1000 条）。仅在用户明确要求评论时调用。"
    }
}

/** 回复评论。 */
internal class WeiboCrowdCommentReplyTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "cid" to idParam("要回复的评论 id"),
                "id" to idParam("该评论所属的微博 id"),
                "comment" to Schema(type = Type.STRING, description = "回复正文"),
                "without_mention" to Schema(type = Type.BOOLEAN, description = "是否不自动 @ 被回复者"),
                "comment_ori" to Schema(type = Type.BOOLEAN, description = "转发微博时是否同时评论原微博"),
                "is_repost" to Schema(type = Type.BOOLEAN, description = "是否同时转发该微博"),
            ),
            required = listOf("cid", "id", "comment"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            // cid 缺失时微博会把回复降级成普通评论，语义与用户意图不符，故必须显式校验。
            val cid = idArg(args, "cid") ?: throw IllegalStateException("缺少参数 cid")
            val id = idArg(args, "id") ?: throw IllegalStateException("缺少参数 id")
            val comment = strArg(args, "comment") ?: throw IllegalStateException("缺少参数 comment")
            projectWriteResult(
                api.crowdReply(
                    cid = cid,
                    id = id,
                    comment = comment,
                    withoutMention = flagArg(args, "without_mention"),
                    commentOri = flagArg(args, "comment_ori"),
                    isRepost = flagArg(args, "is_repost"),
                ),
            )
        }

    companion object {
        const val NAME: String = "weibo_crowd_comment_reply"
        private const val DESCRIPTION: String =
            "以当前账号回复指定评论（真实公开发布，评论与回复每日共 1000 条）。必须提供 cid，否则会变成普通评论。"
    }
}

/** 一级评论（可带子评论）。 */
internal class WeiboCrowdCommentsTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "id" to idParam("微博 id"),
                "count" to Schema(type = Type.INTEGER, description = "每页一级评论条数，1-200，默认 20"),
                "page" to Schema(type = Type.INTEGER, description = "页码，默认 1"),
                "child_count" to Schema(type = Type.INTEGER, description = "每条一级评论附带的子评论条数"),
                "fetch_child" to Schema(type = Type.BOOLEAN, description = "是否一并返回子评论"),
                "is_asc" to Schema(type = Type.BOOLEAN, description = "是否按时间正序"),
                "trim_user" to Schema(type = Type.BOOLEAN, description = "是否精简用户信息"),
                "since_id" to idParam("只取该评论 id 之后的内容"),
                "max_id" to idParam("只取该评论 id 之前的内容，用于翻页"),
            ),
            required = listOf("id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = idArg(args, "id") ?: throw IllegalStateException("缺少参数 id")
            val count = intArg(args, "count")?.coerceIn(1, MAX_COUNT) ?: DEFAULT_COUNT
            val comments = api.crowdComments(
                id = id,
                page = intArg(args, "page")?.coerceAtLeast(1),
                count = count,
                childCount = intArg(args, "child_count")?.coerceAtLeast(1),
                fetchChild = flagArg(args, "fetch_child"),
                isAsc = flagArg(args, "is_asc"),
                trimUser = flagArg(args, "trim_user"),
                sinceId = idArg(args, "since_id"),
                maxId = idArg(args, "max_id"),
            )
            mapOf("id" to id) + projectComments(comments, count)
        }

    companion object {
        const val NAME: String = "weibo_crowd_comments"
        private const val DESCRIPTION: String = "读取指定微博的一级评论（分页，可一并返回子评论）。"
        private const val DEFAULT_COUNT: Int = 20
        private const val MAX_COUNT: Int = 200
    }
}

/** 子评论。 */
internal class WeiboCrowdChildCommentsTool(api: WeiboApi) : WeiboTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "id" to idParam("一级评论 id"),
                "count" to Schema(type = Type.INTEGER, description = "每页子评论条数，1-200，默认 20"),
                "page" to Schema(type = Type.INTEGER, description = "页码，默认 1"),
                "need_root_comment" to Schema(type = Type.BOOLEAN, description = "是否一并返回所属的一级评论"),
                "is_asc" to Schema(type = Type.BOOLEAN, description = "是否按时间正序"),
                "trim_user" to Schema(type = Type.BOOLEAN, description = "是否精简用户信息"),
                "since_id" to idParam("只取该评论 id 之后的内容"),
                "max_id" to idParam("只取该评论 id 之前的内容，用于翻页"),
            ),
            required = listOf("id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = idArg(args, "id") ?: throw IllegalStateException("缺少参数 id")
            val count = intArg(args, "count")?.coerceIn(1, MAX_COUNT) ?: DEFAULT_COUNT
            val comments = api.crowdChildComments(
                id = id,
                page = intArg(args, "page")?.coerceAtLeast(1),
                count = count,
                needRootComment = flagArg(args, "need_root_comment"),
                isAsc = flagArg(args, "is_asc"),
                trimUser = flagArg(args, "trim_user"),
                sinceId = idArg(args, "since_id"),
                maxId = idArg(args, "max_id"),
            )
            mapOf("root_comment_id" to id) + projectComments(comments, count)
        }

    companion object {
        const val NAME: String = "weibo_crowd_child_comments"
        private const val DESCRIPTION: String = "读取指定一级评论下的子评论（分页）。"
        private const val DEFAULT_COUNT: Int = 20
        private const val MAX_COUNT: Int = 200
    }
}
