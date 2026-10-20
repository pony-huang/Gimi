plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.core.apk"
}

dependencies {
    implementation(project(":core:storage"))
    implementation(libs.androidx.core.ktx)
}
