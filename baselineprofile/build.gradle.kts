plugins {
    id("gimi.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "github.ponyhuang.gimi.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
}

baselineProfile {
    useConnectedDevices = true
}
