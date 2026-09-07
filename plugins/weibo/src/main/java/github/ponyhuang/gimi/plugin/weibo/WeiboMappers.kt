package github.ponyhuang.gimi.plugin.weibo

import github.ponyhuang.gimi.pluginapi.PluginJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把微博 open 接口的 org.json 响应投影为 ADK 工具可返回的 JSON-native 结构。
 *
 * 投影只保留模型需要引用的字段（正文、作者、时间、互动数、链接），丢掉头像 URL、
 * 客户端标识等噪声；缺失字段直接省略而不是补 `""`／`0`，避免模型把兜底值当真实数据。
 */

/** 微博接口统一响应包裹里表示成功的 code。 */
internal const val CODE_SUCCESS: Int = 0

/** token 无效或已过期；客户端据此清缓存换新 token 重试。 */
internal const val CODE_TOKEN_INVALID: Int = 40100

/** 频率限制：超过每日调用次数上限。 */
internal const val CODE_RATE_LIMITED: Int = 42900

/**
 * 热搜榜单：模型可见的中文榜名（同时也是接口 category 参数的字面值）。
 *
 * 服务端把榜名单写为「社会榜 / 体育榜 / 生活榜 / 主榜 / 科技榜 / acg榜 / 文娱榜」这 7 个
 * 字符串，并没有英文 SID 这一层中间码。早先抄自 openclaw 参考的 `v_openclaw*` 内部码
 * 在实际接口里直接被拒（`code:40001, "无效的榜单类型"`），因此这里改成中文榜名直通。
 */
internal val HOT_SEARCH_CATEGORIES: Map<String, String> = mapOf(
    "主榜" to "主榜",
    "文娱榜" to "文娱榜",
    "社会榜" to "社会榜",
    "生活榜" to "生活榜",
    "acg榜" to "acg榜",
    "科技榜" to "科技榜",
    "体育榜" to "体育榜",
)

/**
 * 微博业务错误码的中文兜底说明。
 *
 * 只留"用户能直接采取行动"的少数几条：Token 失效（让用户查凭据/重连）、频率上限
 * （次日再试）、服务端兜底（稍后重试）。具体参数错误（40001-40007）一律不翻译——
 * 服务端 `message` 总是非空且比静态文案更精确（例如「无效的榜单类型。可选值：…」），硬
 * 翻译只会让模型去检查无关配置项。所有"窄"码的文案已删除。
 */
private val ERROR_MESSAGES: Map<Int, String> = mapOf(
    40003 to "ai_model_name 超过 64 字符",
    40005 to "Token 刷新失败或参数格式错误",
    CODE_TOKEN_INVALID to "Token 无效或已过期",
    CODE_RATE_LIMITED to "频率限制：超过每日调用次数上限，请次日再试",
    50000 to "服务器内部错误，请稍后重试",
    50001 to "操作失败，请检查参数后重试",
)

/**
 * 微博业务错误（HTTP 200 但包裹 code 非 0）。
 *
 * @property code 微博业务错误码。
 */
internal class WeiboApiException(val code: Int, message: String) : IllegalStateException(message)

/**
 * 微博接口 HTTP 层错误（非 2xx）。
 *
 * @property status HTTP 状态码，客户端据此判定是否值得退避重试。
 */
internal class WeiboHttpException(val status: Int, body: String) :
    IllegalStateException("微博接口 HTTP $status: $body")

/**
 * 解析统一响应包裹 `{code, message, data}` 并返回 `data`。
 *
 * code 非 0 时抛 [WeiboApiException]，由工具统一转成 error 结果回给模型。
 *
 * 错误文案优先取服务端实际 `message`：固定码表只剩 Token/频率/服务端兜底这类"用户能
 * 直接采取行动"的少数情况，具体参数错误（40001-40007 等）一律不翻译——服务端
 * `message` 总是非空且更精确（例如「无效的榜单类型。可选值：…」），硬翻译只会把模型
 * 引到无关配置项上去。服务端 `message` 也为空时退到"未知错误"。
 */
internal fun parseEnvelope(body: String): Any? {
    val json = JSONObject(body)
    val code = json.optInt("code", CODE_SUCCESS)
    if (code != CODE_SUCCESS) {
        val serverMessage = json.optString("message").takeIf(String::isNotBlank)
        val detail = serverMessage ?: ERROR_MESSAGES[code] ?: "未知错误"
        throw WeiboApiException(code, "微博接口错误 $code: $detail")
    }
    return if (json.isNull("data")) null else json.opt("data")
}

/** 热搜榜投影；接口把榜单条目放在 `data.data`，`id` 即名次、`num` 即热度值。 */
internal fun projectHotSearch(data: JSONObject, category: String, max: Int): Map<String, Any?> {
    val items = data.optJSONArray("data") ?: JSONArray()
    val total = items.length()
    val shown = minOf(total, max)
    return compact(
        "category" to category,
        "call_time" to data.presence("callTime"),
        "source" to data.presence("source"),
        "total" to total,
        "shown" to shown,
        "truncated" to (total > shown),
        "items" to (0 until shown).map { projectHotSearchItem(items.optJSONObject(it)) },
    )
}

