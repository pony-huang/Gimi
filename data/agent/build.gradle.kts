plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "github.ponyhuang.asssistantai.data.agent"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 35 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":data:modelcatalog"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:mcp"))
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:toolauthorization"))
    implementation(project(":domain:workfiles"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.anthropic.java)
    implementation(libs.google.adk.kotlin.core)
    implementation(libs.openai.java)
    implementation(libs.okhttp)
    implementation(libs.mcp.kotlin.sdk.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.gson)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.appfunctions)
    implementation(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.google.adk.kotlin.processor)
    ksp(libs.hilt.android.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.google.adk.kotlin.webserver)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}

ksp {
    arg("appfunctions:aggregateAppFunctions", "true")
}
