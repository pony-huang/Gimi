plugins {
    id("gimi.android.library")
}

android {
    namespace = "github.ponyhuang.gimi.core.audio"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}