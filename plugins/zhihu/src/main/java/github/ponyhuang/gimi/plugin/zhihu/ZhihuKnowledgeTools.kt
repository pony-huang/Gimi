package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.pluginapi.PluginJson

/** 知识库列表工具 — 获取当前用户创建或订阅的知识库。 */
internal class ZhihuKnowledgeBasesTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "scope" to stringParam(
                "Filter: all, created, or subscribed; default all",
                enum = listOf("all", "created", "subscribed"),
            ),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            mapOf(RESULT_KEY to PluginJson.toNative(api.knowledgeBases(secret, strArg(args, "scope"))))
        }

    companion object {
        const val NAME: String = "zhihu_knowledge_bases"
        private const val DESCRIPTION: String =
            "List the user's Zhihu knowledge bases. Returns KnowledgeBaseID, Name, Description, Relation (created/subscribed), Visibility, ContentCount, UpdatedAt."
    }
}

/** 知识库内容列表工具 — 分页获取指定知识库中的文档条目。 */
internal class ZhihuKnowledgeItemsTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "knowledge_base_id" to stringParam("Knowledge base ID from zhihu_knowledge_bases"),
            "cursor" to stringParam("Pagination cursor from the previous NextCursor; omit for the first page"),
            "limit" to intParam("Items per page (1-20, default 20)", min = 1, max = 20),
            required = listOf("knowledge_base_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val id = strArg(args, "knowledge_base_id")
                ?: throw IllegalStateException("缺少参数 knowledge_base_id")
            mapOf(
                RESULT_KEY to PluginJson.toNative(
                    api.knowledgeItems(secret, id, strArg(args, "cursor"), intArg(args, "limit", 20)),
                ),
            )
        }

    companion object {
        const val NAME: String = "zhihu_knowledge_items"
        private const val DESCRIPTION: String =
            "List documents (items) in one Zhihu knowledge base. Returns Items, HasMore, NextCursor, Total. " +
                "Reuse the returned KnowledgeBaseID and NextCursor for pagination."
    }
}

/** 知识库检索工具 — RAG 从指定知识库或召回范围检索相关文档片段。 */
internal class ZhihuKnowledgeSearchTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "query" to stringParam("Search query"),
            "knowledge_base_ids" to arrayParam("Knowledge base IDs to restrict the search to"),
            "recall_scopes" to Schema(
                type = Type.ARRAY,
                description = "Recall scopes: personal, subscription, or public",
                items = stringParam("Allowed recall scope", enum = listOf("personal", "subscription", "public")),
            ),
            "limit" to intParam("Max results (1-10, default 10)", min = 1, max = 10),
            required = listOf("query"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val query = strArg(args, "query")
                ?: throw IllegalStateException("缺少参数 query")
            val ids = arrayArg(args, "knowledge_base_ids")
            val scopes = arrayArg(args, "recall_scopes")
            if (ids.isEmpty() && scopes.isEmpty()) {
                throw IllegalStateException("knowledge_base_ids 与 recall_scopes 至少提供一项")
            }
            mapOf(
                RESULT_KEY to PluginJson.toNative(
                    api.knowledgeSearch(secret, query, ids, scopes, intArg(args, "limit", 10)),
                ),
            )
        }

    companion object {
        const val NAME: String = "zhihu_knowledge_search"
        private const val DESCRIPTION: String =
            "Search across the user's Zhihu knowledge bases. Requires a Query and at least one knowledge_base_id or " +
                "recall_scope (personal/subscription/public). Returns matching documents with origin URLs."
    }
}

/** 知识库文件上传工具 — 上传本地文件到知识库（同步解析挂载）。 */
internal class ZhihuKnowledgeUploadTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "file_path" to stringParam("Absolute device filesystem path to the file to upload"),
            "knowledge_base_id" to stringParam("Target knowledge base ID; omit to upload to the default base"),
            required = listOf("file_path"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val path = strArg(args, "file_path")
                ?: throw IllegalStateException("缺少参数 file_path")
            mapOf(
                RESULT_KEY to PluginJson.toNative(
                    api.knowledgeUpload(secret, path, strArg(args, "knowledge_base_id")),
                ),
            )
        }

    companion object {
        const val NAME: String = "zhihu_knowledge_upload"
        private const val DESCRIPTION: String =
            "Upload a local document file (PDF, DOC, DOCX, TXT, MD, etc., max 100MB) to the user's Zhihu knowledge base. " +
                "file_path must be a real absolute device filesystem path. Returns the created knowledge item " +
                "(KnowledgeBaseID, RecallContentID, FileName, Title, Abstract)."
    }
}
