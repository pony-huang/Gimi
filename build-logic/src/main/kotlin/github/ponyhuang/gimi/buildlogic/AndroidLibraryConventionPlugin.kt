package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** 为 Android library 模块提供稳定且不含业务信息的工程基线。 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidLibrary()
        }
        applyUnitTestDefaults()
        registerJvmTestAlias()
    }
}

/**
 * 注册统一的 `jvmTest` 别名：全仓一条 `gradlew jvmTest` 即可覆盖
 * Android 模块的 `testDebugUnitTest` 与纯 JVM 模块的 `test`，
 * 避免"全测试"命令遗漏 domain/core 等纯 JVM 模块。
 */
internal fun Project.registerJvmTestAlias() {
    tasks.register("jvmTest") {
        group = "verification"
        description = "Runs all local unit tests (alias unifying testDebugUnitTest and JVM :test)."
        dependsOn("testDebugUnitTest")
    }
}
