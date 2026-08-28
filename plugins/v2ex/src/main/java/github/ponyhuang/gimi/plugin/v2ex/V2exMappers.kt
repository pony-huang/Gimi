package github.ponyhuang.gimi.plugin.v2ex

import github.ponyhuang.gimi.pluginapi.PluginJson
import org.json.JSONArray
import org.json.JSONObject

/**
 * 把 V2EX API 2.0 的 org.json 解析结果投影为 ADK 工具可返回的 JSON-native 结构。
 *
 * 列表投影刻意不携带正文（V2EX 的 topic 对象自带全文，直接透传会灌爆 Agent 上下文），
 * 正文只在单主题详情里返回。
 */

/** 相对时间；V2EX 的 created 是秒级 Unix 时间戳。 */
internal fun timeAgo(createdSec: Long, nowSec: Long = System.currentTimeMillis() / 1000): String {
    val diff = (nowSec - createdSec).coerceAtLeast(0)
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60} 分钟前"
        diff < 86400 -> "${diff / 3600} 小时前"
        diff < 60 * 86400 -> "${diff / 86400} 天前"
        else -> "${diff / (86400 * 30)} 个月前"
    }
}

/** 列表条目投影：标题/节点/作者/回复数/时间，不含正文。 */
internal fun projectTopicSummary(topic: JSONObject): Map<String, Any?> {
    val node = topic.optJSONObject("node")
    val member = topic.optJSONObject("member")
    return mapOf(
        "id" to topic.optInt("id"),
        "title" to topic.optString("title"),
        "node" to node?.optString("name"),
        "author" to member?.optString("username"),
        "replies" to topic.optInt("replies"),
        "created" to topic.optLong("created"),
        "created_human" to timeAgo(topic.optLong("created")),
    )
}

/** 单主题详情投影：保留原始正文。 */
internal fun projectTopicDetail(topic: JSONObject): Map<String, Any?> {
    val node = topic.optJSONObject("node")
    val member = topic.optJSONObject("member")
    return mapOf(
        "id" to topic.optInt("id"),
        "title" to topic.optString("title"),
        "content" to topic.optString("content"),
        "node" to node?.optString("name"),
        "author" to member?.optString("username"),
        "replies" to topic.optInt("replies"),
        "created" to topic.optLong("created"),
        "created_human" to timeAgo(topic.optLong("created")),
        "last_modified" to topic.optLong("last_modified"),
    )
}

/** 单条回复投影。 */
internal fun projectReply(reply: JSONObject): Map<String, Any?> {
    val member = reply.optJSONObject("member")
    return mapOf(
        "id" to reply.optInt("id"),
        "author" to member?.optString("username"),
        "content" to reply.optString("content"),
        "created" to reply.optLong("created"),
        "created_human" to timeAgo(reply.optLong("created")),
    )
}

/** 回复列表投影：最多返回 [max] 条，附带总数与截断标记，避免长帖撑爆上下文。 */
internal fun projectReplies(array: JSONArray, max: Int = 100): Map<String, Any?> {
    val total = array.length()
    val shown = minOf(total, max)
    return mapOf(
        "total" to total,
        "shown" to shown,
        "truncated" to (total > shown),
        "replies" to (0 until shown).map { projectReply(array.optJSONObject(it)) },
    )
}

/** 节点信息投影。 */
internal fun projectNode(node: JSONObject): Map<String, Any?> = mapOf(
    "id" to node.optInt("id"),
    "name" to node.optString("name"),
    "title" to node.optString("title"),
    "title_alternative" to node.optString("title_alternative"),
    "header" to node.optString("header"),
    "topics" to node.optInt("topics"),
    "created" to node.optLong("created"),
    "created_human" to timeAgo(node.optLong("created")),
)

/** 自己的 Profile 投影（字段与 v1 member 同构）。 */
internal fun projectMember(member: JSONObject): Map<String, Any?> = mapOf(
    "id" to member.optInt("id"),
    "username" to member.optString("username"),
    "location" to member.optString("location"),
    "tagline" to member.optString("tagline"),
    "bio" to member.optString("bio"),
    "created" to member.optLong("created"),
    "created_human" to timeAgo(member.optLong("created")),
)

/** 令牌信息投影；expiration 原样透传（可能为秒数或时间戳，具体视 API 返回）。 */
internal fun projectToken(token: JSONObject): Map<String, Any?> = mapOf(
    "id" to token.optInt("id"),
    "scope" to token.optString("scope"),
    "expiration" to token.opt("expiration"),
    "created" to token.optLong("created"),
    "created_human" to timeAgo(token.optLong("created")),
)

/** 单条提醒投影；payload 结构不定，原样转 native 交给模型解析。 */
internal fun projectNotification(notification: JSONObject): Map<String, Any?> = mapOf(
    "id" to notification.optInt("id"),
    "type" to notification.optString("type"),
    "created" to notification.optLong("created"),
    "created_human" to timeAgo(notification.optLong("created")),
    "payload" to PluginJson.toNative(notification.opt("payload")),
)

/** 提醒列表投影：最多返回 [max] 条。 */
internal fun projectNotifications(array: JSONArray, max: Int = 50): Map<String, Any?> {
    val total = array.length()
    val shown = minOf(total, max)
    return mapOf(
        "total" to total,
        "shown" to shown,
        "truncated" to (total > shown),
        "notifications" to (0 until shown).map { projectNotification(array.optJSONObject(it)) },
    )
}

/**
 * 解析 API 2.0 响应包裹 `{success, message, result}`。
 *
 * 显式 `success: false` 时抛带 message 的异常（由工具统一转成 error 返回）；
 * 个别成功响应省略 success 字段，故只在显式 false 时判失败。
 */
internal fun parseEnvelope(body: String): JSONObject {
    val json = JSONObject(body)
    if (json.opt("success") == false) {
        throw IllegalStateException("V2EX API 错误: ${json.optString("message")}")
    }
    return json
}

/** V2EX 某些接口 result 为单对象、某些为数组；统一成数组。 */
internal fun toTopicArray(json: Any): JSONArray = when (json) {
    is JSONArray -> json
    is JSONObject -> JSONArray().put(json)
    else -> JSONArray()
}
