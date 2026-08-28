package github.ponyhuang.gimi.plugin.v2ex

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.pluginapi.PluginJson
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V2EX 工具基类 — 持有 API 客户端，统一 IO 调度、token 校验与错误处理。
 *
 * token 与基址经 [V2exPlugin.configure] 写入 [V2exApi]，工具执行时读取当前值，
 * 无需每次传参。
 */
internal abstract class V2exTool(
    name: String,
    description: String,
    protected val api: V2exApi,
) : FunctionTool(name = name, description = description) {

    protected suspend fun call(block: suspend () -> Map<String, Any?>): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (api.token.isBlank()) {
                    throw IllegalStateException("V2EX 插件未配置 Personal Access Token")
                }
                block()
            }.getOrElse { mapOf(ERROR_KEY to (it.message ?: "V2EX API 调用失败")) }
        }

    companion object {
        const val ERROR_KEY: String = "error"
    }
}

private fun intArg(args: Map<String, Any?>, key: String, default: Int): Int =
    (args[key] as? Number)?.toInt() ?: default

private fun longArg(args: Map<String, Any?>, key: String, default: Long): Long = when (val v = args[key]) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: default
    else -> default
}

private fun strArg(args: Map<String, Any?>, key: String): String? =
    (args[key] as? String)?.takeIf(String::isNotBlank)

/** 写操作统一结果：回传包裹里的 success/message/result，让模型如实转达。 */
private fun writeResult(env: JSONObject): Map<String, Any?> = mapOf(
    "success" to env.optBoolean("success", false),
    "message" to env.optString("message"),
    "result" to PluginJson.toNative(env.opt("result")),
)

/** 最新提醒。 */
internal class V2exNotificationsTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "page" to Schema(type = Type.INTEGER, description = "分页页码，默认 1"),
                "max" to Schema(type = Type.INTEGER, description = "最多返回的提醒条数，默认 50"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val page = intArg(args, "page", 1).coerceAtLeast(1)
            val result = projectNotifications(api.notifications(page), intArg(args, "max", 50).coerceIn(1, 100))
            mapOf("page" to page) + result
        }

    companion object {
        const val NAME: String = "v2ex_notifications"
        private const val DESCRIPTION: String = "获取 V2EX 最新提醒（回复、提及等），按时间倒序。"
    }
}

/** 删除指定提醒。 */
internal class V2exNotificationDeleteTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "notification_id" to Schema(type = Type.INTEGER, description = "要删除的提醒 id"),
            ),
            required = listOf("notification_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = longArg(args, "notification_id", 0)
            if (id <= 0) throw IllegalStateException("缺少参数 notification_id")
            writeResult(api.deleteNotification(id))
        }

    companion object {
        const val NAME: String = "v2ex_notification_delete"
        private const val DESCRIPTION: String = "删除指定的 V2EX 提醒。"
    }
}

/** 自己的 Profile。 */
internal class V2exMeTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT, properties = emptyMap()),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { mapOf("member" to projectMember(api.member())) }

    companion object {
        const val NAME: String = "v2ex_me"
        private const val DESCRIPTION: String = "获取当前账号自己的 Profile。"
    }
}

/** 当前使用的令牌。 */
internal class V2exTokenTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT, properties = emptyMap()),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { mapOf("token" to projectToken(api.tokenInfo())) }

    companion object {
        const val NAME: String = "v2ex_token"
        private const val DESCRIPTION: String = "查看当前使用的 Personal Access Token 信息（scope、过期时间等）。"
    }
}

/** 创建新令牌。 */
internal class V2exTokenCreateTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "scope" to Schema(
                    type = Type.STRING,
                    description = "令牌权限：everything 或 regular（regular 不能继续创建令牌）",
                    enum = listOf("everything", "regular"),
                ),
                "expiration" to Schema(
                    type = Type.INTEGER,
                    description = "有效期秒数：2592000（30 天）/ 5184000（60 天）/ 7776000（90 天）/ 15552000（180 天），默认 2592000",
                    enum = listOf("2592000", "5184000", "7776000", "15552000"),
                ),
            ),
            required = listOf("scope"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val scope = strArg(args, "scope") ?: throw IllegalStateException("缺少参数 scope")
            writeResult(api.createToken(scope, longArg(args, "expiration", 2_592_000L)))
        }

    companion object {
        const val NAME: String = "v2ex_token_create"
        private const val DESCRIPTION: String = "创建新的 Personal Access Token（最多 10 个；regular scope 不能继续建令牌）。"
    }
}

