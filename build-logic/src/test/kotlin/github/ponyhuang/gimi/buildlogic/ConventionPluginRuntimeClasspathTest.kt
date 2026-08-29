package github.ponyhuang.gimi.buildlogic

import org.junit.Assert.assertNotNull
import org.junit.Test

/** 验证约定插件实现依赖的 Gradle 插件 API 会进入运行时 classpath。 */
class ConventionPluginRuntimeClasspathTest {
    @Test
    fun androidConventionRuntimeIncludesAgpApi() {
        assertNotNull(
            "Android convention runtime is missing the AGP library DSL API",
            javaClass.classLoader.getResource(
                "com/android/build/api/dsl/LibraryExtension.class",
            ),
        )
    }
}
