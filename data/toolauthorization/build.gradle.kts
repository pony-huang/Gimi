plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.toolauthorization"
}

dependencies {
    implementation(project(":domain:toolauthorization"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
