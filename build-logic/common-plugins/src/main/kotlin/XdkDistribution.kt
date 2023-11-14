import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem

class XdkDistribution(buildLogic: XdkBuildLogic) {
    companion object {
        const val DISTRIBUTION_GROUP = "distribution"

        private const val BUILD_NUMBER = "BUILD_NUMBER"
        private const val CI = "CI"

        private val CURRENT_OS = OperatingSystem.current()
    }

    val distributionName: String get() = project.name
    val distributionVersion: String get() = buildString {
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

    private val project: Project
    private val logger: Logger
    private val prefix: String

    init {
        this.project = buildLogic.project
        this.logger = project.logger
        this.prefix = project.prefix
        logger.lifecycle("$prefix Configured XVM distribution: $this (distribution version: '$distributionVersion', target OS: '$CURRENT_OS')")
    }

    fun getLocalDistBackupDir(localDistVersion: String): Provider<Directory> {
        return project.layout.buildDirectory.dir("localDistBackup/$localDistVersion")
    }

    override fun toString(): String {
        return "$distributionName-$distributionVersion"
    }
}
