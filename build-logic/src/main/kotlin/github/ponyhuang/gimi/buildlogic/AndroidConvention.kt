package github.ponyhuang.gimi.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

/**
 * 本地单测默认并行 fork 数：20 核机器取 4 平衡吞吐与内存；
 * CI 等受限环境用 `-PgimiTestMaxParallelForks=1` 覆盖。
 */
internal const val TEST_MAX_PARALLEL_FORKS_PROPERTY = "gimiTestMaxParallelForks"

internal fun Project.applyUnitTestDefaults() {
    val maxForks = providers.gradleProperty(TEST_MAX_PARALLEL_FORKS_PROPERTY)
        .orElse("4")
        .get()
        .toInt()
        .coerceAtLeast(1)
    tasks.withType<Test> {
        maxParallelForks = maxForks
        maxHeapSize = "1g"
    }
}

internal fun ApplicationExtension.configureAndroidApplication() {
    compileSdk { version = release(37) { minorApiLevel = 1 } }
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
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 34 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
