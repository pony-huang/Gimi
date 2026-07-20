plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "github.ponyhuang.asssistantai.core.database"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(libs.androidx.room.runtime)
}
