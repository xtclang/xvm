import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(buildLogic: XdkBuildLogic) {
    companion object {
        const val DISTRIBUTION_GROUP = "distribution"

        private const val BUILD_NUMBER = "BUILD_NUMBER"
        private const val CI = "CI"

        private val CURRENT_OS = OperatingSystem.current()
        private val CI_ENABLED = System.getenv(CI) == "true"
    }

    private val project = buildLogic.project
    private val logger = project.logger
    private val prefix = project.prefix

    // Default: "xdk"
    val distributionName: String get() = project.name

    val distributionVersion: String get() = buildString {
        append(project.version)
        if (CI_ENABLED) {
            val buildNumber = System.getenv(BUILD_NUMBER) ?: ""
            val gitCommitHash = project.executeCommand("git", "rev-parse", "HEAD") ?: ""
            if (buildNumber.isNotEmpty() || gitCommitHash.isNotEmpty()) {
                logger.warn("This is a CI run, BUILD_NUMBER and git hash must both be available: (BUILD_NUMBER='$buildNumber', commit='$gitCommitHash')")
                return@buildString
            }
            append("-ci-$buildNumber+$gitCommitHash")
        }
    }.also {
        logger.lifecycle("$prefix Configuration XVM distributionVersion for build: '$it'")
    }

    init {
        logger.lifecycle("""
            $prefix Configuring XVM distribution: '$this'
            $prefix   distributionName:    '$distributionName'
            $prefix   distributionVersion: '$distributionVersion'
            $prefix   current OS:          '$CURRENT_OS'
            $prefix   env:
            $prefix       CI:                  '$CI_ENABLED' (CI property can be overwritten)
            $prefix       GITHUB_ACTIONS:      '${System.getenv("GITHUB_ACTIONS") ?: "[not set]"}'
        """.trimIndent())
    }

    fun getLocalDistBackupDir(localDistVersion: String): Provider<Directory> {
        return project.layout.buildDirectory.dir("localDistBackup/$localDistVersion")
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
