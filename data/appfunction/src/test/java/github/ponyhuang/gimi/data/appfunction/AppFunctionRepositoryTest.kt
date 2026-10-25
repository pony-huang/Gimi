package github.ponyhuang.gimi.data.appfunction

import github.ponyhuang.gimi.domain.appfunction.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** 使用权限和持久化一致性是跨应用调用的必要风险边界。 */
class AppFunctionRepositoryTest {
    private class Preferences : AppFunctionPreferences {
        var packages: Set<String> = emptySet()
        var fail = false
        override fun read() = packages
        override fun write(packages: Set<String>) {
            if (fail) error("storage unavailable")
            this.packages = packages
        }
    }
    private class Discovery : AppFunctionDiscovery {
        var result = AppFunctionCatalog()
        var failure: Exception? = null
        override suspend fun discover(): AppFunctionCatalog {
            failure?.let { throw it }
            return result
        }
    }
    private val discovery = Discovery()
    private val preferences = Preferences()
    private fun repository() = DefaultAppFunctionRepository(discovery, preferences)

    @Test fun discoveryDoesNotEnableNewAppsAndSortsNames() = runTest {
        discovery.result = AppFunctionCatalog(apps = listOf(AppFunctionApp("z", "Zulu"), AppFunctionApp("a", "alpha")))
        val repository = repository()
        repository.refresh()
        assertEquals(listOf("a", "z"), repository.catalog.value.apps.map { it.packageName })
        assertTrue(repository.enabledPackages.value.isEmpty())
        val revision = repository.revision.value
        repository.refresh()
        assertEquals(revision, repository.revision.value)
    }

    @Test fun togglePersistsAndSurvivesRediscoveryAndRecreation() = runTest {
        val repository = repository()
        repository.setEnabled("notes", true)
        assertEquals(setOf("notes"), preferences.packages)
        assertEquals(1L, repository.revision.value)
        repository.setEnabled("notes", true)
        assertEquals(1L, repository.revision.value)
        repository.refresh()
        assertEquals(setOf("notes"), repository().enabledPackages.value)
        repository.setEnabled("notes", false)
        assertTrue(preferences.packages.isEmpty())
        assertTrue(repository.enabledPackages.value.isEmpty())
    }

    @Test fun failedSaveDoesNotPublishPermissionOrRevision() = runTest {
        val repository = repository()
        preferences.fail = true
        try { repository.setEnabled("notes", true); fail("must report failed save") }
        catch (_: IllegalStateException) { }
        assertTrue(repository.enabledPackages.value.isEmpty())
        assertEquals(0L, repository.revision.value)
    }

    @Test fun permissionFailureIsDistinctFromEmptyAndRetryRecovers() = runTest {
        val repository = repository()
        discovery.failure = SecurityException()
        repository.refresh()
        assertEquals(AppFunctionAvailability.PERMISSION_REQUIRED, repository.catalog.value.availability)
        discovery.failure = null
        discovery.result = AppFunctionCatalog(apps = listOf(AppFunctionApp("notes", "Notes")))
        repository.refresh()
        assertEquals("notes", repository.catalog.value.apps.single().packageName)
        assertEquals(AppFunctionAvailability.AVAILABLE, repository.catalog.value.availability)
    }

    @Test fun unsupportedAndQueryFailureRemainDistinct() = runTest {
        val repository = repository()
        discovery.result = AppFunctionCatalog(AppFunctionAvailability.UNSUPPORTED)
        repository.refresh()
        assertEquals(AppFunctionAvailability.UNSUPPORTED, repository.catalog.value.availability)
        discovery.failure = IllegalStateException()
        repository.refresh()
        assertEquals(AppFunctionAvailability.FAILED, repository.catalog.value.availability)
    }

    @Test fun cancellationPropagatesAndKeepsThePreviousCatalog() = runTest {
        val repository = repository()
        discovery.failure = CancellationException("cancelled")
        try { repository.refresh(); fail("must propagate cancellation") }
        catch (_: CancellationException) { }
        assertEquals(AppFunctionAvailability.AVAILABLE, repository.catalog.value.availability)
        assertEquals(0L, repository.revision.value)
    }
}
