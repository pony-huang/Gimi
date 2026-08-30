plugins {
    id("gimi.kotlin.jvm.library")
}

dependencies {
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
}
