plugins { id("gimi.android.application") }

android {
    namespace = "github.ponyhuang.gimi.environment.runtime"
    defaultConfig {
        applicationId = "github.ponyhuang.gimi.environment.runtime"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging.jniLibs.useLegacyPackaging = true
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("generated/runtimeJni").get().asFile)
    testOptions.unitTests.isIncludeAndroidResources = true
    signingConfigs {
        create("release") {
            // 与主 App 使用同一发布密钥，保证签名权限通信及组件覆盖更新可用。
            val storeFilePath = providers.gradleProperty("releaseStoreFile").orNull
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = providers.gradleProperty("releaseStorePassword").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            }
        }
    }
    buildTypes {
        release {
            signingConfig = when {
                providers.gradleProperty("releaseStoreFile").isPresent -> signingConfigs.getByName("release")
                providers.gradleProperty("android.injected.signing.store.file").isPresent -> null
                else -> signingConfigs.getByName("debug")
            }
        }
    }
}

val prepareRuntime by tasks.registering(Exec::class) {
    inputs.file("prepare_native.py")
    outputs.dir(layout.buildDirectory.dir("generated/runtimeJni"))
    commandLine(if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3",
        "prepare_native.py", layout.buildDirectory.dir("generated/runtimeJni/arm64-v8a").get().asFile.absolutePath)
}
tasks.named("preBuild") { dependsOn(prepareRuntime) }

dependencies {
    implementation(project(":core:security"))
    implementation(project(":domain:environment"))
    implementation(libs.commons.compress)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
