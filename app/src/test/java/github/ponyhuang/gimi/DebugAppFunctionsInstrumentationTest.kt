package github.ponyhuang.gimi

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugAppFunctionsInstrumentationTest {

    @Test
    fun debugBuildRegistersShellIdentityInstrumentation() {
        val manifest = File("src/debug/AndroidManifest.xml")

        assertTrue("Debug AndroidManifest.xml should exist", manifest.isFile)
        val content = manifest.readText()
        assertTrue(content.contains(".ShellIdentityInstrumentation"))
        assertTrue(content.contains("android:targetPackage=\"github.ponyhuang.gimi\""))
    }

    @Test
    fun mainBuildDoesNotRegisterShellIdentityInstrumentation() {
        val manifest = File("src/main/AndroidManifest.xml")

        assertTrue("Main AndroidManifest.xml should exist", manifest.isFile)
        assertFalse(manifest.readText().contains("ShellIdentityInstrumentation"))
    }

    @Test
    fun debugInstrumentationAdoptsAppFunctionsShellPermission() {
        val source = File(
            "src/debug/java/github/ponyhuang/gimi/ShellIdentityInstrumentation.kt",
        )

        assertTrue("ShellIdentityInstrumentation.kt should exist", source.isFile)
        val content = source.readText()
        assertTrue(content.contains("adoptShellPermissionIdentity"))
        assertTrue(content.contains("android.permission.EXECUTE_APP_FUNCTIONS"))
        assertTrue(content.contains("CountDownLatch(1).await()"))
    }
}
