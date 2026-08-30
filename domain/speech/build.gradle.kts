plugins {
    id("gimi.kotlin.jvm.library")
}

dependencies {
    implementation(project(":domain:modelcatalog"))
    implementation(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
