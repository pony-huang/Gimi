package github.ponyhuang.gimi.plugin.zhihu

import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Type
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 新工具的声明 Schema 契约测试：必填字段、类型、枚举与数值边界。 */
class ZhihuToolSchemaTest {

    private val tools = ZhihuPlugin()
        .toolSets()
        .single()
        .let { toolset -> runBlocking { toolset.getTools(null) } }
        .associateBy { it.name }

    private fun declaration(name: String): FunctionDeclaration =
        requireNotNull((tools.getValue(name) as FunctionTool).declaration())

    @Test
    fun pluginExposesFullToolset() {
        assertEquals(
            listOf(
                "zhihu_search",
                "zhihu_global_search",
                "zhihu_hot_list",
                "zhihu_ask",
                "zhihu_quota",
                "zhihu_knowledge_bases",
                "zhihu_knowledge_items",
                "zhihu_knowledge_search",
                "zhihu_knowledge_upload",
                "zhihu_pdf_parse",
                "zhihu_ppt_generate",
                "zhihu_task_status",
            ),
            tools.keys.toList(),
        )
    }

    @Test
    fun quotaSchema() {
        val parameters = declaration("zhihu_quota").parameters
        assertNotNull(parameters?.properties?.get("api_ids"))
        assertEquals(emptyList<String>(), parameters?.required.orEmpty())
    }

    @Test
    fun knowledgeSearchSchema() {
        val parameters = requireNotNull(declaration("zhihu_knowledge_search").parameters)
        assertEquals(listOf("query"), parameters.required)
        assertEquals(Type.ARRAY, parameters.properties?.get("knowledge_base_ids")?.type)
        assertEquals(Type.ARRAY, parameters.properties?.get("recall_scopes")?.type)
        assertEquals(
            listOf("personal", "subscription", "public"),
            parameters.properties?.get("recall_scopes")?.items?.enum,
        )
        assertEquals(10.0, parameters.properties?.get("limit")?.maximum)
    }

    @Test
    fun knowledgeItemsSchema() {
        val parameters = requireNotNull(declaration("zhihu_knowledge_items").parameters)
        assertEquals(listOf("knowledge_base_id"), parameters.required)
        assertEquals(1.0, parameters.properties?.get("limit")?.minimum)
        assertEquals(20.0, parameters.properties?.get("limit")?.maximum)
    }

    @Test
    fun uploadSchema() {
        val parameters = requireNotNull(declaration("zhihu_knowledge_upload").parameters)
        assertEquals(listOf("file_path"), parameters.required)
        assertNotNull(parameters.properties?.get("knowledge_base_id"))
    }

    @Test
    fun pdfParseSchema() {
        val parameters = requireNotNull(declaration("zhihu_pdf_parse").parameters)
        assertEquals(listOf("file_path"), parameters.required)
    }

    @Test
    fun pptGenerateSchema() {
        val parameters = requireNotNull(declaration("zhihu_ppt_generate").parameters)
        assertEquals(listOf("resource_url"), parameters.required)
        assertEquals(6.0, parameters.properties?.get("num_pages")?.minimum)
        assertEquals(21.0, parameters.properties?.get("num_pages")?.maximum)
    }

    @Test
    fun taskStatusSchema() {
        val parameters = requireNotNull(declaration("zhihu_task_status").parameters)
        assertEquals(listOf("task_type", "task_id"), parameters.required)
        assertEquals(listOf("pdf_parse", "ppt_generation"), parameters.properties?.get("task_type")?.enum)
    }
}
