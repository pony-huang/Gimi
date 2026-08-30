plugins {
    id("gimi.plugin.application")
}

android {
    namespace = "github.ponyhuang.gimi.plugin.zhihu"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.zhihu"
    }
}

dependencies {
    // compileOnly（provided）：插件 APK 不打包 SPI/ADK/coroutines，运行时由宿主类加载器提供。
    // compileOnly 非传递，三个都需显式声明。
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
    compileOnly(libs.kotlinx.coroutines.core)

    testImplementation(project(":plugin-api"))
    testImplementation(libs.google.adk.kotlin.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.org.json)
    testImplementation(libs.okhttp.mockwebserver)
}
