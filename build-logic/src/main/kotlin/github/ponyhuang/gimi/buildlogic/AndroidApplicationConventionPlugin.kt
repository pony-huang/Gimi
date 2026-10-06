package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** 为 Android application 模块提供稳定且不含业务信息的工程基线。 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidApplication()
            defaultConfig {
                // 唯一版本来源；本地与 CI 打包读取相同值，重试不会改变安装版本。
                versionName = providers.fileContents(
                    rootProject.layout.projectDirectory.file("version.txt"),
                ).asText.get().trim()
                versionCode = providers.gradleProperty("releaseVersionCode").get().toInt()
            }
        }
    }
}
