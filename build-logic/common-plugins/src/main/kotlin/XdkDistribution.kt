import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(project: Project): XdkProjectBuildLogic(project) {
    companion object {
        const val DISTRIBUTION_TASK_GROUP = "distribution"
        const val MAKENSIS = "makensis"

        const val XDK_COMPILER_BINARY_NAME_LEGACY = "xtc"
        const val XDK_COMPILER_BINARY_NAME = "xcc"
        const val XDK_RUNNER_BINARY_NAME = "xec"

        private const val BUILD_NUMBER = "BUILD_NUMBER"
        private const val CI = "CI"

        private val currentOs = OperatingSystem.current()
        private val isCiEnabled = System.getenv(CI) == "true"

        val distributionTasks = listOfNotNull("distTar", "distZip", "distExe")
        val launcherNames = listOfNotNull(XDK_COMPILER_BINARY_NAME_LEGACY, XDK_COMPILER_BINARY_NAME, XDK_RUNNER_BINARY_NAME)
    }

    init {
        logger.info("""
            $prefix Configuring XVM distribution: '$this'
            $prefix   Name        : '$distributionName'
            $prefix   Version     : '$distributionVersion'
            $prefix   Current OS  : '$currentOs'
            $prefix   Environment:
            $prefix       CI             : '$isCiEnabled' (CI property can be overwritten)
            $prefix       GITHUB_ACTIONS : '${System.getenv("GITHUB_ACTIONS") ?: "[not set]"}'
        """.trimIndent())
    }

    val installDirProvider: Provider<Directory> get() = project.layout.buildDirectory.dir("install/xdk")

    val localDistDirProvider = project.compositeRootBuildDirectory.dir("dist")

    val distributionName: String get() = project.name // Default: "xdk"

    val distributionVersion: String get() = buildString {
        append(project.version)
        if (isCiEnabled) {
            val buildNumber = System.getenv(BUILD_NUMBER) ?: ""
            val gitCommitHash = project.executeCommand("git", "rev-parse", "HEAD")
            if (buildNumber.isNotEmpty() || gitCommitHash.isNotEmpty()) {
                logger.warn("This is a CI run, BUILD_NUMBER and git hash must both be available: (BUILD_NUMBER='$buildNumber', commit='$gitCommitHash')")
                return@buildString
            }
            append("-ci-$buildNumber+$gitCommitHash")
        }
    }

    fun resolvePlatformSpecificLauncherFile(localDistDir: Provider<Directory> = localDistDirProvider): RegularFile {
        val launcher = if (currentOs.isMacOsX) {
            "macos_launcher"
        } else if (currentOs.isLinux) {
            "linux_launcher"
        } else if (currentOs.isWindows) {
            "windows_launcher.exe"
        } else {
            throw UnsupportedOperationException("Cannot build distribution for currentOs: $currentOs")
        }
        return localDistDir.get().file("libexec/bin/$launcher")
    }

    fun shouldCreateWindowsDistribution(): Boolean {
        val runDistExe = project.getXdkPropertyBoolean("org.xtclang.install.distExe", false)
        if (runDistExe) {
            logger.info("$prefix 'distExe' task is enabled; will attempt to build Windows installer.")
            if (XdkBuildLogic.findExecutableOnPath(MAKENSIS) == null) {
                throw project.buildException("Illegal configuration; project is set to weave a Windows installer, but '$MAKENSIS' is not on the PATH.")
            }
            return true
        }
        logger.warn("$prefix 'distExe' is disabled for building distributions. Only 'tar.gz' and 'zip' are allowed.")
        return false
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
