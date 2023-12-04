import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(project: Project): XdkProjectBuildLogic(project) {
    companion object {
        const val DISTRIBUTION_GROUP = "distribution"

        private const val BUILD_NUMBER = "BUILD_NUMBER"
        private const val CI = "CI"
        private const val LOCALDIST_BACKUP_DIR = "localdist-backup"

        private val CURRENT_OS = OperatingSystem.current()
        private val CI_ENABLED = System.getenv(CI) == "true"
    }

    init {
        logger.lifecycle("""
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

    fun getLocalDistBackupDir(localDistVersion: String): Provider<Directory> {
        return project.layout.buildDirectory.dir("$LOCALDIST_BACKUP_DIR/$localDistVersion")
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
