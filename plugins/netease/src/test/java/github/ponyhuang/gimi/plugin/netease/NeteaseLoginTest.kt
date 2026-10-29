package github.ponyhuang.gimi.plugin.netease

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 轮询与取消是登录业务的必要风险边界，不依赖真实凭证或网易云服务。 */
@OptIn(ExperimentalCoroutinesApi::class)
class NeteaseLoginTest {
    private val account = mockk<NeteaseAccount>()
    private val qr = mapOf<String, Any?>("qr_code_url" to "https://163cn.tv/test")

    @Test fun opensOnceThenPollsAndSavesAuthorizedStatus() = runTest {
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } returnsMany listOf(mapOf("status" to 801), mapOf("status" to 802), mapOf("status" to 803))
        coEvery { account.status() } returns mapOf("authorized" to true, "anonymous" to false)
        val login = NeteaseLogin(account, backgroundScope)
        assertEquals(qr, login.start())
        assertEquals("pending", login.status())
        runCurrent()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals("authorized", login.status())
        coVerify(exactly = 1) { account.createQr() }
        coVerify(exactly = 3) { account.pollQr() }
    }

    @Test fun expiresAfterFiveMinutesWithoutBusyPolling() = runTest {
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } returns mapOf("status" to 801)
        coEvery { account.status() } returns mapOf("authorized" to true, "anonymous" to true)
        val login = NeteaseLogin(account, backgroundScope)
        login.start()
        runCurrent()
        advanceTimeBy(300_001)
        runCurrent()
        assertEquals("expired", login.status())
        coVerify(atMost = 101) { account.pollQr() }
    }

    @Test fun cancellationAndCredentialChangeStopOldPolls() = runTest {
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } returns mapOf("status" to 801)
        coEvery { account.status() } returns mapOf("authorized" to false)
        val login = NeteaseLogin(account, backgroundScope)
        login.start()
        runCurrent()
        login.cancel()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals("unauthorized", login.status())
        coVerify(exactly = 1) { account.pollQr() }
    }

    @Test fun failedReauthorizationKeepsAnExistingValidSessionAuthorized() = runTest {
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } throws IOException("test failure")
        coEvery { account.status() } returns mapOf("authorized" to true, "anonymous" to false)
        val login = NeteaseLogin(account, backgroundScope)
        login.start()
        runCurrent()
        assertEquals("authorized", login.status())
        assertEquals(null, login.failure)
    }

    @Test fun expiredReauthorizationDoesNotHideAnExistingValidSession() = runTest {
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } returns mapOf("status" to 800)
        coEvery { account.status() } returns mapOf("authorized" to true, "anonymous" to false)
        val login = NeteaseLogin(account, backgroundScope)
        login.start()
        runCurrent()
        assertEquals("authorized", login.status())
    }

    @Test fun retryReconcilesAFailedAttemptWithAValidSessionCreatedElsewhere() = runTest {
        var authorized = false
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } throws IOException("test failure")
        coEvery { account.status() } answers { mapOf("authorized" to authorized, "anonymous" to false) }
        val login = NeteaseLogin(account, backgroundScope)
        login.start()
        runCurrent()
        assertEquals("failed", login.status())
        authorized = true
        assertEquals("authorized", login.status())
        assertEquals(null, login.failure)
    }

    @Test fun pollFailureIsVisibleAndDoesNotRetryBusinessRequest() = runTest {
        coEvery { account.createQr() } returns qr
        coEvery { account.pollQr() } throws IOException("test failure")
        coEvery { account.status() } returns mapOf("authorized" to false)
        val login = NeteaseLogin(account, backgroundScope)
        login.start()
        runCurrent()
        assertEquals("failed", login.status())
        coVerify(exactly = 1) { account.pollQr() }
    }
}
