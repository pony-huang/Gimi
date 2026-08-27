package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.ToolContext
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/** 新工具的 execute 参数校验错误路径：缺参/非法值应返回 error 而非抛到外层。 */
class ZhihuToolExecuteTest {

    private val context = mockk<ToolContext>()

    @Test
    fun knowledgeSearch_missingQuery_returnsError() = runTest {
        val tool = ZhihuKnowledgeSearchTool(ZhihuApi()) { "secret" }
        val result = tool.execute(context, emptyMap()) as Map<*, *>
        assertTrue(result["error"]?.toString()?.contains("query") == true)
    }

    @Test
    fun knowledgeSearch_emptyScopes_returnsError() = runTest {
        val tool = ZhihuKnowledgeSearchTool(ZhihuApi()) { "secret" }
        val result = tool.execute(context, mapOf("query" to "q")) as Map<*, *>
        assertTrue(result["error"]?.toString()?.contains("至少提供一项") == true)
    }

    @Test
    fun knowledgeUpload_missingFilePath_returnsError() = runTest {
        val tool = ZhihuKnowledgeUploadTool(ZhihuApi()) { "secret" }
        val result = tool.execute(context, emptyMap()) as Map<*, *>
        assertTrue(result["error"]?.toString()?.contains("file_path") == true)
    }

    @Test
    fun taskStatus_unknownType_returnsError() = runTest {
        val tool = ZhihuTaskStatusTool(ZhihuApi()) { "secret" }
        val result = tool.execute(context, mapOf("task_type" to "bogus", "task_id" to "t")) as Map<*, *>
        assertTrue(result["error"]?.toString()?.contains("task_type") == true)
    }
}
