import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class XdkDistributionTest {

    @Test
    fun `test platform naming consistency`() {
        val project = ProjectBuilder.builder().build()
        val xdkDist = XdkDistribution(project)

        println("=== Current Platform ===")
        println("OS: ${XdkDistribution.getOsName()}")
        println("Arch: ${XdkDistribution.currentArch}")
        println("Platform: ${XdkDistribution.getOsName()}_${XdkDistribution.currentArch}")
        println()

        println("=== Supported Platforms ===")
        XdkDistribution.getSupportedPlatforms().forEach { (os, arch) ->
            val launcher = xdkDist.launcherFileName(os, arch)
            val classifier = xdkDist.osClassifier(os, arch)
            val isCurrent = xdkDist.isCurrentPlatform(os, arch)
            val marker = if (isCurrent) " (CURRENT)" else ""
            println("$os/$arch -> launcher: $launcher, classifier: $classifier$marker")
            
            // Verify naming consistency
            assertTrue(XdkDistribution.isPlatformSupported(os, arch), "Platform $os/$arch should be supported")
            assertTrue(launcher.startsWith("${os}_launcher_$arch"), "Launcher should follow naming convention")
            assertEquals("${os}_$arch", classifier, "Classifier should match OS_ARCH pattern")
        }
        
        // Test architecture normalization
        assertEquals("amd64", XdkDistribution.normalizeArchitecture("x86_64"))
        assertEquals("amd64", XdkDistribution.normalizeArchitecture("amd64"))
        assertEquals("arm64", XdkDistribution.normalizeArchitecture("aarch64"))
        assertEquals("arm64", XdkDistribution.normalizeArchitecture("arm64"))
    }

    @Test
    fun `test launcher filename generation`() {
        val project = ProjectBuilder.builder().build()
        val xdkDist = XdkDistribution(project)

        // Test all supported platforms
        assertEquals("linux_launcher_amd64", xdkDist.launcherFileName("linux", "amd64"))
        assertEquals("linux_launcher_arm64", xdkDist.launcherFileName("linux", "arm64"))
        assertEquals("macos_launcher_arm64", xdkDist.launcherFileName("macos", "arm64"))
        assertEquals("macos_launcher_amd64", xdkDist.launcherFileName("macos", "amd64"))
        assertEquals("windows_launcher_amd64.exe", xdkDist.launcherFileName("windows", "amd64"))
    }

    @Test
    fun `test osClassifier generation`() {
        val project = ProjectBuilder.builder().build()
        val xdkDist = XdkDistribution(project)

        // Test classifier consistency
        assertEquals("linux_amd64", xdkDist.osClassifier("linux", "amd64"))
        assertEquals("linux_arm64", xdkDist.osClassifier("linux", "arm64"))
        assertEquals("macos_arm64", xdkDist.osClassifier("macos", "arm64"))
        assertEquals("windows_amd64", xdkDist.osClassifier("windows", "amd64"))
    }

    @Test
    fun `test Docker platform alignment`() {
        val project = ProjectBuilder.builder().build()
        
        // Verify our architecture naming aligns with Docker platforms
        val supportedPlatforms = XdkDistribution.getSupportedPlatforms()
        
        // Check that we use Docker-standard architecture names
        assertTrue(supportedPlatforms.any { it.second == "amd64" }, "Should support amd64 (Docker standard)")
        assertTrue(supportedPlatforms.any { it.second == "arm64" }, "Should support arm64 (Docker standard)")
        assertFalse(supportedPlatforms.any { it.second == "x86_64" }, "Should not use x86_64 (use amd64 instead)")
        assertFalse(supportedPlatforms.any { it.second == "aarch64" }, "Should not use aarch64 (use arm64 instead)")
    }
}