package github.ponyhuang.gimi.data.plugin

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginLoadNoticeQueueTest {
    @Test
    fun foregroundCollectorReceivesOneNoticeAfterScanCompletes() = runTest {
        val queue = PluginLoadNoticeQueue()
        val next = async { queue.incompatiblePluginNames.take(1).toList() }
        queue.flush()
        queue.report("plugin.one", 1L, "One")
        runCurrent()
        assertFalse(next.isCompleted)
        queue.report("plugin.two", 1L, "Two")
        queue.flush()
        assertEquals(listOf(listOf("One", "Two")), next.await())
    }

    @Test
    fun startupNoticesAreRetainedUntilUiCollects() = runTest {
        val queue = PluginLoadNoticeQueue()
        queue.report("plugin.one", 1L, "One")
        queue.report("plugin.two", 1L, "Two")

        queue.flush()
        assertEquals(listOf(listOf("One", "Two")), queue.incompatiblePluginNames.take(1).toList())
    }

    @Test
    fun refreshOnlyNotifiesAgainForAnUpdatedInstallation() = runTest {
        val queue = PluginLoadNoticeQueue()
        queue.report("plugin.one", 1L, "One")
        queue.report("plugin.one", 1L, "One")
        queue.report("plugin.one", 2L, "Updated one")

        queue.flush()
        assertEquals(listOf(listOf("One", "Updated one")), queue.incompatiblePluginNames.take(1).toList())
    }

    @Test
    fun consumedNoticesAreNotReplayedWhenUiRestarts() = runTest {
        val queue = PluginLoadNoticeQueue()
        queue.report("plugin.one", 1L, "One")
        queue.flush()
        assertEquals(listOf(listOf("One")), queue.incompatiblePluginNames.take(1).toList())
        val next = async { queue.incompatiblePluginNames.take(1).toList() }
        queue.report("plugin.two", 1L, "Two")
        queue.flush()
        assertEquals(listOf(listOf("Two")), next.await())
    }
}
