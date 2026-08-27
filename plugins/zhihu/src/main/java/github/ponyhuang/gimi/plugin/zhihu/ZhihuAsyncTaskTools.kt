package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import github.ponyhuang.gimi.pluginapi.PluginJson

/** PDF 解析任务创建工具 — 上传本地 PDF 并建异步解析任务，返回 task_id。 */
internal class ZhihuPdfParseTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "file_path" to stringParam("Absolute device filesystem path to the PDF file"),
            required = listOf("file_path"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val path = strArg(args, "file_path")
                ?: throw IllegalStateException("缺少参数 file_path")
            val fileId = api.pdfUpload(secret, path).optString("file_id").takeIf(String::isNotBlank)
                ?: throw IllegalStateException("PDF 上传失败：未返回 file_id")
            mapOf(RESULT_KEY to PluginJson.toNative(api.createPdfTask(secret, fileId)))
        }

    companion object {
        const val NAME: String = "zhihu_pdf_parse"
        private const val DESCRIPTION: String =
            "Parse a local PDF file into text and a summary via a Zhihu async task. Uploads the file and creates the " +
                "parse task, returning task_id and initial task_status. Do NOT block; poll with zhihu_task_status " +
                "(task_type=pdf_parse) until task_status=succeeded, then read result.url/summary."
    }
}

/** PPT 生成任务创建工具 — 用知乎回答/文章链接建异步生成任务，返回 task_id。 */
internal class ZhihuPptGenerateTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "resource_url" to stringParam("A Zhihu answer or article URL"),
            "num_pages" to intParam("Number of slides (6-21, default 10)", min = 6, max = 21),
            required = listOf("resource_url"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val url = strArg(args, "resource_url")
                ?: throw IllegalStateException("缺少参数 resource_url")
            val pages = intArg(args, "num_pages", 10).coerceIn(6, 21)
            mapOf(RESULT_KEY to PluginJson.toNative(api.createPptTask(secret, url, pages)))
        }

    companion object {
        const val NAME: String = "zhihu_ppt_generate"
        private const val DESCRIPTION: String =
            "Generate a PPT from a Zhihu answer or article URL. Creates a ppt-generation async task and returns " +
                "task_id + initial task_status. Do NOT block; poll with zhihu_task_status (task_type=ppt_generation) " +
                "until task_status=succeeded, then open result.url."
    }
}

/** 异步任务状态轮询工具 — 查询 PDF 解析 / PPT 生成任务的进度与结果。 */
internal class ZhihuTaskStatusTool(api: ZhihuApi, secretProvider: () -> String) :
    ZhihuTool(NAME, DESCRIPTION, api, secretProvider) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = objectSchema(
            "task_type" to stringParam("Task kind", enum = listOf("pdf_parse", "ppt_generation")),
            "task_id" to stringParam("Task ID returned by zhihu_pdf_parse or zhihu_ppt_generate"),
            required = listOf("task_type", "task_id"),
        ),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any =
        call { secret ->
            val kind = when (strArg(args, "task_type")) {
                "pdf_parse" -> ZhihuTaskKind.PdfParse
                "ppt_generation" -> ZhihuTaskKind.PptGeneration
                else -> throw IllegalStateException("未知 task_type，应为 pdf_parse 或 ppt_generation")
            }
            val id = strArg(args, "task_id")
                ?: throw IllegalStateException("缺少参数 task_id")
            mapOf(RESULT_KEY to PluginJson.toNative(api.pollTask(secret, kind, id)))
        }

    companion object {
        const val NAME: String = "zhihu_task_status"
        private const val DESCRIPTION: String =
            "Poll the status of an async Zhihu task (PDF parse or PPT generation). Returns task_id, task_status " +
                "(pending/running/succeeded/failed), progress (0..1), and result (url/summary/expires_at_ms) or error when complete."
    }
}
