plugins {
    id("gimi.android.library")
}

android {
    namespace = "github.ponyhuang.gimi.data.workspace"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":core:storage"))
    implementation(project(":domain:workspace"))

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
