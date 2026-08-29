package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

internal fun ApplicationExtension.configureAndroidApplication() {
    compileSdk { version = release(37) }
    defaultConfig {
        minSdk = 34
        targetSdk = 37
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

internal fun LibraryExtension.configureAndroidLibrary() {
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
