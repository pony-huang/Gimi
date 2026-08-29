import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.plugin.use.PluginDependency

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Included build 隔离插件实现 classpath，并按 version catalog 锁定同一版本。
    implementation(pluginMarker(libs.plugins.android.application))
    implementation(pluginMarker(libs.plugins.android.library))
    implementation(pluginMarker(libs.plugins.kotlin.jvm))
    implementation(pluginMarker(libs.plugins.kotlin.compose))
    implementation(pluginMarker(libs.plugins.ksp))
    implementation(pluginMarker(libs.plugins.hilt.android))

    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("androidApplicationConvention") {
            id = "gimi.android.application"
            implementationClass =
                "github.ponyhuang.gimi.buildlogic.AndroidApplicationConventionPlugin"
        }
        create("androidLibraryConvention") {
            id = "gimi.android.library"
            implementationClass =
                "github.ponyhuang.gimi.buildlogic.AndroidLibraryConventionPlugin"
        }
        create("kotlinJvmLibraryConvention") {
            id = "gimi.kotlin.jvm.library"
            implementationClass =
                "github.ponyhuang.gimi.buildlogic.KotlinJvmLibraryConventionPlugin"
        }
        create("androidComposeConvention") {
            id = "gimi.android.compose"
            implementationClass =
                "github.ponyhuang.gimi.buildlogic.AndroidComposeConventionPlugin"
        }
        create("androidHiltConvention") {
            id = "gimi.android.hilt"
            implementationClass =
                "github.ponyhuang.gimi.buildlogic.AndroidHiltConventionPlugin"
        }
        create("pluginApplicationConvention") {
            id = "gimi.plugin.application"
            implementationClass =
                "github.ponyhuang.gimi.buildlogic.PluginApplicationConventionPlugin"
        }
    }
}

private fun DependencyHandler.pluginMarker(
    plugin: Provider<PluginDependency>,
): Provider<String> = plugin.map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}
