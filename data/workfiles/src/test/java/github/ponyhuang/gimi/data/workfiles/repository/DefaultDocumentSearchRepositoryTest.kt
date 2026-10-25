package github.ponyhuang.gimi.data.workfiles.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultDocumentSearchRepositoryTest {
    private val gateway = mockk<DocumentSearchGateway> {
        every { hasAccess() } returns true
    }
    private val repository = DefaultDocumentSearchRepository(gateway)

    @Test
    fun searchNormalizesQueryAndCapsRequestedResults() = runTest {
        coEvery { gateway.search("plan", 50) } returns emptyList()
        assertEquals(emptyList<Any>(), repository.search(" plan ", 100))
        coVerify(exactly = 1) { gateway.search("plan", 50) }
    }

    @Test
    fun missingAccessPreventsSearchAndUriAuthorization() = runTest {
        every { gateway.hasAccess() } returns false
        assertEquals(emptyList<Any>(), repository.search("plan"))
        assertEquals(false, repository.isAuthorized("content://shared/file"))
        coVerify(exactly = 0) { gateway.search(any(), any()) }
        coVerify(exactly = 0) { gateway.contains(any()) }
    }

    @Test
    fun blankQueryDoesNotTraverseStorage() = runTest {
        assertEquals(emptyList<Any>(), repository.search("  "))
        coVerify(exactly = 0) { gateway.search(any(), any()) }
    }

    @Test
    fun authorizationRechecksAccessAfterRevocation() = runTest {
        coEvery { gateway.contains("content://shared/file") } returns true
        assertEquals(true, repository.isAuthorized("content://shared/file"))
        every { gateway.hasAccess() } returns false
        assertEquals(false, repository.isAuthorized("content://shared/file"))
        coVerify(exactly = 1) { gateway.contains(any()) }
    }

    @Test
    fun searchPropagatesCancellation() = runTest {
        coEvery { gateway.search(any(), any()) } throws CancellationException("cancelled")
        try {
            repository.search("plan")
            org.junit.Assert.fail("Expected cancellation")
        } catch (_: CancellationException) {
            // 取消不能被转换成搜索成功但没有结果。
        }
    }
}
