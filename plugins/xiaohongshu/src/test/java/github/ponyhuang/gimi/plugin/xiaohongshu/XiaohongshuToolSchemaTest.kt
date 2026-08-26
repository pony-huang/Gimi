package github.ponyhuang.gimi.plugin.xiaohongshu

import com.google.adk.kt.tools.FunctionTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaohongshuToolSchemaTest {

    private val tools = XiaohongshuPlugin(injectedService = object : XiaohongshuService {})
        .tools()
        .associateBy { it.name }

    @Test
    fun publishContentMatchesReferenceArguments() {
        val declaration = requireNotNull((tools.getValue("publish_content") as FunctionTool).declaration())
        val parameters = requireNotNull(declaration.parameters)

        assertEquals(listOf("title", "content", "images"), parameters.required)
        assertTrue(parameters.properties.orEmpty().keys.containsAll(
            listOf("title", "content", "images", "tags", "schedule_at", "is_original", "visibility", "products"),
        ))
        assertNotNull(parameters.properties?.get("images")?.items)
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
