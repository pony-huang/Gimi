package github.ponyhuang.gimi.plugin.v2ex

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * V2EX 工具基类 — 持有 API 客户端，统一 IO 调度与错误处理。
 *
 * 基址经 [V2exPlugin.configure] 写入 [V2exApi.baseUrl]，工具调用时读取当前值，
 * 无需每次执行传参。
 */
internal abstract class V2exTool(
    name: String,
    description: String,
    protected val api: V2exApi,
) : FunctionTool(name = name, description = description) {

    protected suspend fun call(block: suspend () -> Map<String, Any?>): Map<String, Any?> =
        withContext(Dispatchers.IO) {
            runCatching { block() }.getOrElse { mapOf(ERROR_KEY to (it.message ?: "V2EX API 调用失败")) }
        }

    companion object {
        const val ERROR_KEY: String = "error"
    }
}

private fun intArg(args: Map<String, Any?>, key: String, default: Int): Int =
    (args[key] as? Number)?.toInt() ?: default

private fun strArg(args: Map<String, Any?>, key: String): String? =
    (args[key] as? String)?.takeIf(String::isNotBlank)

private fun projectTopics(array: JSONArray, limit: Int): List<Map<String, Any?>> =
    (0 until minOf(array.length(), limit)).map { projectTopicSummary(array.optJSONObject(it)) }

/** 热门主题。 */
internal class V2exHotTopicsTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，最大 30"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val topics = projectTopics(api.hotTopics(), intArg(args, "limit", 10).coerceIn(1, 30))
            mapOf("count" to topics.size, "topics" to topics)
        }

    companion object {
        const val NAME: String = "v2ex_hot_topics"
        private const val DESCRIPTION: String = "获取 V2EX 当前热门主题（标题、节点、作者、回复数、发布时间）。"
    }
}

/** 最新主题。 */
internal class V2exLatestTopicsTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，最大 30"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val topics = projectTopics(api.latestTopics(), intArg(args, "limit", 10).coerceIn(1, 30))
            mapOf("count" to topics.size, "topics" to topics)
        }

    companion object {
        const val NAME: String = "v2ex_latest_topics"
        private const val DESCRIPTION: String = "获取 V2EX 最新创建的主题（标题、节点、作者、回复数、发布时间）。"
    }
}

/** 指定节点下的主题。 */
internal class V2exNodeTopicsTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "node_name" to Schema(type = Type.STRING, description = "节点名（如 python、job、v2ex），来自 v2ex.com/go/<name> 的 slug"),
                "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 20，最大 50"),
            ),
            required = listOf("node_name"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val nodeName = strArg(args, "node_name")
                ?: throw IllegalStateException("缺少参数 node_name")
            val topics = projectTopics(api.nodeTopics(nodeName), intArg(args, "limit", 20).coerceIn(1, 50))
            mapOf("node" to nodeName, "count" to topics.size, "topics" to topics)
        }

    companion object {
        const val NAME: String = "v2ex_node_topics"
        private const val DESCRIPTION: String = "获取 V2EX 指定节点下的主题列表。"
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
                "topic_id" to Schema(type = Type.INTEGER, description = "主题 id（来自列表工具返回的 id 字段）"),
            ),
            required = listOf("topic_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            mapOf("topic" to projectTopicDetail(api.topic(intArg(args, "topic_id", 0))))
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
                "max" to Schema(type = Type.INTEGER, description = "最多返回的回复条数，默认 100"),
            ),
            required = listOf("topic_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            projectReplies(api.replies(intArg(args, "topic_id", 0)), intArg(args, "max", 100).coerceIn(1, 100))
        }

    companion object {
        const val NAME: String = "v2ex_topic_replies"
        private const val DESCRIPTION: String = "获取 V2EX 主题的回复列表；超过上限时只返回前 max 条并附总数。"
    }
}

/** 节点信息。 */
internal class V2exNodeInfoTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "name" to Schema(type = Type.STRING, description = "节点名（如 python、job）"),
            ),
            required = listOf("name"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val name = strArg(args, "name") ?: throw IllegalStateException("缺少参数 name")
            mapOf("node" to projectNode(api.node(name)))
        }

    companion object {
        const val NAME: String = "v2ex_node_info"
        private const val DESCRIPTION: String = "获取 V2EX 节点信息（名称、标题、简介、主题数）。"
    }
}

/** 用户信息。 */
internal class V2exMemberInfoTool(api: V2exApi) : V2exTool(NAME, DESCRIPTION, api) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = mapOf(
                "username" to Schema(type = Type.STRING, description = "用户名"),
            ),
            required = listOf("username"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call {
            val username = strArg(args, "username") ?: throw IllegalStateException("缺少参数 username")
            mapOf("member" to projectMember(api.member(username)))
        }

    companion object {
        const val NAME: String = "v2ex_member_info"
        private const val DESCRIPTION: String = "获取 V2EX 用户信息（用户名、所在地、简介、注册时间）。"
    }
}
