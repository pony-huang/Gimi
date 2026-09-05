plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.plugin"
}

dependencies {
    // SPI 仅供本模块加载和实现插件使用；真实消费者需显式声明 :plugin-api。
    implementation(project(":plugin-api"))
    implementation(project(":domain:plugin"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
