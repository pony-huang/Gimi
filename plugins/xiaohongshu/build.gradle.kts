plugins {
    id("gimi.plugin.application")
}

android {
    namespace = "github.ponyhuang.gimi.plugin.xiaohongshu"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.xiaohongshu"
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
