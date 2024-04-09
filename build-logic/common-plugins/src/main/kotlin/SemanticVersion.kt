import kotlin.IllegalArgumentException
import org.gradle.api.Project

data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {

    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
    }

    fun getGitHubLabel(project: Project): GitLabel {
        return GitLabel(project, this)
    }

    /**
     * Increase the microVersion with one, and potentially add or remove a SNAPSHOT suffix from
     * this semantic version.
     *
     * @return New SemanticVersion, as SemanticVersion objects are immutable.
     */
    fun bump(toSnapshot: Boolean = true): SemanticVersion {
        return run {
            val lastDot = artifactVersion.lastIndexOf('.')
            if (lastDot == -1) {
                throw IllegalArgumentException("Illegal version format: '$artifactVersion'")
            }
            val majorMinorVersion = artifactVersion.substring(0, lastDot)
            val microVersionFull = artifactVersion.substring(lastDot + 1)
            val microVersion = microVersionFull.trim { !it.isDigit() }
            val nextMicroVersion = microVersion.toInt() + 1
            if (isSnapshot()) {
                throw IllegalArgumentException("Bumping semantic version that is already a snapshot. Is this intentional?")
            }
            val next = SemanticVersion(
                artifactGroup,
                artifactId,
                buildString {
                    append(majorMinorVersion)
                    append('.')
                    append(nextMicroVersion)
                    if (toSnapshot) {
                        append("-SNAPSHOT")
                    }
                })
            next
        }
    }

    fun isSnapshot(): Boolean {
        return artifactVersion.endsWith("-SNAPSHOT")
    }
}
