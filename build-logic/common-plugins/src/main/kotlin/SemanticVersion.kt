import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.findByType
import kotlin.IllegalArgumentException

data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {
    companion object {
        const val XDK_VERSION_CATALOG_GROUP = "xdkgroup"
        const val XDK_VERSION_CATALOG_VERSION = "xdk"
        const val XDK_VERSION_CATALOG_PLUGIN_VERSION = "xtcplugin"
        const val VERSION_CATALOG_LIBS_NAME = "libs"
        const val VERSION_CATALOG_TOML_VERSIONS = "[versions]"

        fun resolveCatalogSemanticVersion(project: Project): SemanticVersion {
            return SemanticVersion(
                resolveCatalogVersion(project, XDK_VERSION_CATALOG_GROUP),
                project.name,
                resolveCatalogVersion(project, XDK_VERSION_CATALOG_VERSION)
            )
        }

        /**
         * Returns the settings phase equivalent of doing the type safe short hand "libs.versions.<name>", when
         * the project has been evaluated.
         */
        private fun resolveCatalogVersion(project: Project, name: String, catalog: String = VERSION_CATALOG_LIBS_NAME): String = project.run {
            extensions.findByType<VersionCatalogsExtension>()?.also { catalogs ->
                val versionCatalog = catalogs.named(catalog)
                val value = versionCatalog.findVersion(name)
                if (value.isPresent) {
                    return value.get().toString()
                }
            }
            throw buildException("Version catalog entry '$name' has no value for '$catalog:$name'")
        }
    }

    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
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
