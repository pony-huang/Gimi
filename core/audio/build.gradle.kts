plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "github.ponyhuang.asssistantai.core.audio"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
