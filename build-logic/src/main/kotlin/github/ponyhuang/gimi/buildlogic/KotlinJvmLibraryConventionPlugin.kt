package github.ponyhuang.gimi.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** 为纯 Kotlin/JVM 模块统一 Kotlin plugin 与 JVM toolchain。 */
class KotlinJvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<KotlinJvmProjectExtension> {
                jvmToolchain(11)
            }
            // 与 Android 模块的 jvmTest 别名对齐，保证 `gradlew jvmTest` 全仓可用。
            tasks.register("jvmTest") {
                group = "verification"
                description = "Runs all JVM tests (alias of :test)."
                dependsOn("test")
            }
            applyUnitTestDefaults()
        }
    }
}
