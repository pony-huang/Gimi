plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(11) }

dependencies {
    api(project(":domain:conversation"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
