import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType

class XdkVersionHandler(project: Project): XdkProjectBuildLogic(project) {
    companion object {
        private const val XDK_VERSION_CATALOG_NAME = "libs"
        private const val XDK_VERSION_CATALOG_VERSION = "xdk"
        private const val XDK_VERSION_CATALOG_GROUP = "group-xdk"
        private const val XTC_VERSION_PLUGIN_CATALOG_VERSION = "xtc-plugin"
        private const val XTC_VERSION_PLUGIN_CATALOG_GROUP = "group-xtc-plugin"
        private const val XDK_ROOT_PROJECT_NAME = "xvm"

        fun <T : Dependency> semanticVersionFor(dependency: Provider<T>): SemanticVersion {
            with(dependency.get()) {
                return SemanticVersion(group!!, name, version!!)
            }
        }
    }

    fun assignSemanticVersionFromCatalog(): SemanticVersion {
        val verXdk = catalogSemanticVersion(XDK_VERSION_CATALOG_GROUP, XDK_VERSION_CATALOG_VERSION)
        val verXtcPlugin = catalogSemanticVersion(XTC_VERSION_PLUGIN_CATALOG_GROUP, XTC_VERSION_PLUGIN_CATALOG_VERSION)
        if (verXdk != verXtcPlugin) {
            throw project.buildException("Illegal state: version mismatch between XDK and XTC plugin: '$verXdk' != '$verXtcPlugin'")
        }
        return assignSemanticVersion(catalogSemanticVersion(XDK_VERSION_CATALOG_GROUP, XDK_VERSION_CATALOG_VERSION))
    }

    private fun assignSemanticVersion(semanticVersion: SemanticVersion): SemanticVersion {
        val (group, name, version) = semanticVersion

        ensureNotVersioned(project)

        if (project.name != name) {
            throw project.buildException("Illegal state: project name '${project.name}' does not match the name in the semantic version: '$name'")
        }

        project.group = group
        project.version = version

        if (name == XDK_ROOT_PROJECT_NAME) {
            return semanticVersion
        }

        logger.lifecycle("$prefix XDK Project '$name' versioned as: '$semanticVersion'")
        with (project) {
            logger.info("""
                $prefix    project.group  : $group
                $prefix    project.name   : $name
                $prefix    project.version: $version
            """.trimIndent()
            )
        }

        return semanticVersion
    }

    private fun catalogSemanticVersion(catalogGroup: String, catalogVersion: String): SemanticVersion {
        // Try to resolve group and version to assign for an unversioned project in this repo (XDK).
        return SemanticVersion(catalogVersion(catalogGroup), project.name, catalogVersion(catalogVersion))
    }

    /**
     * Returns the settings phase equivalent of doing the type safe shorthand "libs.versions.<name>", when
     * the project has been evaluated.
     */
    private fun catalogVersion(name: String, catalog: String = XDK_VERSION_CATALOG_NAME): String {
        // TODO: Do not pass projects to companion object functions. This is usually just a contamination to get the logger, and we
        //   should avoid that. This is the same kind of code smell as static methods that take an instance to operate on as first
        //   parameter in Java.
        project.extensions.findByType<VersionCatalogsExtension>()?.also { catalogs ->
            val versionCatalog = catalogs.named(catalog)
            val value = versionCatalog.findVersion(name)
            if (value.isPresent) {
                logger.info("$prefix Version catalog '$catalog': '$name' = '${value.get()}'")
                return value.get().toString()
            }
        }
        throw project.buildException("Version catalog entry '$name' has no value for '$catalog:$name'")
    }

    private fun ensureNotVersioned(project: Project): Unit = project.run {
        val group = group.toString()
        val version = version.toString()
        val hasGroup = group.isNotEmpty() // Not always empty by default. Can be parent project hierarchy too.
        val hasVersion = Project.DEFAULT_VERSION == version
        if ((hasGroup || hasVersion) && group.indexOf('.') != -1) {
            logger.warn("$prefix Project '$name' is not expected to have hierarchical group and version configured at init: (version: group='$group', name='$name', version='$version')")
        }
    }
}
