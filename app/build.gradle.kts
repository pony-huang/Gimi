import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use(::load)
}

fun releaseSigningValue(environmentName: String, propertyName: String): String? =
    System.getenv(environmentName)?.takeIf(String::isNotBlank)
        ?: localProperties.getProperty(propertyName)?.takeIf(String::isNotBlank)

val releaseStoreFile = releaseSigningValue(
    "ASSISTANTAI_RELEASE_STORE_FILE",
    "asssistantai.release.storeFile",
)
val releaseStorePassword = releaseSigningValue(
    "ASSISTANTAI_RELEASE_STORE_PASSWORD",
    "asssistantai.release.storePassword",
)
val releaseKeyAlias = releaseSigningValue(
    "ASSISTANTAI_RELEASE_KEY_ALIAS",
    "asssistantai.release.keyAlias",
)
val releaseKeyPassword = releaseSigningValue(
    "ASSISTANTAI_RELEASE_KEY_PASSWORD",
    "asssistantai.release.keyPassword",
)
val releaseStorePath = releaseStoreFile?.let(rootProject::file)?.absolutePath.orEmpty()

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

    signingConfigs {
        create("releasePrivate") {
            storeFile = rootProject.file(releaseStoreFile ?: "temp/missing-release.keystore")
            storePassword = releaseStorePassword.orEmpty()
            keyAlias = releaseKeyAlias.orEmpty()
            keyPassword = releaseKeyPassword.orEmpty()
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("releasePrivate")
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

val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails release builds when private signing configuration is incomplete."
    inputs.property("releaseStoreFile", releaseStorePath)
    inputs.property("releaseStorePasswordConfigured", !releaseStorePassword.isNullOrBlank())
    inputs.property("releaseKeyAliasConfigured", !releaseKeyAlias.isNullOrBlank())
    inputs.property("releaseKeyPasswordConfigured", !releaseKeyPassword.isNullOrBlank())
    doLast {
        val configuredValues = mapOf(
            "store file" to inputs.properties["releaseStoreFile"].toString().isNotBlank(),
            "store password" to (inputs.properties["releaseStorePasswordConfigured"] as Boolean),
            "key alias" to (inputs.properties["releaseKeyAliasConfigured"] as Boolean),
            "key password" to (inputs.properties["releaseKeyPasswordConfigured"] as Boolean),
        )
        val missing = configuredValues.filterValues { configured -> !configured }.keys
        check(missing.isEmpty()) {
            "Release signing is incomplete. Missing ${missing.joinToString()}. " +
                "Configure ASSISTANTAI_RELEASE_* environment variables or " +
                "asssistantai.release.* entries in local.properties."
        }
        val configuredStoreFile = inputs.properties["releaseStoreFile"].toString()
        check(File(configuredStoreFile).isFile) {
            "Release signing store file does not exist: $configuredStoreFile"
        }
    }
}

tasks.configureEach {
    if (name.contains("Release") && name != verifyReleaseSigning.name) {
        dependsOn(verifyReleaseSigning)
    }
}

dependencies {
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:speech"))
    implementation(project(":domain:mcp"))
    implementation(project(":domain:workfiles"))
    implementation(project(":domain:permissions"))
    implementation(project(":domain:toolauthorization"))
    implementation(project(":domain:skills"))
    implementation(project(":core:audio"))
    implementation(project(":data:assistant"))
    implementation(project(":domain:assistant"))
    implementation(project(":core:common"))
    implementation(project(":data:modelcatalog"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":data:speech"))
    implementation(project(":data:conversation"))
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
    implementation(project(":feature:assistant"))
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
    ksp(libs.androidx.appfunctions.compiler)
}

// AppFunctions KSP option — collect every @AppFunction declared in this module
// into a single aggregated metadata file the platform reads at runtime.
// Reference: https://developer.android.com/ai/appfunctions
ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}
