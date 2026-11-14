plugins { id("gimi.android.library"); id("gimi.android.hilt") }
android { namespace = "github.ponyhuang.gimi.data.environment" }
dependencies {
    implementation(project(":core:storage"))
    implementation(project(":domain:workfiles"))
    implementation(project(":domain:environment"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
