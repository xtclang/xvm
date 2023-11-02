import org.gradle.api.Project

data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {
    companion object {
        const val XDK_VERSION_CATALOG_GROUP = "xdkgroup"
        const val XDK_VERSION_CATALOG_VERSION = "xdk"
        const val XDK_VERSION_CATALOG_PLUGIN_VERSION = "xtcplugin"
    }

    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
    }

    fun bump(project: Project, toSnapshot: Boolean = true): SemanticVersion {
        // TODO: Snaoshots and non numbers.
        return runCatching {
            val lastDot = artifactVersion.lastIndexOf('.')
            if (lastDot == -1) {
                throw project.buildException("Illegal version format: '$artifactVersion'")
            }
            val majorMinorVersion = artifactVersion.substring(0, lastDot)
            val patchVersionFull = artifactVersion.substring(lastDot + 1)
            val patchVersion = patchVersionFull.trim { !it.isDigit() }
            val nextPatchVersion = patchVersion.toInt() + 1
            if (isSnapshot()) {
                project.logger.warn("${project.prefix} WARNING: Bumping snapshot version. Is this intentional?")
            }
            val next = SemanticVersion(
                artifactGroup,
                artifactId,
                buildString {
                    append(majorMinorVersion)
                    append('.')
                    append(nextPatchVersion)
                    if (toSnapshot) {
                        append("-SNAPSHOT")
                    }
                })
            project.logger.lifecycle("${project.prefix} Bumping project version from '$this' to '$next'")
            next
        }.getOrThrow()
    }

    fun isSnapshot(): Boolean {
        return artifactVersion.endsWith("-SNAPSHOT")
    }
}
