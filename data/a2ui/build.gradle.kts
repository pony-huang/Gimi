plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}

android { namespace = "github.ponyhuang.gimi.data.a2ui" }

dependencies {
    implementation(project(":domain:a2ui"))
    implementation(libs.androidx.a2ui.engine)
    implementation(libs.androidx.a2ui.model)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
