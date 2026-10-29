package github.ponyhuang.gimi.plugin.netease

import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.FunctionTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import github.ponyhuang.gimi.pluginapi.PluginJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** 固定的工具目录定义；业务路径只能由插件声明，模型不能指定任意接口。 */
internal data class NeteaseToolSpec(
    val name: String,
    val description: String,
    val properties: Map<String, Schema> = emptyMap(),
    val required: List<String> = emptyList(),
    val run: suspend (Map<String, Any?>) -> Any?,
)

/** 工具统一做参数验证和安全错误映射，取消始终传播到 HTTP 请求。 */
internal class NeteaseTool(private val spec: NeteaseToolSpec) : FunctionTool(spec.name, spec.description) {
    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = name,
        description = description,
        parameters = Schema(type = Type.OBJECT, properties = spec.properties, required = spec.required),
    )

    override suspend fun execute(context: ToolContext, args: Map<String, Any?>): Any = withContext(Dispatchers.IO) {
        try {
            spec.required.forEach { require(args[it] != null) { "缺少参数 $it" } }
            mapOf("data" to sanitize(PluginJson.toNative(spec.run(args))))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: NeteaseApiException) {
            mapOf("error" to error.message, "code" to error.code)
        } catch (_: IOException) {
            mapOf("error" to "网易云网络请求失败，请稍后重试")
        } catch (error: IllegalArgumentException) {
            mapOf("error" to error.message)
        } catch (error: IllegalStateException) {
            mapOf("error" to error.message)
        } catch (_: JSONException) {
            mapOf("error" to "网易云响应格式无效")
        }
    }
}

/** 深层过滤意外回显的凭证，登录结果仅返回状态，不把 token 交给模型。 */
private fun sanitize(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries.filterNot { (key, _) ->
        key.toString().lowercase().replace("_", "") in setOf("accesstoken", "refreshtoken", "privatekey", "clientsecret", "sign")
    }.associate { it.key.toString() to sanitize(it.value) }
    is List<*> -> value.map(::sanitize)
    else -> value
}

private fun text(args: Map<String, Any?>, key: String): String = (args[key] as? String)?.trim()
    ?.takeIf(String::isNotEmpty) ?: throw IllegalArgumentException("请填写 $key")

private fun wholeNumber(value: Any?): Long? = try {
    value?.toString()?.toBigDecimalOrNull()?.longValueExact()
} catch (_: ArithmeticException) {
    null
}

// 开放平台返回的资源 ID 是不透明加密字符串，不能转换成网页使用的数字 ID。
private fun id(args: Map<String, Any?>, key: String): String {
    val value = text(args, key)
    require(Regex("[0-9a-fA-F]{32}").matches(value)) { "$key 必须是平台返回的 32 位十六进制加密 ID，不能使用网页数字 ID" }
    return value
}

private fun integer(args: Map<String, Any?>, key: String, default: Int, range: IntRange): Int {
    val value = if (args.containsKey(key)) wholeNumber(args[key]) else default.toLong()
    require(value != null && value >= range.first && value <= range.last) { "$key 超出允许范围 ${range.first}..${range.last}" }
    return value.toInt()
}

