package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** 为 macrobenchmark/baseline profile 测试模块提供与主 App 一致的工程基线。 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        extensions.configure<TestExtension> {
            compileSdk { version = release(37) { minorApiLevel = 1 } }
            defaultConfig { minSdk = 34 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }
}
