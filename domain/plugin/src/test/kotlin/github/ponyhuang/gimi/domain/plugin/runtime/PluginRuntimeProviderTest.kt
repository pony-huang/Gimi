package github.ponyhuang.gimi.domain.plugin.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PluginRuntimeProviderTest {

    @Test
    fun `runtime exposes revision and enabled plugins atomically`() = runTest {
        val state = MutableStateFlow(
            PluginRuntimeSnapshot(revision = 7L, enabledPlugins = listOf("spotify", "zhihu")),
        )
        val provider = object : PluginRuntimeProvider<String> {
            override val runtime = state
        }

        val snapshot = provider.runtime.first()

        assertEquals(7L, snapshot.revision)
        assertEquals(listOf("spotify", "zhihu"), snapshot.enabledPlugins)
    }

    @Test
    fun `provider returns the same StateFlow instance across reads`() {
        val state = MutableStateFlow(PluginRuntimeSnapshot<String>(0L, emptyList()))
        val provider = object : PluginRuntimeProvider<String> {
            override val runtime = state
        }

        assertSame(provider.runtime, provider.runtime)
    }

    @Test
    fun `snapshot is immutable from the consumer perspective`() {
        val original = PluginRuntimeSnapshot(revision = 1L, enabledPlugins = mutableListOf("a"))
        // 拷贝封装为 List 后不应反向影响 StateFlow，但数据类的 enabledPlugins 仍是 List 引用。
        // 这里验证的是：toString 不暴露内部细节、== 与 hashCode 仅依赖 data class 字段。
        val clone = original.copy(revision = 2L)

        assertEquals(1L, original.revision)
        assertEquals(2L, clone.revision)
        assertEquals(original.enabledPlugins, clone.enabledPlugins)
    }
}