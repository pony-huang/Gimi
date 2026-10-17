package github.ponyhuang.gimi

import github.ponyhuang.gimi.feature.modelsettings.ModelSettingsDestination
import github.ponyhuang.gimi.feature.memory.MemoryDestination
import github.ponyhuang.gimi.feature.permissions.PermissionDestination
import github.ponyhuang.gimi.navigation.navigationContentKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationContentKeyTest {
    @Test
    fun sameNamedDestinationsFromDifferentFeaturesHaveDistinctKeys() {
        assertEquals(MemoryDestination.Settings.toString(), PermissionDestination.Settings.toString())
        assertNotEquals(
            MemoryDestination.Settings.navigationContentKey(),
            PermissionDestination.Settings.navigationContentKey(),
        )
    }

    @Test
    fun destinationParametersDistinguishKeysAndEquivalentDestinationsStayStable() {
        val destination = ModelSettingsDestination.ServiceDetail("service-a")
        assertEquals(destination.navigationContentKey(), destination.copy().navigationContentKey())
        assertNotEquals(destination.navigationContentKey(), destination.copy(serviceId = "service-b").navigationContentKey())
    }
}
