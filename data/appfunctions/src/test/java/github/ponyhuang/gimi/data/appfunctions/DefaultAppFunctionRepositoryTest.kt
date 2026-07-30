package github.ponyhuang.gimi.data.appfunctions

import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionDescriptor
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionExecutionResult
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionKey
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionSelection
import github.ponyhuang.gimi.domain.appfunctions.model.AppFunctionsSupport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppFunctionRepositoryTest {

    @Test
    fun discoveryDoesNotStartUntilUserEnablesExperimentalFeature() = runTest {
        val gateway = FakeGateway()
        val repository = DefaultAppFunctionRepository(
            gateway = gateway,
            selectionStore = FakeSelectionStore(),
            scope = backgroundScope,
        )

        runCurrent()
        assertEquals(0, gateway.observationCount)

        assertTrue(repository.setFeatureEnabled(true))
        runCurrent()

        assertEquals(1, gateway.observationCount)
        assertTrue(repository.state.value.selection.featureEnabled)
    }

    @Test
    fun unsupportedOrUnauthorizedDeviceRejectsManualEnable() = runTest {
        val gateway = FakeGateway(support = AppFunctionsSupport.MISSING_SYSTEM_PERMISSION)
        val repository = DefaultAppFunctionRepository(
            gateway = gateway,
            selectionStore = FakeSelectionStore(),
            scope = backgroundScope,
        )

        assertFalse(repository.setFeatureEnabled(true))
        runCurrent()

        assertFalse(repository.state.value.selection.featureEnabled)
        assertEquals(0, gateway.observationCount)
    }

    @Test
    fun newlyDiscoveredFunctionStaysDisabled() = runTest {
        val gateway = FakeGateway()
        val store = FakeSelectionStore(
            AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(FIRST_KEY.packageName),
                enabledFunctionKeys = setOf(FIRST_KEY),
            ),
        )
        val repository = DefaultAppFunctionRepository(
            gateway = gateway,
            selectionStore = store,
            scope = backgroundScope,
        )

        runCurrent()

        assertEquals(listOf(FIRST_KEY), repository.state.value.enabledFunctions.map { it.key })
        assertFalse(repository.state.value.selection.isEnabled(SECOND_KEY))
    }

    @Test
    fun disablingStopsDiscoveryAndPreservesExplicitFunctionSelection() = runTest {
        val gateway = FakeGateway()
        val store = FakeSelectionStore(
            AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(FIRST_KEY.packageName),
                enabledFunctionKeys = setOf(FIRST_KEY),
            ),
        )
        val repository = DefaultAppFunctionRepository(
            gateway = gateway,
            selectionStore = store,
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(1, gateway.activeObservationCount)

        assertTrue(repository.setFeatureEnabled(false))
        runCurrent()

        assertEquals(0, gateway.activeObservationCount)
        assertTrue(repository.state.value.functions.isEmpty())
        assertEquals(setOf(FIRST_KEY), store.selection.value.enabledFunctionKeys)
    }

    @Test
    fun providerDisableOrFunctionRemovalImmediatelyRemovesAgentAvailability() = runTest {
        val gateway = FakeGateway()
        val store = FakeSelectionStore(
            AppFunctionSelection(
                featureEnabled = true,
                enabledPackageNames = setOf(FIRST_KEY.packageName),
                enabledFunctionKeys = setOf(FIRST_KEY),
            ),
        )
        val repository = DefaultAppFunctionRepository(
            gateway = gateway,
            selectionStore = store,
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(listOf(FIRST_KEY), repository.state.value.enabledFunctions.map { it.key })

        gateway.catalog.value = listOf(descriptor(FIRST_KEY).copy(providerEnabled = false))
        runCurrent()
        assertTrue(repository.state.value.enabledFunctions.isEmpty())

        gateway.catalog.value = emptyList()
        runCurrent()
        assertTrue(repository.state.value.functions.isEmpty())
    }

    private class FakeGateway(
        override val support: AppFunctionsSupport = AppFunctionsSupport.AVAILABLE,
    ) : AppFunctionGateway {
        var observationCount: Int = 0
        var activeObservationCount: Int = 0
        val catalog = MutableStateFlow(
            listOf(descriptor(FIRST_KEY), descriptor(SECOND_KEY)),
        )

        override fun observeFunctions(): Flow<List<AppFunctionDescriptor>> = flow {
            observationCount += 1
            activeObservationCount += 1
            try {
                emitAll(catalog)
            } finally {
                activeObservationCount -= 1
            }
        }

        override suspend fun execute(
            key: AppFunctionKey,
            arguments: Map<String, Any>,
        ): AppFunctionExecutionResult = AppFunctionExecutionResult.Success(emptyMap<String, Any>())
    }

    private class FakeSelectionStore(
        initial: AppFunctionSelection = AppFunctionSelection(),
    ) : AppFunctionSelectionStore {
        private val mutableSelection = MutableStateFlow(initial)
        override val selection: StateFlow<AppFunctionSelection> = mutableSelection

        override fun update(selection: AppFunctionSelection) {
            mutableSelection.value = selection
        }
    }

    private companion object {
        val FIRST_KEY = AppFunctionKey("notes.app", "create_note")
        val SECOND_KEY = AppFunctionKey("notes.app", "archive_note")

        fun descriptor(key: AppFunctionKey) = AppFunctionDescriptor(
            key = key,
            appLabel = "Notes",
            appDescription = "Notes app",
            description = key.functionId,
            parameters = emptyList(),
            providerEnabled = true,
            supported = true,
        )
    }
}
