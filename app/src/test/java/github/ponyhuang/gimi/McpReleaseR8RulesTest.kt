package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class McpReleaseR8RulesTest {

    @Test
    fun mcpTypeRefsRetainTheirGenericSubclassAtRuntime() {
        val rules = File("proguard-rules.pro").readText()

        assertTrue(
            "R8 must not optimize MCP TypeRef itself",
            rules.contains(
                "-keep,allowobfuscation,allowshrinking class " +
                    "io.modelcontextprotocol.json.TypeRef",
            ),
        )
        assertTrue(
            "R8 must not merge anonymous MCP TypeRef subclasses",
            rules.contains(
                "-keep,allowobfuscation,allowshrinking class * extends " +
                    "io.modelcontextprotocol.json.TypeRef",
            ),
        )
    }

    @Test
    fun mcpJacksonSchemaModelsRemainSerializable() {
        val rules = File("proguard-rules.pro").readText()

        assertTrue(
            "R8 must retain MCP schema members used by Jackson",
            rules.contains("-keep class io.modelcontextprotocol.spec.McpSchema$* { *; }"),
        )
    }
}
