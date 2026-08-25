package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpReleaseR8RulesTest {

    @Test
    fun releaseUsesKotlinMcpSdkWithoutJavaSdkRuntime() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val agentBuild = File("../data/agent/build.gradle.kts").readText()

        assertTrue(
            "The MCP client must use the Kotlin SDK",
            catalog.contains("io.modelcontextprotocol:kotlin-sdk-client"),
        )
        assertFalse(
            "The direct Java MCP SDK dependency must be removed",
            catalog.contains("io.modelcontextprotocol.sdk:mcp-core"),
        )
        assertFalse(
            "The Java SDK bridge must not keep Reactor in data:agent",
            agentBuild.contains("libs.kotlinx.coroutines.reactor"),
        )
    }

    @Test
    fun releaseRulesDoNotKeepJavaMcpSerializationModels() {
        val rules = File("proguard-rules.pro").readText()

        assertFalse(rules.contains("io.modelcontextprotocol.json.TypeRef"))
        assertFalse(rules.contains("io.modelcontextprotocol.spec.McpSchema"))
        assertTrue(
            "App code must remain protected from reflection-related R8 regressions",
            rules.contains("-keep class github.ponyhuang.gimi.** { *; }"),
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

    @Test
    fun releaseBuildProvidesEnoughHeapForR8() {
        val gradleProperties = File("../gradle.properties").readText()
        val releaseWorkflow = File("../.github/workflows/release.yml").readText()

        assertTrue(
            "R8 exhausted the former 4 GiB heap; release builds require a 6 GiB Gradle heap",
            gradleProperties.contains("org.gradle.jvmargs=-Xmx6144m"),
        )
        assertTrue(
            "CI must cap Gradle worker concurrency during release shrinking",
            releaseWorkflow.contains("--max-workers=2"),
        )
        assertTrue(
            "CI must not run other project tasks in parallel with R8",
            releaseWorkflow.contains("--no-parallel"),
        )
    }
}
