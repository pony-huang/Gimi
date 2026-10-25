package github.ponyhuang.gimi.plugin.netease

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 必需设备参数的边界测试，覆盖供应方失败、缓存和取消。 */
class NeteasePublicIpTest {
    @Test fun fallsBackOnInvalidResponsesAndCachesOnlyValidIp() = runTest {
        var calls = 0
        var now = 0L
        val ip = NeteasePublicIp({
            calls++
            when (calls) {
                1 -> "not-an-ip"
                2 -> throw IOException("offline")
                else -> "{\"ip\":\"203.0.113.7\"}"
            }
        }, clock = { now }, endpoints = listOf("a", "b", "c"))
        assertEquals("203.0.113.7", ip.resolve())
        assertEquals("203.0.113.7", ip.resolve())
        assertEquals(3, calls)
        now = 300_001
        assertEquals("203.0.113.7", ip.resolve())
        assertEquals(4, calls)
    }

    @Test fun doesNotTreatLookupFailureAsValidAddress() = runTest {
        val ip = NeteasePublicIp({ "999.1.1.1" }, endpoints = listOf("a"))
        try { ip.resolve(); fail() } catch (e: NeteaseNetworkException) {
            assertEquals(NeteaseNetworkFailure.PublicIp, e.failure)
        }
    }

    @Test fun cancellationDoesNotStartFallbackLookup() = runTest {
        var calls = 0
        val ip = NeteasePublicIp({ calls++; throw CancellationException() }, endpoints = listOf("a", "b"))
        try { ip.resolve(); fail() } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }
}
