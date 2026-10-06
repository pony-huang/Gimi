package github.ponyhuang.gimi.data.plugin

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginLoadNoticeQueueTest {
    @Test
    fun startupNoticesAreRetainedUntilUiCollects() = runTest {
        val queue = PluginLoadNoticeQueue()
        queue.report("plugin.one", 1L, "One")
        queue.report("plugin.two", 1L, "Two")

        assertEquals(listOf("One", "Two"), queue.incompatiblePluginNames.take(2).toList())
    }

    @Test
    fun refreshOnlyNotifiesAgainForAnUpdatedInstallation() = runTest {
        val queue = PluginLoadNoticeQueue()
        queue.report("plugin.one", 1L, "One")
        queue.report("plugin.one", 1L, "One")
        queue.report("plugin.one", 2L, "Updated one")

        assertEquals(listOf("One", "Updated one"), queue.incompatiblePluginNames.take(2).toList())
    }

    @Test
    fun consumedNoticesAreNotReplayedWhenUiRestarts() = runTest {
        val queue = PluginLoadNoticeQueue()
        queue.report("plugin.one", 1L, "One")
        assertEquals(listOf("One"), queue.incompatiblePluginNames.take(1).toList())
        val next = async { queue.incompatiblePluginNames.take(1).toList() }
        queue.report("plugin.two", 1L, "Two")
        assertEquals(listOf("Two"), next.await())
    }
}
