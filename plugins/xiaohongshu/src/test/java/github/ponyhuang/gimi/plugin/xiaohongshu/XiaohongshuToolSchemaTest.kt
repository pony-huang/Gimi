package github.ponyhuang.gimi.plugin.xiaohongshu

import com.google.adk.kt.tools.FunctionTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuToolSchemaTest {

    private val tools = XiaohongshuPlugin(injectedService = object : XiaohongshuService {})
        .toolSets()
        .single()
        .let { toolset -> runBlocking { toolset.getTools(null) } }
        .associateBy { it.name }

    @Test
    fun stringParametersWithoutCandidatesDeclareNoEmptyEnum() {
        // 回归：空 enum（"enum": []）在模型提供方严格校验下不允许任何取值，
        // 会导致 title/content 等参数被清空后调用，必须以 null 表示无候选项。
        for (tool in tools.values) {
            val declaration = requireNotNull((tool as FunctionTool).declaration())
            val properties = declaration.parameters?.properties.orEmpty()
            for ((name, property) in properties) {
                if (property.enum != null) {
                    assertTrue(
                        "工具 ${declaration.name} 参数 $name 声明了空 enum",
                        property.enum.orEmpty().isNotEmpty(),
                    )
                }
            }
        }
        // 有候选项的参数仍保留枚举约束。
        val sortBy = requireNotNull(
            requireNotNull(
                (tools.getValue("search_feeds") as FunctionTool).declaration(),
            ).parameters?.properties?.get("filters")?.properties?.get("sort_by"),
        )
        assertEquals(listOf("综合", "最新", "最多点赞", "最多评论", "最多收藏"), sortBy.enum)
    }

    @Test
    fun searchAndDetailExposeTypedReferenceContracts() {
        val search = requireNotNull((tools.getValue("search_feeds") as FunctionTool).declaration()).parameters
        assertEquals(listOf("keyword"), search?.required)
        assertNotNull(search?.properties?.get("filters")?.properties?.get("sort_by"))

        val detail = requireNotNull((tools.getValue("get_feed_detail") as FunctionTool).declaration()).parameters
        assertEquals(listOf("feed_id", "xsec_token"), detail?.required)
        assertNotNull(detail?.properties?.get("load_all_comments"))
        assertNotNull(detail?.properties?.get("limit"))
    }

    @Test
    fun interactionToolsRequireStableIds() {
        val like = requireNotNull((tools.getValue("like_feed") as FunctionTool).declaration()).parameters
        assertEquals(listOf("feed_id", "xsec_token"), like?.required)

        val reply = requireNotNull((tools.getValue("reply_notification") as FunctionTool).declaration()).parameters
        assertEquals(listOf("comment_id", "content"), reply?.required)
    }
}
