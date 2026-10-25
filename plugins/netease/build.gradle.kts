plugins {
    id("gimi.plugin.application")
}

android {
    namespace = "github.ponyhuang.gimi.plugin.netease"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.plugin.netease"
    }
}

dependencies {
    compileOnly(project(":plugin-api"))
    compileOnly(libs.google.adk.kotlin.core)
    compileOnly(libs.kotlinx.coroutines.core)
    implementation(project(":core:security"))

    testImplementation(project(":plugin-api"))
    testImplementation(libs.google.adk.kotlin.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.org.json)
    testImplementation(libs.okhttp.mockwebserver)
}
