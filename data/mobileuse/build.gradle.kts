plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.mobileuse"
    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":domain:mobileuse"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mlkit.text.recognition.chinese)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
