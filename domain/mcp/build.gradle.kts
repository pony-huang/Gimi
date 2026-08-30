plugins {
    id("gimi.kotlin.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":domain:conversation"))

    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
