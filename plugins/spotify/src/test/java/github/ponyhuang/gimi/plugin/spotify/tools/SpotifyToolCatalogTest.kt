package github.ponyhuang.gimi.plugin.spotify.tools

import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import github.ponyhuang.gimi.plugin.spotify.SpotifyAuth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class SpotifyToolCatalogTest {

    @Test
    fun catalogOmitsEndpointsRemovedBySpotifyIn2026() {
        val names = allToolNames()

        // 官方已移除/已废弃端点。
        assertFalse("Recommendations endpoint was removed", "spotify_recommendations" in names)
        assertFalse("Batch tracks endpoint was removed", "spotify_get_tracks" in names)
        assertFalse("Batch albums endpoint was removed", "spotify_get_albums" in names)
        assertFalse("Batch artists endpoint was removed", "spotify_get_artists" in names)
    }

    @Test
    fun catalogExposesFullToolset() {
        val names = allToolNames()

        // 完整 35 个工具全部在册。
        val expected = listOf(
            // 认证
            "spotify_login",
            "spotify_auth_status",
            "spotify_logout",
            // 搜索/目录
            "spotify_search",
            "spotify_get_track",
            "spotify_get_album",
            "spotify_get_artist",
            "spotify_get_album_tracks",
            // 个人库/播放状态
            "spotify_now_playing",
            "spotify_get_my_playlists",
            "spotify_get_playlist",
            "spotify_get_playlist_tracks",
            "spotify_get_saved_tracks",
            "spotify_get_recently_played",
            "spotify_get_queue",
            "spotify_get_devices",
            "spotify_get_top_tracks",
            "spotify_get_top_artists",
            "spotify_save_to_library",
            "spotify_remove_from_library",
            "spotify_check_library",
            // 播放控制
            "spotify_play",
            "spotify_pause",
            "spotify_next",
            "spotify_previous",
            "spotify_set_volume",
            "spotify_add_to_queue",
            "spotify_seek",
            "spotify_set_repeat",
            "spotify_set_shuffle",
            // 歌单编辑
            "spotify_create_playlist",
            "spotify_add_tracks_to_playlist",
            "spotify_remove_tracks_from_playlist",
            "spotify_reorder_playlist_items",
            "spotify_update_playlist",
        )
        assertEquals(expected, names)
    }

    @Test
    fun searchContractAvoidsInaccessiblePublicPlaylistDeadEnd() {
        val search = searchTools(uninitializedSpotifyApi()).single { it.name == "spotify_search" }
        val declaration = requireNotNull(search.declaration())
        val supportedTypes = declaration.parameters
            ?.properties
            ?.get("type")
            ?.enum
            .orEmpty()

        assertEquals(listOf("track", "album", "artist"), supportedTypes)
        assertTrue(declaration.description.contains("concrete", ignoreCase = true))
        assertTrue(declaration.description.contains("spotify_get_top_tracks"))
    }

    @Test
    fun schemaAlignsWithOfficialParameters() {
        val api = uninitializedSpotifyApi()

        // spotify_play：官方 body 支持 position_ms（毫秒定位）。
        val play = playbackTools(api).single { it.name == "spotify_play" }
        val playParams = requireNotNull(play.declaration()).parameters
        assertNotNull(playParams?.properties?.get("position_ms"))

        // 分页 limit 默认 50、最大 50（列表/搜索工具统一）。
        val playlistTracks = libraryTools(api).single { it.name == "spotify_get_playlist_tracks" }
        val limit = requireNotNull(playlistTracks.declaration()).parameters?.properties?.get("limit")
        assertEquals(50.0, limit?.maximum)
        assertTrue(limit?.description?.contains("default 50") == true)

        val search = searchTools(api).single { it.name == "spotify_search" }
        val searchLimit = requireNotNull(search.declaration()).parameters?.properties?.get("limit")
        // Spotify 的 /search 接口 limit 上限是 10（>10 返回 400），不是 50。
        assertEquals(10.0, searchLimit?.maximum)
        assertTrue(searchLimit?.description?.contains("default 10") == true)

        // search 不再默认塞 market=from_token（Spotify 会 403），改提供可选 market 入参。
        assertNotNull(requireNotNull(search.declaration()).parameters?.properties?.get("market"))

        // spotify_create_playlist：官方 body 支持 collaborative。
        val create = playlistTools(api).single { it.name == "spotify_create_playlist" }
        val createParams = requireNotNull(create.declaration()).parameters
        assertNotNull(createParams?.properties?.get("collaborative"))

        // 关键回归：Spotify /search 响应里 type 用的是复数 key (tracks/albums/artists)，
        // 早前用单数 type 直接查导致 items 永远是空数组。
        assertEquals("tracks", SEARCH_RESPONSE_KEY["track"])
        assertEquals("albums", SEARCH_RESPONSE_KEY["album"])
        assertEquals("artists", SEARCH_RESPONSE_KEY["artist"])

        // spotify_set_repeat：enum 覆盖 off/context/track。
        val repeat = playbackTools(api).single { it.name == "spotify_set_repeat" }
        val repeatState = requireNotNull(repeat.declaration()).parameters?.properties?.get("state")
        assertEquals(listOf("off", "context", "track"), repeatState?.enum)

        // 库工具走 /me/library 路径，最多 40 个 URI。
        val save = libraryTools(api).single { it.name == "spotify_save_to_library" }
        val saveIds = requireNotNull(save.declaration()).parameters?.properties?.get("ids")
        assertEquals(40L, saveIds?.maxItems)
    }

    private fun allToolNames(): List<String> {
        val api = uninitializedSpotifyApi()
        val auth = uninitializedSpotifyAuth()
        return buildList {
            addAll(authTools(auth, { "" }, { "" }, { "" }) { null }.map { it.name })
            addAll(searchTools(api).map { it.name })
            addAll(libraryTools(api).map { it.name })
            addAll(playbackTools(api).map { it.name })
            addAll(playlistTools(api).map { it.name })
        }
    }

    /** 工具声明不访问 API；跳过 Android TokenStore 构造，仅用于验证真实注册表元数据。 */
    private fun uninitializedSpotifyApi(): SpotifyApi {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(SpotifyApi::class.java) as SpotifyApi
    }

    private fun uninitializedSpotifyAuth(): SpotifyAuth {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(SpotifyAuth::class.java) as SpotifyAuth
    }
}
