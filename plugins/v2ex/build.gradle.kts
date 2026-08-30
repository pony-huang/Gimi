plugins {
    id("gimi.plugin.application")
}

android {
    namespace = "github.ponyhuang.gimi.plugin.v2ex"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.v2ex"
    }
}

dependencies {
    // compileOnly（provided）：插件 APK 不打包 SPI/ADK/coroutines，运行时由宿主类加载器提供。
    // compileOnly 非传递，三个都需显式声明。
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
    compileOnly(libs.kotlinx.coroutines.core)

    // 单测里用 JVM 版 org.json（Android 的 org.json 在 JVM 单测里是抛 not mocked 的桩）。
    testImplementation(project(":plugin-api"))
    testImplementation(libs.google.adk.kotlin.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.org.json)
}
