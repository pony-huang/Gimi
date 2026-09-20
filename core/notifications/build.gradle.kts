plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android {
    namespace = "github.ponyhuang.gimi.core.notifications"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
}
