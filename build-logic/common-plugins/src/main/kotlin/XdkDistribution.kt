import XdkBuildLogic.Companion.getDateTimeStampWithTz
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(project: Project): XdkProjectBuildLogic(project) {
    companion object {
        const val DISTRIBUTION_TASK_GROUP = "distribution"
        const val MAKENSIS = "makensis"

        private const val BUILD_NUMBER = "BUILD_NUMBER"
        private const val CI = "CI"
        private const val LOCALDIST_BACKUP_DIR = "localdist-backup"

        private val CURRENT_OS = OperatingSystem.current()
        private val CI_ENABLED = System.getenv(CI) == "true"

        val distributionTasks = listOfNotNull("distTar", "distZip", "distExe")
    }

    init {
        logger.info("""
            $prefix Configuring XVM distribution: '$this'
            $prefix   Name        : '$distributionName'
            $prefix   Version     : '$distributionVersion'
            $prefix   Current OS  : '$CURRENT_OS'
            $prefix   Environment:
            $prefix       CI             : '$CI_ENABLED' (CI property can be overwritten)
            $prefix       GITHUB_ACTIONS : '${System.getenv("GITHUB_ACTIONS") ?: "[not set]"}'
        """.trimIndent())
    }

    val distributionName: String get() = project.name // Default: "xdk"

    val distributionVersion: String get() = buildString {
        append(project.version)
        if (CI_ENABLED) {
            val buildNumber = System.getenv(BUILD_NUMBER) ?: ""
            val gitCommitHash = XdkBuildLogic.executeCommand(project, "git", "rev-parse", "HEAD")
            if (buildNumber.isNotEmpty() || gitCommitHash.isNotEmpty()) {
                logger.warn("This is a CI run, BUILD_NUMBER and git hash must both be available: (BUILD_NUMBER='$buildNumber', commit='$gitCommitHash')")
                return@buildString
            }
            append("-ci-$buildNumber+$gitCommitHash")
        }
    }

    fun getLocalDistBackupDir(localDistPath: String): Provider<Directory> {
        val path = "$LOCALDIST_BACKUP_DIR/$localDistPath/${getDateTimeStampWithTz().replace(' ', '-')}"
        return project.layout.buildDirectory.dir(path)
    }

    fun shouldPublishPluginToLocalDist(): Boolean {
        return project.getXdkPropertyBoolean("org.xtclang.publish.localDist", false)
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
