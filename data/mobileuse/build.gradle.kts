plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.data.mobileuse"
    defaultConfig.consumerProguardFiles("consumer-rules.pro")
    buildFeatures {
        aidl = true
    }
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    implementation(project(":domain:mobileuse"))
    implementation(libs.shizuku.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mlkit.text.recognition.chinese)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
