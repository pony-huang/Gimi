plugins {
    id("gimi.android.library")
    id("gimi.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "github.ponyhuang.gimi.data.conversation"
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":domain:a2ui"))
    implementation(project(":core:storage"))
    implementation(project(":core:security"))
    implementation(project(":domain:conversation"))
    implementation(project(":domain:mcp"))
    implementation(project(":domain:modelcatalog"))
    implementation(project(":domain:toolauthorization"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.google.adk.kotlin.core) {
        exclude(group = "io.modelcontextprotocol.sdk")
    }
    implementation(libs.openai.java)
    implementation(libs.okhttp)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
