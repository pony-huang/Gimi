plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "github.ponyhuang.asssistantai.data.workfiles"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":domain:workfiles"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.android.compiler)
}
