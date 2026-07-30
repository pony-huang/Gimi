package github.ponyhuang.gimi.feature.appfunctions

import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionCatalogState
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionSelection
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFunctionsUiMapperTest {
    @Test
    fun `groups functions by application and counts explicit enabled selections`() {
        val first = descriptor("com.notes", "Notes", "create")
        val second = descriptor("com.notes", "Notes", "delete")
        val catalog = AppFunctionCatalogState(
            support = AppFunctionsSupport.AVAILABLE,
            selection = AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(first.key.packageName),
                enabledFunctionKeys = setOf(first.key),
            ),
            functions = listOf(first, second),
        )

        val app = catalog.toAppItems().single()

        assertEquals("com.notes", app.packageName)
        assertEquals(1, app.enabledCount)
        assertEquals(2, app.totalCount)
        assertFalse(app.allEnabled)
    }

    @Test
    fun `unsupported functions never make the application fully enabled`() {
        val supported = descriptor("com.notes", "Notes", "create")
        val unsupported = descriptor("com.notes", "Notes", "share", supported = false)
        val catalog = AppFunctionCatalogState(
            support = AppFunctionsSupport.AVAILABLE,
            selection = AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(supported.key.packageName),
                enabledFunctionKeys = setOf(supported.key, unsupported.key),
            ),
            functions = listOf(supported, unsupported),
        )

        val app = catalog.toAppItems().single()

        assertEquals(1, app.enabledCount)
        assertEquals(1, app.totalCount)
        assertTrue(app.allEnabled)
        assertEquals(1, app.unsupportedCount)
    }

    private fun descriptor(
        packageName: String,
        label: String,
        functionId: String,
        supported: Boolean = true,
    ) = AppFunctionDescriptor(
        key = AppFunctionKey(packageName, functionId),
        appLabel = label,
        appDescription = null,
        description = functionId,
        parameters = emptyList(),
        providerEnabled = true,
        supported = supported,
        unsupportedReason = if (supported) null else "Unsupported",
    )
}
