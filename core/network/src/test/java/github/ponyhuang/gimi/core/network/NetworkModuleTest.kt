package github.ponyhuang.gimi.core.network

import github.ponyhuang.gimi.core.storage.AppDirectoryResolver
import github.ponyhuang.gimi.core.storage.StorageArea
import github.ponyhuang.gimi.core.storage.StorageRegistry
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
        val root = temporaryFolder.newFolder("cache")
        val resolver = object : AppDirectoryResolver {
            override fun resolve(spec: github.ponyhuang.gimi.core.storage.ManagedDirectorySpec, create: Boolean) =
                root.resolve(spec.relativePath).apply { if (create) mkdirs() }
        }
        val registry = StorageRegistry(setOf(NetworkStorage.HttpCache), resolver)
        val cache = NetworkModule.provideHttpCache(registry)
        val client = NetworkModule.provideOkHttpClient(cache)

        assertEquals(StorageArea.CACHE, NetworkStorage.HttpCache.area)
        assertEquals("network/http", NetworkStorage.HttpCache.relativePath)
        assertEquals(root.resolve("network/http").canonicalFile, cache.directory.canonicalFile)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertEquals(60_000, client.callTimeoutMillis)
        assertNotNull(client.cache)
        assertEquals(50L * 1024L * 1024L, client.cache?.maxSize())
        cache.close()
    }
}
