plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "github.ponyhuang.gimi.plugin.spotify"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.spotify"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // compileOnly（provided）：插件 APK 不打包 SPI/ADK/coroutines，运行时由宿主类加载器提供。
    // compileOnly 非传递，三个都需显式声明。
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
    compileOnly(libs.kotlinx.coroutines.core)

    // 单测里用 JVM 版 org.json（Android 的 org.json 在 JVM 单测里是抛 not mocked 的桩）。
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
