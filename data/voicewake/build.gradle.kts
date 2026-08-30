plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.voicewake"
}

dependencies {
    implementation(project(":core:audio"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":domain:speech"))
    implementation(project(":domain:assistant"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.gson)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.vosk.android)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
