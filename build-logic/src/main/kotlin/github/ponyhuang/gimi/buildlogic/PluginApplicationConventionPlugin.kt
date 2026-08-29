package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** 为独立插件 APK 复用发布版本注入与安全签名回退策略。 */
class PluginApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("gimi.android.application")
        extensions.configure<ApplicationExtension> {
            defaultConfig {
                versionCode = providers.gradleProperty("releaseVersionCode")
                    .map(String::toInt)
                    .getOrElse(1)
                versionName = providers.gradleProperty("releaseVersionName").getOrElse("1.0")
            }

            val storeFilePath = providers.gradleProperty("releaseStoreFile").orNull
            val storePasswordValue = providers.gradleProperty("releaseStorePassword").orNull
            val releaseKeyAlias = providers.gradleProperty("releaseKeyAlias").orNull
            val releaseKeyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            val hasCompleteReleaseSigning = listOf(
                storeFilePath,
                storePasswordValue,
                releaseKeyAlias,
                releaseKeyPassword,
            ).all { it != null }

            val releaseSigning = signingConfigs.maybeCreate("release")
            if (hasCompleteReleaseSigning) {
                // 密钥只从本次 Gradle 属性读取，不记录到日志或持久化文件。
                releaseSigning.storeFile = file(requireNotNull(storeFilePath))
                releaseSigning.storePassword = requireNotNull(storePasswordValue)
                releaseSigning.keyAlias = requireNotNull(releaseKeyAlias)
                releaseSigning.keyPassword = requireNotNull(releaseKeyPassword)
            }

            buildTypes.named("release").configure {
                signingConfig = if (hasCompleteReleaseSigning) {
                    releaseSigning
                } else {
                    signingConfigs.getByName("debug")
                }
            }
        }
    }
}
