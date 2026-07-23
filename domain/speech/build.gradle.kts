plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":domain:modelcatalog"))
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
