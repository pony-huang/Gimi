package github.ponyhuang.gimi.plugin.v2ex

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class V2exMappersTest {

    @Test
    fun timeAgoFormatsRelativeHumanTimes() {
        val now = 1_600_000_000L

        assertEquals("刚刚", timeAgo(now, now))
        assertEquals("刚刚", timeAgo(now - 30, now))
        assertEquals("5 分钟前", timeAgo(now - 5 * 60, now))
        assertEquals("2 小时前", timeAgo(now - 2 * 3600, now))
        assertEquals("3 天前", timeAgo(now - 3 * 86400, now))
        assertEquals("40 天前", timeAgo(now - 40 * 86400, now))
        assertEquals("2 个月前", timeAgo(now - 2L * 30 * 86400, now))
    }

    @Test
    fun projectTopicSummaryExtractsListFieldsWithoutContent() {
        val summary = projectTopicSummary(topicJson())

        assertEquals(42, summary["id"])
        assertEquals("今天你在听什么歌", summary["title"])
        assertEquals("music", summary["node"])
        assertEquals("Livid", summary["author"])
        assertEquals(12, summary["replies"])
        assertEquals("3 天前", summary["created_human"])
        // 列表投影刻意不含正文，避免灌爆上下文。
        assertNull(summary["content"])
    }

    @Test
    fun projectTopicDetailKeepsFullContent() {
        val detail = projectTopicDetail(topicJson())

        assertEquals(42, detail["id"])
        assertEquals("今天的正文内容", detail["content"])
        assertEquals("music", detail["node"])
    }

    @Test
    fun projectReplyExtractsAuthorContentAndTime() {
        val reply = projectReply(
            JSONObject()
                .put("id", 7)
                .put("content", "回复正文")
                .put("created", System.currentTimeMillis() / 1000 - 2 * 3600)
                .put(
                    "member",
                    JSONObject().put("username", "beta"),
                ),
        )

        assertEquals(7, reply["id"])
        assertEquals("beta", reply["author"])
        assertEquals("回复正文", reply["content"])
        assertEquals("2 小时前", reply["created_human"])
    }

    @Test
    fun projectRepliesCapsShownCountAndReportsTruncation() {
        val array = JSONArray()
            .put(JSONObject().put("id", 1).put("content", "r1"))
            .put(JSONObject().put("id", 2).put("content", "r2"))
            .put(JSONObject().put("id", 3).put("content", "r3"))

        val result = projectReplies(array, max = 2)

        assertEquals(3, result["total"])
        assertEquals(2, result["shown"])
        assertEquals(true, result["truncated"])
        val replies = result["replies"] as List<*>
        assertEquals(2, replies.size)
        assertEquals("r1", (replies[0] as Map<*, *>)["content"])
    }

    @Test
    fun projectRepliesWithoutTruncationWhenUnderMax() {
        val array = JSONArray().put(JSONObject().put("id", 1).put("content", "r1"))

        val result = projectReplies(array, max = 5)

        assertEquals(false, result["truncated"])
        assertEquals(1, result["shown"])
    }

    @Test
    fun projectNodeExtractsNodeFields() {
        val node = projectNode(
            JSONObject()
                .put("id", 12)
                .put("name", "python")
                .put("title", "Python")
                .put("title_alternative", "Python 语言")
                .put("header", "Python 编程语言节点")
                .put("topics", 1234),
        )

        assertEquals("python", node["name"])
        assertEquals("Python 编程语言节点", node["header"])
        assertEquals(1234, node["topics"])
        // 真实 API 的节点对象没有 created 字段，不应投影出误导性的相对时间。
        assertNull(node["created_human"])
    }

    @Test
    fun projectMemberExtractsProfileFields() {
        val member = projectMember(
            JSONObject()
                .put("id", 1)
                .put("username", "Livid")
                .put("location", "深圳")
                .put("tagline", "Make a dent")
                .put("created", System.currentTimeMillis() / 1000 - 100 * 86400),
        )

        assertEquals("Livid", member["username"])
        assertEquals("深圳", member["location"])
        assertEquals("Make a dent", member["tagline"])
        assertEquals("3 个月前", member["created_human"])
    }

    @Test
    fun projectTokenExtractsScopeAndExpiration() {
        val token = projectToken(
            JSONObject()
                .put("id", 1)
                .put("scope", "everything")
                .put("expiration", 2592000)
                .put("created", System.currentTimeMillis() / 1000 - 5 * 86400),
        )

        assertEquals(1, token["id"])
        assertEquals("everything", token["scope"])
        assertEquals(2592000, token["expiration"])
        assertEquals("5 天前", token["created_human"])
    }

    @Test
    fun projectNotificationExtractsIdTypeAndPayload() {
        val notification = projectNotification(
            JSONObject()
                .put("id", 9)
                .put("type", "reply")
                .put("created", System.currentTimeMillis() / 1000 - 3600)
                .put("payload", JSONObject().put("topic_id", 42).put("title", "标题")),
        )

        assertEquals(9, notification["id"])
        assertEquals("reply", notification["type"])
        assertEquals("1 小时前", notification["created_human"])
        val payload = notification["payload"] as Map<*, *>
        assertEquals(42, payload["topic_id"])
    }

    @Test
    fun projectNotificationsCapsAndReportsTruncation() {
        val array = JSONArray()
            .put(JSONObject().put("id", 1))
            .put(JSONObject().put("id", 2))
            .put(JSONObject().put("id", 3))

        val result = projectNotifications(array, max = 2)

        assertEquals(3, result["total"])
        assertEquals(2, result["shown"])
        assertEquals(true, result["truncated"])
        assertEquals(2, (result["notifications"] as List<*>).size)
    }

    @Test
    fun parseEnvelopeReturnsEnvelopeOnSuccess() {
        val envelope = parseEnvelope("""{"success":true,"message":"ok","result":{"id":1}}""")

        assertTrue(envelope.optBoolean("success"))
        assertEquals(1, envelope.optJSONObject("result").optInt("id"))
    }

    @Test
    fun parseEnvelopeAcceptsMissingSuccessFlag() {
        // 个别成功响应可能省略 success 字段；不显式 false 即视为成功。
        val envelope = parseEnvelope("""{"result":[{"id":1}]}""")

        assertEquals(1, envelope.optJSONArray("result").length())
    }

    @Test
    fun parseEnvelopeThrowsWithMessageOnFailure() {
        val e = assertThrows(IllegalStateException::class.java) {
            parseEnvelope("""{"success":false,"message":"Not Found"}""")
        }

        assertTrue(e.message!!.contains("Not Found"))
    }

    @Test
    fun toTopicArrayWrapsSingleTopicObject() {
        val single = JSONObject().put("id", 1).put("title", "t")
        val wrapped = toTopicArray(single)

        assertEquals(1, wrapped.length())
        assertEquals(1, wrapped.optJSONObject(0).optInt("id"))
    }

    @Test
    fun toTopicArrayPassesThroughArrays() {
        val array = JSONArray().put(JSONObject().put("id", 1))
        assertEquals(1, toTopicArray(array).length())
    }

    private fun topicJson(): JSONObject = JSONObject()
        .put("id", 42)
        .put("title", "今天你在听什么歌")
        .put("content", "今天的正文内容")
        .put("created", System.currentTimeMillis() / 1000 - 3 * 86400)
        .put("last_modified", System.currentTimeMillis() / 1000 - 2 * 86400)
        .put("replies", 12)
        .put("node", JSONObject().put("name", "music").put("title", "音乐"))
        .put("member", JSONObject().put("username", "Livid"))
}
