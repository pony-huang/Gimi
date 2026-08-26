package github.ponyhuang.gimi.plugin.spotify.tools

import github.ponyhuang.gimi.plugin.spotify.SpotifyApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sun.misc.Unsafe

class SpotifyToolCatalogTest {

    @Test
    fun catalogOmitsEndpointsRemovedBySpotifyIn2026() {
        val names = searchTools(uninitializedSpotifyApi()).map { it.name }

        assertFalse("Recommendations endpoint was removed", "spotify_recommendations" in names)
        assertFalse("Batch tracks endpoint was removed", "spotify_get_tracks" in names)
        assertFalse("Batch albums endpoint was removed", "spotify_get_albums" in names)
        assertFalse("Batch artists endpoint was removed", "spotify_get_artists" in names)
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

    /** 工具声明不访问 API；跳过 Android TokenStore 构造，仅用于验证真实注册表元数据。 */
    private fun uninitializedSpotifyApi(): SpotifyApi {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        return (field.get(null) as Unsafe).allocateInstance(SpotifyApi::class.java) as SpotifyApi
    }
}
