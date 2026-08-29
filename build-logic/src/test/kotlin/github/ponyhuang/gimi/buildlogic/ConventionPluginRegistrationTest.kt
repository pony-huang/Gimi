package github.ponyhuang.gimi.buildlogic

import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** 验证项目约定插件 ID 与实现类之间的稳定映射。 */
class ConventionPluginRegistrationTest {
    @Test
    fun conventionPluginDescriptorsPointToExpectedImplementations() {
        expectedPlugins.forEach { (pluginId, implementationClass) ->
            val resourceName = "META-INF/gradle-plugins/$pluginId.properties"
            val resource = javaClass.classLoader.getResourceAsStream(resourceName)
            assertNotNull("Missing plugin descriptor: $resourceName", resource)

            val properties = Properties().apply {
                resource!!.use(::load)
            }
            assertEquals(
                "Unexpected implementation class for $pluginId",
                implementationClass,
                properties.getProperty("implementation-class"),
            )
        }
    }

    private companion object {
        val expectedPlugins = mapOf(
            "gimi.android.application" to
                "github.ponyhuang.gimi.buildlogic.AndroidApplicationConventionPlugin",
            "gimi.android.library" to
                "github.ponyhuang.gimi.buildlogic.AndroidLibraryConventionPlugin",
            "gimi.kotlin.jvm.library" to
                "github.ponyhuang.gimi.buildlogic.KotlinJvmLibraryConventionPlugin",
            "gimi.android.compose" to
                "github.ponyhuang.gimi.buildlogic.AndroidComposeConventionPlugin",
            "gimi.android.hilt" to
                "github.ponyhuang.gimi.buildlogic.AndroidHiltConventionPlugin",
            "gimi.plugin.application" to
                "github.ponyhuang.gimi.buildlogic.PluginApplicationConventionPlugin",
        )
    }
}
