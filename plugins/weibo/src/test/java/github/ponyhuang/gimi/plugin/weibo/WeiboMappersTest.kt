package github.ponyhuang.gimi.plugin.weibo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeiboMappersTest {

    @Test
    fun parseEnvelopeReturnsDataOnSuccess() {
        val data = parseEnvelope("""{"code":0,"message":"ok","data":{"id":"abc","name":"x"}}""")
        val obj = data as JSONObject
        assertEquals("abc", obj.optString("id"))
        assertEquals("x", obj.optString("name"))
    }

    @Test
    fun parseEnvelopeReturnsNullData() {
        val data = parseEnvelope("""{"code":0,"message":"ok","data":null}""")
        assertNull(data)
    }

    @Test
    fun parseEnvelopeThrowsWithFriendlyCodeMessage() {
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope("""{"code":42900,"message":"","data":null}""")
        }
        assertEquals(CODE_RATE_LIMITED, ex.code)
        // 服务端 message 缺省时回退到静态码表，仍能给出中文说明。
        assertTrue(ex.message!!.contains("频率限制"))
    }

    @Test
    fun parseEnvelopeFallsBackToRawMessageOnUnknownCode() {
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope("""{"code":99999,"message":"原始错误","data":null}""")
        }
        assertTrue(ex.message!!.contains("原始错误"))
    }

    @Test
    fun parseEnvelopePrefersServerMessageOverStaticMapping() {
        // 即使服务端返回的 code 在 ERROR_MESSAGES 表里，仍以服务端 message 为准。
        // 否则读接口拿到 40003 之类通用码时被翻译成「ai_model_name 超过 64 字符或 sort_type 参数错误」，
        // 模型会把用户引到无关的 ai_model_name / sort_type 配置上。
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope("""{"code":40003,"message":"category 参数错误：foo","data":null}""")
        }
        assertEquals(40003, ex.code)
        assertTrue(ex.message!!.contains("category 参数错误"))
        assertFalse(ex.message!!.contains("ai_model_name"))
    }

    @Test
    fun parseEnvelopeUsesStaticMappingOnlyWhenServerMessageEmpty() {
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope("""{"code":40100,"message":"","data":null}""")
        }
        assertEquals(CODE_TOKEN_INVALID, ex.code)
        assertTrue(ex.message!!.contains("Token 无效"))
    }

    @Test
    fun projectHotSearchMapsRankWordHotValue() {
        val data = JSONObject(
            """
            {"callTime":"2026-01-01 12:00:00","source":"openclaw",
             "data":[
                {"id":1,"word":"A","num":12345,"cat":"主榜","app_query_link":"sinaweibo://a"},
                {"id":2,"word":"B","num":67890,"flag":1}
             ]}
            """.trimIndent(),
        )
        val projected = projectHotSearch(data, "主榜", max = 10)
        assertEquals("主榜", projected["category"])
        assertEquals("2026-01-01 12:00:00", projected["call_time"])
        assertEquals(2, projected["total"])
        assertEquals(2, projected["shown"])
        assertFalse(projected["truncated"] as Boolean)

        val items = projected["items"] as List<*>
        val first = items[0] as Map<*, *>
        assertEquals(1, first["rank"])
        assertEquals("A", first["word"])
        assertEquals(12345, first["hot_value"])
        assertEquals("主榜", first["category"])
        // 缺 flag / flag_icon 时整个键被丢弃，不出现 null。
        assertNull(first["flag"])
        assertNull(first["flag_icon"])
    }

    @Test
    fun projectHotSearchReportsTruncationWhenOverMax() {
        val items = JSONArray()
        repeat(5) { items.put(JSONObject().put("id", it + 1).put("word", "W$it").put("num", 100)) }
        val data = JSONObject().put("data", items)
        val projected = projectHotSearch(data, "科技榜", max = 3)
        assertEquals(5, projected["total"])
        assertEquals(3, projected["shown"])
        assertTrue(projected["truncated"] as Boolean)
    }

    @Test
    fun projectSearchSurfacesAiStates() {
        val data = JSONObject(
            """
            {"completed":false,"analyzing":true,"noContent":false,"refused":false,
             "msg":"<p>摘要中</p>","msg_format":"html","reference_num":3,"scheme":"",
             "version":"v1","callTime":"now","source":"openclaw"}
            """.trimIndent(),
        )
        val projected = projectSearch(data)
        assertEquals(false, projected["completed"])
        assertEquals(true, projected["analyzing"])
        assertEquals(3, projected["reference_count"])
        assertEquals("<p>摘要中</p>", projected["content"])
    }

    @Test
    fun projectStatusesReturnsRecurseRepost() {
        val data = JSONObject()
                .put("total_number", 2)
                .put("statuses", JSONArray()
                    .put(JSONObject()
                        .put("id", "100")
                        .put("text", "原文")
                        .put("created_at", "今天")
                        .put("user", JSONObject().put("screen_name", "本人"))
                        .put("repost", JSONObject()
                            .put("id", "101")
                            .put("text", "转发自")
                            .put("user", JSONObject().put("screen_name", "他人"))
                        )
                    )
                    .put(JSONObject().put("id", "200").put("text", "第二条")),
                )
        val projected = projectStatuses(data, max = 10)
        assertEquals(2, projected["total"])
        val statuses = projected["statuses"] as List<*>
        val first = statuses[0] as Map<*, *>
        assertEquals("100", first["id"])
        assertEquals("本人", first["author"])
        val repost = first["repost"] as Map<*, *>
        assertEquals("101", repost["id"])
        assertEquals("他人", repost["author"])
    }

    @Test
    fun projectStatusesCapsAndReportsTruncation() {
        val statuses = JSONArray()
        repeat(5) { statuses.put(JSONObject().put("id", "$it").put("text", "t$it")) }
        val data = JSONObject().put("statuses", statuses)
        val projected = projectStatuses(data, max = 2)
        assertEquals(2, projected["shown"])
        assertEquals(true, projected["truncated"])
        assertEquals(2, (projected["statuses"] as List<*>).size)
    }

    @Test
    fun projectCommentsRecursesIntoReplies() {
        val root = JSONObject()
            .put("id", "1")
            .put("text", "一级评论")
            .put("user", JSONObject().put("screen_name", "用户A"))
            .put("comments", JSONArray()
                .put(JSONObject()
                    .put("id", "2")
                    .put("text", "子评论")
                    .put("user", JSONObject().put("screen_name", "用户B"))
                )
            )
        val data = JSONObject()
            .put("total_number", 1)
            .put("comments", JSONArray().put(root))
        val projected = projectComments(data, max = 10)
        val comments = projected["comments"] as List<*>
        val first = comments[0] as Map<*, *>
        assertEquals("1", first["id"])
        val replies = first["replies"] as List<*>
        val reply = replies[0] as Map<*, *>
        assertEquals("2", reply["id"])
        assertEquals("子评论", reply["text"])
    }

    @Test
    fun projectWriteResultUsesNestedSuccessFlag() {
        // 外层 code=0、内层 code 非 0 必须按内层判失败。
        val data = JSONObject()
            .put("code", 40002)
            .put("msg", "参数缺失")
        val projected = projectWriteResult(data)
        assertEquals(false, projected["success"])
        assertEquals("参数缺失", projected["message"])

        val ok = JSONObject()
            .put("code", 0)
            .put("msg", "OK")
            .put("mid", "1234567890")
            .put("comment_id", "9876543210")
            .put("created_at", "now")
            .put("text", "正文")
        val projectedOk = projectWriteResult(ok)
        assertEquals(true, projectedOk["success"])
        assertEquals("1234567890", projectedOk["mid"])
    }

    @Test
    fun projectTopicNamesDropsBlankEntries() {
        val arr = JSONArray()
            .put("动漫").put("  ").put("游戏").put("")
        val projected = projectTopicNames(arr)
        assertEquals(2, projected["count"])
        val topics = projected["topics"] as List<*>
        assertEquals(listOf("动漫", "游戏"), topics)
    }

    @Test
    fun hotSearchCategoriesExposesSevenChineseNames() {
        // 服务端把榜单直接定义为「社会榜 / 体育榜 / …」这些中文字面值，没有 SID 中间层；
        // 映射键值相同，工具层只需要校验白名单。
        assertEquals(7, HOT_SEARCH_CATEGORIES.size)
        assertEquals("主榜", HOT_SEARCH_CATEGORIES["主榜"])
        assertEquals("文娱榜", HOT_SEARCH_CATEGORIES["文娱榜"])
        assertEquals("科技榜", HOT_SEARCH_CATEGORIES["科技榜"])
    }

    @Test
    fun parseEnvelopeFallsBackToPreciseAiModelNameHintWhenServerMessageEmpty() {
        // 40003 服务端真实文案是英文「ai_model_name must not exceed 64 characters」，
        // 但服务端 message 缺省时本地兜底成「ai_model_name 超过 64 字符」（不带
        // 「或 sort_type 参数错误」那种误导性合并）。
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope("""{"code":40003,"message":"","data":null}""")
        }
        assertEquals(40003, ex.code)
        assertTrue(ex.message!!.contains("ai_model_name 超过 64 字符"))
        assertFalse(ex.message!!.contains("sort_type"))
        assertFalse(ex.message!!.contains("未知错误"))
    }

    @Test
    fun parseEnvelopeServerMessageWinsOverStaticMappingFor40003() {
        // 服务端真文案优先透传；本地兜底"超过 64 字符"必须让位给服务端更精确的英文原文。
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope(
                """{"code":40003,"message":"ai_model_name must not exceed 64 characters","data":null}""",
            )
        }
        assertEquals(40003, ex.code)
        assertTrue(ex.message!!.contains("must not exceed 64 characters"))
        assertFalse(ex.message!!.contains("未知错误"))
    }

    @Test
    fun parseEnvelopePreservesServerCategoryHintForInvalidBoardType() {
        // 服务端真实文案示例：40001「无效的榜单类型。可选值：[…]」——必须原样透传，
        // 不能再被我之前的 40001 静态文案「参数缺失：app_id、topic_name、id 或 cid」盖掉。
        val ex = assertThrows(WeiboApiException::class.java) {
            parseEnvelope(
                """{"code":40001,"message":"无效的榜单类型。可选值：[社会榜, 体育榜, 生活榜, 主榜, 科技榜, acg榜, 文娱榜]","data":null}""",
            )
        }
        assertEquals(40001, ex.code)
        assertTrue(ex.message!!.contains("无效的榜单类型"))
        assertTrue(ex.message!!.contains("主榜"))
        assertFalse(ex.message!!.contains("参数缺失"))
    }
}