internal fun neteaseTools(account: NeteaseAccount): List<BaseTool> {
    val playlistId = "playlist_id" to Schema(type = Type.STRING, description = "平台返回的歌单加密 ID，32 位十六进制字符串")
    val pagination = mapOf(
        "limit" to Schema(type = Type.INTEGER, description = "每页条数，默认 30，范围 1..100"),
        "offset" to Schema(type = Type.INTEGER, description = "偏移量，默认 0"),
    )
    fun page(args: Map<String, Any?>): JSONObject = JSONObject()
        .put("limit", integer(args, "limit", 30, 1..100))
        .put("offset", integer(args, "offset", 0, 0..Int.MAX_VALUE))
    return listOf(
        NeteaseToolSpec("netease_auth_status", "查询网易云本地登录状态，不访问平台、不返回令牌。") { account.status() },
        NeteaseToolSpec("netease_validate_credentials", "验证配置的网易云开放平台 App ID 与私钥。") { account.validate() },
        NeteaseToolSpec("netease_login_anonymous", "匿名登录网易云，只能用于公开歌曲和歌单，不支持用户资料或写入。") { account.anonymousLogin() },
        NeteaseToolSpec("netease_login_qrcode", "生成登录二维码链接；向用户展示 qr_code_url，让用户用网易云客户端扫码确认。300 秒过期。") { account.createQr() },
        NeteaseToolSpec("netease_login_poll", "检查最近生成的二维码登录状态，只检查一次；801 等待扫码、802 等待确认、803 成功、800 失效。") { account.pollQr() },
        NeteaseToolSpec("netease_refresh_login", "使用已保存的 refresh token 刷新登录，不返回令牌。") { account.refresh() },
        NeteaseToolSpec("netease_logout", "清除网易云插件本地登录和待确认二维码，不撤销平台端授权。") { account.logout() },
        NeteaseToolSpec("netease_commands", "获取当前 App ID 获授权的服务端动态命令清单（搜索、歌词、笔记等）。仅查询能力，不执行清单中的命令；返回 301 表示 App ID 未获接口授权，重新登录无法解决。") {
            account.business("/openapi/v1/ncm/cli/manifest", JSONObject()
                .put("cli_version", "0.1.7").put("cached_versions", JSONObject()), post = true, memberOnly = false)
        },
        NeteaseToolSpec("netease_profile", "读取已登录网易云用户资料。") {
            account.business("/openapi/music/basic/user/profile/get/v2")
        },
        NeteaseToolSpec("netease_liked_playlist", "获取当前用户红心收藏歌单。") {
            account.business("/openapi/music/basic/playlist/star/get/v2")
        },
        NeteaseToolSpec("netease_playlists", "分页获取用户创建或收藏的歌单。", pagination + ("kind" to Schema(
            type = Type.STRING, description = "created 创建的歌单；subscribed 收藏的歌单", enum = listOf("created", "subscribed"),
        )), listOf("kind")) { args ->
            val path = when (text(args, "kind")) {
                "created" -> "/openapi/music/basic/playlist/created/get/v2"
                "subscribed" -> "/openapi/music/basic/playlist/subed/get/v2"
                else -> throw IllegalArgumentException("kind 必须是 created 或 subscribed")
            }
            account.business(path, page(args))
        },
        NeteaseToolSpec("netease_playlist_detail", "读取指定歌单详情。", mapOf(playlistId), listOf("playlist_id")) { args ->
            account.business("/openapi/music/basic/playlist/detail/get/v2", JSONObject().put("playlistId", id(args, "playlist_id")), memberOnly = false)
        },
        NeteaseToolSpec("netease_playlist_songs", "分页读取歌单歌曲，结果不是完整歌单时请说明还有更多页。", pagination + playlistId, listOf("playlist_id")) { args ->
            account.business("/openapi/music/basic/playlist/song/list/get/v3", page(args)
                .put("playlistId", id(args, "playlist_id")).put("qualityFlag", false), memberOnly = false)
        },
        NeteaseToolSpec("netease_search", "按关键字分页搜索歌曲、专辑、歌手或歌单。后续工具使用响应中的加密 ID；请说明分页和版权限制。", pagination + mapOf(
            "keyword" to Schema(type = Type.STRING, description = "搜索关键字"),
            "kind" to Schema(type = Type.STRING, description = "搜索类型，默认 song", enum = listOf("song", "album", "artist", "playlist")),
        ), listOf("keyword")) { args ->
            val endpoint = when (if (args.containsKey("kind")) text(args, "kind") else "song") {
                "song" -> "song/get/v3"
                "album" -> "album/get/v2"
                "artist" -> "artists/get/v2"
                "playlist" -> "playlist/get/v2"
                else -> throw IllegalArgumentException("kind 必须是 song、album、artist 或 playlist")
            }
            account.business("/openapi/music/basic/search/$endpoint", page(args)
                .put("keyword", text(args, "keyword")), memberOnly = false)
        },
        NeteaseToolSpec("netease_search_combined", "按关键字综合搜索，保留平台返回的各类型结果。", mapOf(
            "keyword" to Schema(type = Type.STRING, description = "搜索关键字"),
        ), listOf("keyword")) { args ->
            account.business("/openapi/music/basic/complex/search", JSONObject().put("keyword", text(args, "keyword")), memberOnly = false)
        },
        NeteaseToolSpec("netease_search_hot", "获取平台搜索热词。", mapOf(
            "limit" to Schema(type = Type.INTEGER, description = "返回条数，默认 10，范围 1..100"),
        )) { args ->
            account.business("/openapi/music/basic/search/hot/keyword/get/v2", JSONObject()
                .put("limit", integer(args, "limit", 10, 1..100)), memberOnly = false)
        },
        NeteaseToolSpec("netease_search_suggest", "根据输入获取平台搜索提示词。", mapOf(
            "keyword" to Schema(type = Type.STRING, description = "已输入的搜索文字"),
        ), listOf("keyword")) { args ->
            account.business("/openapi/music/basic/search/suggest/keyword/get/v2", JSONObject().put("keyword", text(args, "keyword")), memberOnly = false)
        },
        NeteaseToolSpec("netease_lyrics", "获取歌曲逐行或逐字歌词。逐字歌词仅部分曲库支持；保留 noLyric 等结果，不编造歌词。", mapOf(
            "song_id" to Schema(type = Type.STRING, description = "平台返回的 32 位十六进制歌曲加密 ID"),
            "word_by_word" to Schema(type = Type.BOOLEAN, description = "是否获取逐字歌词，默认 false"),
        ), listOf("song_id")) { args ->
            val wordByWord = if (args.containsKey("word_by_word")) args["word_by_word"] as? Boolean
                ?: throw IllegalArgumentException("word_by_word 必须是布尔值") else false
            val endpoint = if (wordByWord) "word/by/word/get" else "get/v2"
            account.business("/openapi/music/basic/song/lyric/$endpoint", JSONObject().put("songId", id(args, "song_id")), memberOnly = false)
        },
        NeteaseToolSpec("netease_playlist_create", "创建真实账号歌单，仅在用户明确要求时调用，不自动重试。", mapOf(
            "playlist_name" to Schema(type = Type.STRING, description = "新歌单名称"),
        ), listOf("playlist_name")) { args ->
            account.business("/openapi/music/basic/playlist/create", JSONObject().put("playlistName", text(args, "playlist_name")), post = true)
        },
        NeteaseToolSpec("netease_song_like", "添加或取消红心收藏，修改真实账号；仅在用户明确要求时调用，不自动重试。", mapOf(
            "song_id" to Schema(type = Type.STRING, description = "平台返回的 32 位十六进制歌曲加密 ID"),
            "like" to Schema(type = Type.BOOLEAN, description = "true 添加红心，false 取消红心"),
        ), listOf("song_id", "like")) { args ->
            val like = args["like"] as? Boolean ?: throw IllegalArgumentException("like 必须是布尔值")
            account.business("/openapi/music/basic/playlist/song/like/v2", JSONObject()
                .put("songId", id(args, "song_id")).put("isLike", like), post = true)
        },
        NeteaseToolSpec("netease_song_detail", "获取歌曲详情和平台授权的音频 URL；可能为试听或需要 VIP。此工具不会开始播放；动态能力可用 commands 查询。", mapOf(
            "song_id" to Schema(type = Type.STRING, description = "搜索或歌单结果中的歌曲加密 ID，32 位十六进制字符串，不能使用网页数字 ID"),
            "bitrate" to Schema(type = Type.INTEGER, description = "平台码率枚举（单位 kbps），默认 320：128 标准、320 极高、999 无损、1999 Hi-Res"),
            "effects" to Schema(type = Type.STRING, description = "平台音效参数，仅在已知平台支持的值时填写"),
            "level" to Schema(type = Type.STRING, description = "平台音质等级，仅在已知平台支持的值时填写"),
            "immerse_type" to Schema(type = Type.STRING, description = "平台沉浸音效类型，仅在已知平台支持的值时填写"),
            "trial_scene" to Schema(type = Type.STRING, description = "平台试听场景，仅在已知平台支持的值时填写；不改变试听权限"),
        ), listOf("song_id")) { args ->
            val bitrate = integer(args, "bitrate", 320, 128..1999)
            require(bitrate in setOf(128, 320, 999, 1999)) { "bitrate 必须是 128、320、999 或 1999" }
            val biz = JSONObject().put("songId", id(args, "song_id")).put("withUrl", true).put("bitrate", bitrate)
            // 可选音效参数只转发显式输入，不推测平台枚举或绕过试听限制。
            mapOf("effects" to "effects", "level" to "level", "immerse_type" to "immerseType", "trial_scene" to "trialScene")
                .forEach { (argument, field) -> if (args.containsKey(argument)) biz.put(field, text(args, argument)) }
            account.business("/openapi/music/basic/song/detail/get/v2", biz, memberOnly = false)
        },
        NeteaseToolSpec("netease_playlist_add_songs", "将歌曲加入指定歌单，修改真实账号；仅在用户明确要求时调用，不自动重试。", mapOf(
            playlistId,
            "song_ids" to Schema(type = Type.ARRAY, description = "1..100 个平台返回的歌曲加密 ID，32 位十六进制字符串", items = Schema(type = Type.STRING)),
        ), listOf("playlist_id", "song_ids")) { args ->
            val ids = args["song_ids"] as? List<*> ?: throw IllegalArgumentException("song_ids 必须是数组")
            require(ids.size in 1..100) { "song_ids 需要 1..100 个 ID" }
            val songs = ids.map { id(mapOf("song_id" to it), "song_id") }.distinct()
            account.business("/openapi/music/basic/playlist/song/batch/like", JSONObject()
                .put("playlistId", id(args, "playlist_id")).put("songIdList", JSONArray(songs)), post = true)
        },
        NeteaseToolSpec("netease_playlist_remove_songs", "从指定歌单删除歌曲，修改真实账号；仅在用户明确要求时调用，不自动重试。", mapOf(
            playlistId,
            "song_ids" to Schema(type = Type.ARRAY, description = "1..100 个平台返回的歌曲加密 ID，32 位十六进制字符串", items = Schema(type = Type.STRING)),
        ), listOf("playlist_id", "song_ids")) { args ->
            val ids = args["song_ids"] as? List<*> ?: throw IllegalArgumentException("song_ids 必须是数组")
            require(ids.size in 1..100) { "song_ids 需要 1..100 个 ID" }
            val songs = ids.map { id(mapOf("song_id" to it), "song_id") }.distinct()
            account.business("/openapi/music/basic/playlist/song/batch/delete", JSONObject()
                .put("playlistId", id(args, "playlist_id")).put("songIdList", JSONArray(songs)), post = true)
        },
        NeteaseToolSpec("netease_shorten_url", "将网易云歌曲、歌单或专辑分享链接转换为短链。", mapOf(
            "source_url" to Schema(type = Type.STRING, description = "https://music.163.com/ 的分享链接"),
        ), listOf("source_url")) { args ->
            val url = text(args, "source_url")
            val uri = try { java.net.URI(url) } catch (_: java.net.URISyntaxException) {
                throw IllegalArgumentException("请输入网易云 HTTPS 分享链接")
            }
            require(uri.scheme == "https" && uri.host == "music.163.com" && uri.userInfo == null) { "请输入网易云 HTTPS 分享链接" }
            account.business("/openapi/music/basic/common/middle/short/url", JSONObject().put("sourceUrl", url), post = true, memberOnly = false)
        },
    ).map(::NeteaseTool)
}