/** 指定节点。 */
internal class V2exNodeTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "node_name" to Schema(type = Type.STRING, description = "节点名（如 python、job），来自 v2ex.com/go/<name> 的 slug"),
            ),
            required = listOf("node_name"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val nodeName = strArg(args, "node_name") ?: throw IllegalStateException("缺少参数 node_name")
            mapOf("node" to projectNode(api.node(nodeName)))
        }

    companion object {
        const val NAME: String = "v2ex_node"
        private const val DESCRIPTION: String = "获取 V2EX 节点信息（名称、标题、简介、主题数）。"
    }
}

/** 节点下的主题。 */
internal class V2exNodeTopicsTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "node_name" to Schema(type = Type.STRING, description = "节点名（如 python、job）"),
                "page" to Schema(type = Type.INTEGER, description = "分页页码，默认 1"),
            ),
            required = listOf("node_name"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val nodeName = strArg(args, "node_name") ?: throw IllegalStateException("缺少参数 node_name")
            val page = intArg(args, "page", 1).coerceAtLeast(1)
            val array = api.nodeTopics(nodeName, page)
            val topics = (0 until array.length()).map { projectTopicSummary(array.optJSONObject(it)) }
            mapOf("node" to nodeName, "page" to page, "count" to topics.size, "topics" to topics)
        }

    companion object {
        const val NAME: String = "v2ex_node_topics"
        private const val DESCRIPTION: String = "获取 V2EX 指定节点下的主题列表（分页；列表不含正文）。"
    }
}

/** 单主题详情。 */
internal class V2exTopicTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "topic_id" to Schema(type = Type.INTEGER, description = "主题 id"),
            ),
            required = listOf("topic_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = longArg(args, "topic_id", 0)
            if (id <= 0) throw IllegalStateException("缺少参数 topic_id")
            mapOf("topic" to projectTopicDetail(api.topic(id)))
        }

    companion object {
        const val NAME: String = "v2ex_topic"
        private const val DESCRIPTION: String = "获取 V2EX 单个主题的详情，含完整正文内容。"
    }
}

/** 主题回复。 */
internal class V2exTopicRepliesTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "topic_id" to Schema(type = Type.INTEGER, description = "主题 id"),
                "page" to Schema(type = Type.INTEGER, description = "分页页码，默认 1"),
                "max" to Schema(type = Type.INTEGER, description = "最多返回的回复条数，默认 100"),
            ),
            required = listOf("topic_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = longArg(args, "topic_id", 0)
            if (id <= 0) throw IllegalStateException("缺少参数 topic_id")
            val page = intArg(args, "page", 1).coerceAtLeast(1)
            mapOf("page" to page) +
                projectReplies(api.replies(id, page), intArg(args, "max", 100).coerceIn(1, 100))
        }

    companion object {
        const val NAME: String = "v2ex_topic_replies"
        private const val DESCRIPTION: String = "获取 V2EX 主题的回复列表（分页）；超过上限时只返回前 max 条并附总数。"
    }
}

/** 置顶自己的主题。 */
internal class V2exTopicSetStickyTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "topic_id" to Schema(type = Type.INTEGER, description = "主题 id（须为自己的主题）"),
                "duration" to Schema(
                    type = Type.STRING,
                    description = "置顶时长：15min / 1hr / 8hr，默认 15min",
                    enum = listOf("15min", "1hr", "8hr"),
                ),
            ),
            required = listOf("topic_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = longArg(args, "topic_id", 0)
            if (id <= 0) throw IllegalStateException("缺少参数 topic_id")
            writeResult(api.setSticky(id, strArg(args, "duration") ?: "15min"))
        }

    companion object {
        const val NAME: String = "v2ex_topic_set_sticky"
        private const val DESCRIPTION: String = "置顶自己的主题（duration: 15min / 1hr / 8hr）。"
    }
}

/** 放置自己的主题到首页。 */
internal class V2exTopicBoostTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "topic_id" to Schema(type = Type.INTEGER, description = "主题 id（须为自己的主题）"),
            ),
            required = listOf("topic_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val id = longArg(args, "topic_id", 0)
            if (id <= 0) throw IllegalStateException("缺少参数 topic_id")
            writeResult(api.boost(id))
        }

    companion object {
        const val NAME: String = "v2ex_topic_boost"
        private const val DESCRIPTION: String = "放置自己的主题到首页（需要 PRO/高持有量等级，费用 100 铜币起）。"
    }
}
