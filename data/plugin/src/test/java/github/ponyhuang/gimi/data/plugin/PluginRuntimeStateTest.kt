package github.ponyhuang.gimi.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeStateTest {

    @Test
    fun `initial snapshot excludes disabled plugins`() {
        val state = state(disabledIds = setOf("spotify"))

        assertEquals(0L, state.runtime.value.revision)
        assertEquals(listOf("zhihu"), state.runtime.value.enabledPlugins)
    }

    @Test
    fun `disabling enabled plugin publishes next snapshot`() {
        val state = state()

        assertTrue(state.setEnabled("spotify", enabled = false))

        assertEquals(1L, state.runtime.value.revision)
        assertEquals(listOf("zhihu"), state.runtime.value.enabledPlugins)
        assertEquals(setOf("spotify"), state.disabledPluginIds())
    }

    @Test
    fun `enabling disabled plugin publishes next snapshot`() {
        val state = state(disabledIds = setOf("spotify"))

        assertTrue(state.setEnabled("spotify", enabled = true))

        assertEquals(1L, state.runtime.value.revision)
        assertEquals(listOf("spotify", "zhihu"), state.runtime.value.enabledPlugins)
    }

    @Test
    fun `setting existing state does not publish revision`() {
        val state = state()

        assertFalse(state.setEnabled("spotify", enabled = true))

        assertEquals(0L, state.runtime.value.revision)
    }

    @Test
    fun `setting unknown plugin does not publish revision`() {
        val state = state()

        assertFalse(state.setEnabled("missing", enabled = false))

        assertEquals(0L, state.runtime.value.revision)
    }

    @Test
    fun `replacing loaded plugins publishes filtered snapshot`() {
        val state = state(disabledIds = setOf("spotify"))

        state.replacePlugins(listOf("spotify", "zhihu", "v2ex"))

        assertEquals(1L, state.runtime.value.revision)
        assertEquals(listOf("zhihu", "v2ex"), state.runtime.value.enabledPlugins)
    }

    @Test
    fun `replacing identical plugins does not publish revision`() {
        val state = state()

        assertFalse(state.replacePlugins(listOf("spotify", "zhihu")))

        assertEquals(0L, state.runtime.value.revision)
    }

    @Test
    fun `configuration change publishes revision only for loaded plugin`() {
        val state = state()

        assertTrue(state.markConfigurationChanged("spotify"))
        assertFalse(state.markConfigurationChanged("missing"))

        assertEquals(1L, state.runtime.value.revision)
    }

    @Test
    fun `snapshot list cannot mutate runtime state`() {
        val state = state()
        val exposed = state.runtime.value.enabledPlugins

        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (exposed as MutableList<String>).add("injected")
        }
        assertEquals(listOf("spotify", "zhihu"), state.runtime.value.enabledPlugins)
    }

    private fun state(disabledIds: Set<String> = emptySet()): PluginRuntimeState<String> =
        PluginRuntimeState(
            initialPlugins = listOf("spotify", "zhihu"),
            initialDisabledPluginIds = disabledIds,
            pluginId = { it },
        )
}
