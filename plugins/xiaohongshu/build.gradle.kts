plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "github.ponyhuang.gimi.plugin.xiaohongshu"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.xiaohongshu"
        minSdk = 34
        targetSdk = 37
        versionCode = providers.gradleProperty("releaseVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("releaseVersionName").getOrElse("1.0")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    signingConfigs {
        create("release") {
            val storeFilePath = providers.gradleProperty("releaseStoreFile").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("releaseStorePassword").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            }
        }
    }
    buildTypes {
        release {
            val hasCiSigning = providers.gradleProperty("releaseStoreFile").isPresent
            signingConfig = if (hasCiSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
    compileOnly(libs.kotlinx.coroutines.core)

    testImplementation(project(":plugin-api"))
    testImplementation(libs.google.adk.kotlin.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.org.json)
}
