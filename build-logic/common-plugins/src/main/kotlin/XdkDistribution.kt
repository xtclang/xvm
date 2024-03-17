import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(project: Project): XdkProjectBuildLogic(project) {
    companion object {
        const val DISTRIBUTION_TASK_GROUP = "distribution"
        const val MAKENSIS = "makensis"
        const val JAVATOOLS_PREFIX_PATTERN = "**/javatools*"
        const val JAVATOOLS_INSTALLATION_NAME : String = "javatools.jar"

        private const val BUILD_NUMBER = "BUILD_NUMBER"
        private const val CI = "CI"

        private val isCiEnabled = System.getenv(CI) == "true"

        val currentOs: OperatingSystem = OperatingSystem.current()
        val distributionTasks = listOf("distTar", "distZip", "distExe", "withLaunchersDistTar", "withLaunchersDistZip")

        fun isDistributionArchiveTask(task: Task): Boolean {
            return task.group == DISTRIBUTION_TASK_GROUP && task.name in distributionTasks
        }
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

    @Suppress("MemberVisibilityCanBePrivate") // No it can't, IntelliJ
    val distributionName: String get() = project.name // Default: "xdk"

    @Suppress("MemberVisibilityCanBePrivate") // No it can't, IntelliJ
    val distributionVersion: String get() = buildString {
        append(project.version)
        if (isCiEnabled) {
            val buildNumber = System.getenv(BUILD_NUMBER) ?: ""
            if (buildNumber.isNotEmpty() || distributionCommit.isNotEmpty()) {
                logger.warn("This is a CI run, BUILD_NUMBER and git hash must both be available: (BUILD_NUMBER='$buildNumber', commit='$distributionCommit')")
                return@buildString
            }
            append("-ci-$buildNumber+${distributionCommit}")
        }
    }

    val distributionCommit: String get() = buildString {
        return project.executeCommand("git", "rev-parse", "HEAD")
    }

    fun configScriptFilename(installDir: Provider<Directory>): RegularFile {
        val config = if (currentOs.isMacOsX) {
            "cfg_macos.sh"
        } else if (currentOs.isLinux) {
            "cfg_linux.sh"
        } else if (currentOs.isWindows) {
            "cfg_windows.bat"
        } else {
            throw UnsupportedOperationException("Cannot find launcher config script for currentOs: $currentOs")
        }
        return installDir.get().file(config)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun launcherFileName(): String {
        return if (currentOs.isMacOsX) {
            "macos_launcher"
        } else if (currentOs.isLinux) {
            "linux_launcher"
        } else if (currentOs.isWindows) {
            "windows_launcher.exe"
        } else {
            throw UnsupportedOperationException("Cannot build distribution for currentOs: $currentOs")
        }
    }

    fun resolveLauncherFile(localDistDir: Provider<Directory>): RegularFile {
        return localDistDir.get().file("bin/${launcherFileName()}")
    }

    fun osClassifier(): String {
        val arch = System.getProperty("os.arch")
        return when {
            currentOs.isMacOsX -> "macos_$arch"
            currentOs.isLinux -> "linux_$arch"
            currentOs.isWindows -> "windows_$arch"
            else -> throw UnsupportedOperationException("Cannot build distribution for currentOs: $currentOs")
        }
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
        logger.info("$prefix 'distExe' is disabled for building distributions. Only 'tar.gz' and 'zip' are allowed.")
        return false
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
