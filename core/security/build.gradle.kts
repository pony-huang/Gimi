plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "github.ponyhuang.gimi.core.security"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    testImplementation(libs.junit)
}