private fun projectHotSearchItem(item: JSONObject?): Map<String, Any?> {
    if (item == null) return emptyMap()
    return compact(
        "rank" to item.presence("id"),
        "word" to item.presence("word"),
        "hot_value" to item.presence("num"),
        "category" to item.presence("cat"),
        "flag" to item.presence("flag"),
        "app_link" to item.presence("app_query_link"),
        "h5_link" to item.presence("h5_query_link"),
        "flag_icon" to item.presence("flag_link"),
    )
}

/**
 * 智搜投影。
 *
 * 智搜返回的是 AI 摘要而非原始微博列表：`analyzing` 为真表示仍在生成、结果不完整，
 * `refused`/`noContent` 表示拒答或无内容——这些状态必须透传，否则模型会把空摘要当结论。
 */
internal fun projectSearch(data: JSONObject): Map<String, Any?> = compact(
    "completed" to data.presence("completed"),
    "analyzing" to data.presence("analyzing"),
    "no_content" to data.presence("noContent"),
    "refused" to data.presence("refused"),
    "content" to data.presence("msg"),
    "content_format" to data.presence("msg_format"),
    "reference_count" to data.presence("reference_num"),
    "scheme" to data.presence("scheme"),
    "version" to data.presence("version"),
    "call_time" to data.presence("callTime"),
    "source" to data.presence("source"),
)

/** 微博列表投影（自己的微博、超话帖子流共用）。 */
internal fun projectStatuses(data: JSONObject, max: Int): Map<String, Any?> {
    val statuses = data.optJSONArray("statuses") ?: JSONArray()
    val listed = statuses.length()
    val shown = minOf(listed, max)
    return compact(
        "total" to (data.presence("total_number") ?: listed),
        "listed" to listed,
        "shown" to shown,
        "truncated" to (listed > shown),
        "next_cursor" to data.presence("next_cursor"),
        "previous_cursor" to data.presence("previous_cursor"),
        "statuses" to (0 until shown).map { projectStatus(statuses.optJSONObject(it)) },
    )
}

/** 单条微博投影；转发原文递归投影，保留引用链。 */
internal fun projectStatus(status: JSONObject?): Map<String, Any?> {
    if (status == null) return emptyMap()
    return compact(
        "id" to status.presence("id"),
        "mid" to status.presence("mid"),
        "text" to status.presence("text"),
        "created_at" to status.presence("created_at"),
        "author" to status.optJSONObject("user")?.presence("screen_name"),
        "has_image" to status.presence("has_image"),
        "pic_num" to status.presence("pic_num"),
        "images" to status.optJSONArray("images")?.let { PluginJson.toNative(it) },
        "comments_count" to status.presence("comments_count"),
        "reposts_count" to status.presence("reposts_count"),
        "attitudes_count" to status.presence("attitudes_count"),
        "repost" to status.optJSONObject("repost")?.let { projectStatus(it) },
    )
}

/** 评论树投影；`comments` 里的子评论递归投影。 */
internal fun projectComments(data: JSONObject, max: Int): Map<String, Any?> {
    val comments = data.optJSONArray("comments") ?: JSONArray()
    val listed = comments.length()
    val shown = minOf(listed, max)
    return compact(
        "total" to (data.presence("total_number") ?: listed),
        "listed" to listed,
        "shown" to shown,
        "truncated" to (listed > shown),
        "next_cursor" to data.presence("next_cursor"),
        "previous_cursor" to data.presence("previous_cursor"),
        "root_comment" to data.optJSONObject("root_comment")?.let { projectComment(it) },
        "comments" to (0 until shown).map { projectComment(comments.optJSONObject(it)) },
    )
}

private fun projectComment(comment: JSONObject?): Map<String, Any?> {
    if (comment == null) return emptyMap()
    val children = comment.optJSONArray("comments")
    return compact(
        "id" to comment.presence("id"),
        "text" to comment.presence("text"),
        "created_at" to comment.presence("created_at"),
        "author" to comment.optJSONObject("user")?.presence("screen_name"),
        "replies" to children?.let { array ->
            (0 until array.length()).map { projectComment(array.optJSONObject(it)) }
        },
    )
}

/**
 * 写操作（发帖/评论/回复）结果投影。
 *
 * 这类响应在 `data` 里又嵌了一层 `code`/`msg`，外层 code 为 0 也不代表内层成功，
 * 因此按内层 code 判定 success，让模型如实转达而不是一律宣布成功。
 */
internal fun projectWriteResult(data: JSONObject): Map<String, Any?> = compact(
    "success" to (data.optInt("code", CODE_SUCCESS) == CODE_SUCCESS),
    "message" to data.presence("msg"),
    "mid" to data.presence("mid"),
    "comment_id" to data.presence("comment_id"),
    "created_at" to data.presence("created_at"),
    "text" to data.presence("text"),
)

/** 超话名列表投影。 */
internal fun projectTopicNames(names: JSONArray): Map<String, Any?> {
    val topics = (0 until names.length()).mapNotNull { names.optString(it).takeIf(String::isNotBlank) }
    return mapOf("count" to topics.size, "topics" to topics)
}

/** 取存在且非 null 的字段值，缺失返回 null 以便 [compact] 丢弃。 */
private fun JSONObject.presence(key: String): Any? = if (has(key) && !isNull(key)) opt(key) else null

/** 丢掉值为 null 的键；`false`/`0`/`""` 是真实数据，保留。 */
private fun compact(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
    pairs.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
