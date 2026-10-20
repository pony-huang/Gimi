plugins {
    id("gimi.kotlin.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
