plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "github.ponyhuang.asssistantai"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "github.ponyhuang.asssistantai"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0-alpha.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Baseline 记录现存 lint 问题（如 BluetoothVoiceService 蓝牙权限检查），lint 继续对新问题报错。
    lint {
        baseline = file("lint-baseline.xml")
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
                // GraalVM native-image metadata shipped by google-genai, google-auth,
                // google-http-client, jansi, opentelemetry. Useless on Android runtime.
                "META-INF/native-image/**",
                "META-INF/proguard/**",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(project(":domain:conversation"))
    implementation(project(":domain:speech"))
    implementation(project(":core:audio"))
    implementation(project(":data:assistant"))
    implementation(project(":domain:assistant"))
    implementation(project(":core:common"))
    implementation(project(":data:modelcatalog"))
    implementation(project(":data:speech"))
    implementation(project(":data:voicewake"))
    // BluetoothVoiceService uses org.vosk.Model directly (wakeModels.acquire /
    // VoskWakeWordDetector), so the app must declare vosk itself instead of
    // relying on :data:voicewake's implementation-scope transitive.
    implementation(libs.vosk.android)
    implementation(project(":data:conversation"))
    implementation(project(":data:agent"))
    implementation(project(":data:mcp"))
    implementation(project(":data:workfiles"))
    implementation(project(":data:permissions"))
    implementation(project(":data:toolauthorization"))
    implementation(project(":data:skills"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:modelsettings"))
    implementation(project(":feature:mcp"))
    implementation(project(":feature:workfiles"))
    implementation(project(":feature:permissions"))
    implementation(project(":feature:toolauthorization"))
    implementation(project(":feature:voicewake"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:skills"))
    implementation(project(":feature:chat"))
    testImplementation(project(":core:testing"))
    // Drop kxml2 from the release runtime configurations only. Android's
    // framework already provides android.content.res.XmlResourceParser which
    // implements the same org.xmlpull.v1.XmlPullParser interface, so keeping
    // both jars in the runtime classpath makes R8 fail with "Library class
    // implements program class". Excluding kxml2 from runtime/compile lets
    // the Android implementation take over at runtime, while leaving it on
    // lint/testAndroid configurations so build-time tooling keeps working.
    val kxml2ExcludedConfigurations = setOf(
        "releaseRuntimeClasspath",
        "releaseCompileClasspath",
        "releaseRuntimeOnly",
    )
    configurations.configureEach {
        if (name in kxml2ExcludedConfigurations) {
            exclude(group = "net.sf.kxml", module = "kxml2")
        }
    }

    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.javax.inject)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AppFunctions — expose on-device agent workflows to system agents via
    // androidx.appfunctions (Android 16+, Jetpack backwards-compat layer).
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
}
