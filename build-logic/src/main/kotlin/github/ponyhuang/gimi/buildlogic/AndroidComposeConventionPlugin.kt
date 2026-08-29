package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** 为已应用 Android plugin 的模块启用 Compose 构建能力。 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        requireAndroidPlugin("gimi.android.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        if (pluginManager.hasPlugin("com.android.application")) {
            extensions.configure<ApplicationExtension> {
                buildFeatures { compose = true }
            }
        } else {
            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
            }
        }
    }
}

internal fun Project.requireAndroidPlugin(conventionId: String) {
    require(
        pluginManager.hasPlugin("com.android.application") ||
            pluginManager.hasPlugin("com.android.library"),
    ) {
        "$conventionId requires gimi.android.application or gimi.android.library"
    }
}
