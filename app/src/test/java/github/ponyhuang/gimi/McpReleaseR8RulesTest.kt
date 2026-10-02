package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 发布源码配置约定，不证明最终依赖图、合并 R8 规则或 Release 运行行为。 */
class McpReleaseR8RulesTest {

    @Test
    fun releaseRulesProtectAppOptimizationAndPluginAbi() {
        val rules = File("proguard-rules.pro").readText()

        assertTrue(
            "App code must stay shrinkable but optimization-free (R8 inlining crashed plugin config saving)",
            rules.contains("-keep,allowshrinking class github.ponyhuang.gimi.** { *; }"),
        )
        assertTrue(
            "The plugin ABI must stay full keep roots for parent-first plugin resolution",
            rules.contains("-keep class github.ponyhuang.gimi.pluginapi.** { *; }"),
        )
    }

    @Test
    fun releaseRulesProtectJacksonDeserializersFromR8Optimization() {
        val rules = File("proguard-rules.pro").readText()

        assertTrue(
            "Jackson deserializers must not be optimized because release-only R8 rewriting can null their value deserializer",
            rules.contains("-keep,allowobfuscation class com.fasterxml.jackson.databind.JsonDeserializer { *; }") &&
                rules.contains("-keep,allowobfuscation class com.fasterxml.jackson.databind.deser.** { *; }"),
        )
    }

}
