import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val minimaxApiKey = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { input -> load(input) }
    }
}.getProperty("MINIMAX_API_KEY").orEmpty()

val deepseekApiKey = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use { input -> load(input) }
    }
}.getProperty("DEEPSEEK_API_KEY").orEmpty()

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
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "MINIMAX_API_KEY", "\"$minimaxApiKey\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepseekApiKey\"")
        }
        release {
            buildConfigField("String", "MINIMAX_API_KEY", "\"\"")
            buildConfigField("String", "DEEPSEEK_API_KEY", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // TODO: replace with a real upload keystore before Play Store publish.
            // For local Meizu verification we sign with the auto-generated debug
            // keystore so `assembleRelease` produces an installable APK without
            // standing up a keystore just to test R8 / shrink behavior.
            signingConfig = signingConfigs.getByName("debug")
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
    testOptions {
        unitTests.isReturnDefaultValues  = true
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
            // minSdk = 35 — 32-bit ABIs are no longer required by Google Play
            // for API 35+ targets. arm64 covers real devices; x86_64 covers the
            // Meizu emulator and CI emulators. isUniversalApk = true keeps a
            // fat APK so ADB install works without picking an ABI.
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    // Drop kxml2 from the release runtime configurations only. Android's
    // framework already provides android.content.res.XmlResourceParser which
    // implements the same org.xmlpull.v1.XmlPullParser interface, so keeping
    // both jars in the runtime classpath makes R8 fail with "Library class
    // implements program class". Excluding kxml2 from runtime/compile lets
    // the Android implementation take over at runtime, while leaving it on
    // lint/testAndroid configurations so build-time tooling keeps working.
    listOf("releaseRuntimeClasspath", "releaseCompileClasspath", "releaseRuntimeOnly").forEach { configName ->
        configurations.findByName(configName)?.exclude(group = "net.sf.kxml", module = "kxml2")
    }

    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.google.adk.kotlin.core)
    implementation(libs.anthropic.java)
    ksp(libs.google.adk.kotlin.processor)
    ksp(libs.androidx.room.compiler)
    implementation(libs.openai.java)
    implementation(libs.okhttp)
    implementation(libs.mcp.kotlin.sdk.client)
    // ktor-client-okhttp is the OkHttp engine for the Ktor client. McpToolRegistry
    // constructs HttpClient(OkHttp) explicitly to share the okhttp connection pool
    // pulled in by openai-java and anthropic-java — the engine artifact is required,
    // not redundant.
    implementation(libs.ktor.client.okhttp)
    implementation(libs.gson)
    implementation(libs.vosk.android)
    implementation(libs.multiplatform.markdown.renderer.m3)
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
    testImplementation(libs.google.adk.kotlin.webserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
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
    ksp(libs.androidx.appfunctions.compiler)
}

// AppFunctions KSP option — collect every @AppFunction declared in this module
// into a single aggregated metadata file the platform reads at runtime.
// Reference: https://developer.android.com/ai/appfunctions
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}
