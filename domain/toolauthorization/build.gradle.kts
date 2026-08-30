plugins {
    id("gimi.kotlin.jvm.library")
}

dependencies {
    implementation(project(":domain:conversation"))
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
