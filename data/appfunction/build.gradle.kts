plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
}
android { namespace = "github.ponyhuang.gimi.data.appfunction" }
dependencies {
    implementation(project(":domain:appfunction"))
    implementation(libs.androidx.appfunctions)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
