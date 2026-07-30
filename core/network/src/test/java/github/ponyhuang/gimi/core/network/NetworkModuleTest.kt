package github.ponyhuang.gimi.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okhttp3.Cache

class NetworkModuleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun defaultClientHasBoundedTimeoutsAndHttpCache() {
        val cache = Cache(temporaryFolder.newFolder("http"), 50L * 1024L * 1024L)
        val client = NetworkModule.provideOkHttpClient(cache)

        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.callTimeoutMillis)
        assertNotNull(client.cache)
        assertEquals(50L * 1024L * 1024L, client.cache?.maxSize())
        cache.close()
    }
}
