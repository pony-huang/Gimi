plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    // MainDispatcherRule 是 main src 暴露给 feature 单元测试的通用 JUnit 规则。
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)

    testImplementation(libs.junit)
}