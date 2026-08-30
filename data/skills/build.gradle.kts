plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.skills"
}

dependencies {
    implementation(project(":domain:skills"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.adk.kotlin.core) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
