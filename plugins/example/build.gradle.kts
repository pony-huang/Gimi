plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "github.ponyhuang.gimi.plugin.example"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.example"
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
    // compileOnly（provided）：插件 APK 不打包 SPI 与 ADK，运行时由宿主类加载器提供，
    // 保证 AgentPlugin / Plugin 类身份与宿主一致。compileOnly 非传递，两者都需显式声明。
    // 后续 :plugin-api 拆到独立仓库并发布后，只需把 project(":plugin-api") 换成 Maven 坐标。
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
}
