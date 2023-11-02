import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(buildLogic: XdkBuildLogic) {
    companion object {
        const val DISTRIBUTION_GROUP = "distribution"
        const val BUILD_NUMBER = "BUILD_NUMBER"
        const val CI = "CI"

        private val CURRENT_OS = OperatingSystem.current()
    }

    private val project = buildLogic.project
    private val logger = project.logger
    private val prefix = project.prefix

    private val distributionVersion: String = buildString {
        fun isCiBuild(): Boolean {
            return System.getenv(CI) != null
        }

        fun getBuildNumber(): String? {
            return System.getenv(BUILD_NUMBER)
        }

        fun getLatestGitCommit(): String? {
            return project.executeCommand("git", "rev-parse", "HEAD")
        }

        fun getCiTag(): String {
            if (!isCiBuild()) {
                return ""
            }
            val buildNumber = getBuildNumber()
            val gitCommit = getLatestGitCommit()
            if (buildNumber == null || gitCommit == null) {
                logger.error("$prefix Cannot resolve CI build tag (buildNumber=$buildNumber, commit=$gitCommit)")
                return ""
            }
            return "-ci-$buildNumber+$gitCommit".also {
                logger.lifecycle("$prefix Configuration XVM distribution for CI build: '$it'")
            }
        }

        append(project.version)
        append(getCiTag())
    }

    val distributionName: String = project.name

    init {
        logger.lifecycle("$prefix Configured XVM distribution: $this (distribution version: '$distributionVersion', target os: '$CURRENT_OS')")
    }

    fun getLocalDistBackupDir(localDistVersion: String): Provider<Directory> {
        return project.layout.buildDirectory.dir("localDistBackup/$localDistVersion")
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
