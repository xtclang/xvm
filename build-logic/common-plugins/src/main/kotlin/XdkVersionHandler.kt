import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.provideDelegate
import java.io.File

// TODO All these should have an abstract common superclass, instantiated with an XdkBuildLogic instance/and or a project, that creates it itself.
// TODO We can simplify this quite a bit by propagating the version as gradle properties from the composite root. The toml file is kind of
//   overkill to get updated, if we can read the gradle.property version and use it in the file.
class XdkVersionHandler(buildLogic: XdkBuildLogic) {
    companion object {
        const val XDK_VERSION_CATALOG_VERSION_SECTION = "[versions]"

        private const val XDK_VERSION_CATALOG_NAME = "libs"
        private const val XDK_VERSION_CATALOG_VERSION = "xdk"
        private const val XDK_VERSION_CATALOG_GROUP = "xdk-group"
        private const val XDK_VERSION_CATALOG_PLUGIN_VERSION = "xtc-plugin"

        fun <T: Dependency> semanticVersionFor(dependency: Provider<T>): SemanticVersion {
            with (dependency.get()) {
                return SemanticVersion(group!!, name, version!!)
            }
        }

        fun resolveCatalogSemanticVersion(project: Project): SemanticVersion {
            // Try to resolve group and version to assign for an unversioned project in this repo (XDK).
            return SemanticVersion(
                resolveCatalogVersion(project, XDK_VERSION_CATALOG_GROUP),
                project.name,
                resolveCatalogVersion(project, XDK_VERSION_CATALOG_VERSION))
        }

        /**
         * Returns the settings phase equivalent of doing the type safe shorthand "libs.versions.<name>", when
         * the project has been evaluated.
         */
        private fun resolveCatalogVersion(project: Project, name: String, catalog: String = XDK_VERSION_CATALOG_NAME): String = project.run {
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

    private val project = buildLogic.project
    private val logger = project.logger
    private val prefix = project.prefix
    private val versionCatalogToml: File get() = File("${project.compositeRootProjectDirectory}/gradle/libs.versions.toml")

    // TODO if we can fold these functions into the XdkBuildLogic class, it would be a lot nicer. (TODO: partially done)
    fun assignXdkVersion(semanticVersion: SemanticVersion): SemanticVersion {
        val (group, name, version) = semanticVersion

        ensureNotVersioned(project)

        if (project.name != name) {
            throw project.buildException("Illegal state: project name '${project.name}' does not match the name in the semantic version: '$name'")
        }

        project.group = group
        project.version = version

        logger.lifecycle("$prefix XDK Project '$name' versioned as: '$semanticVersion'")
        with (project) {
            logger.info(
                """
                $prefix XDK Project '$name' versioned as: '$semanticVersion'
                $prefix    project.group  : $group
                $prefix    project.name   : $name
                $prefix    project.version: $version
            """.trimIndent()
            )
        }

        return semanticVersion
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

    /**
     * Overwrite the version reference to all versioned XDK components, by modifying the
     * TOML file that hold the version catalog of this repo.
     */
    fun updateVersionCatalogFile(toSnapshot: Boolean): Unit = project.run  {
        val semanticVersion: SemanticVersion by extra
        val change = semanticVersion to semanticVersion.bump(toSnapshot)
        logger.lifecycle("$prefix '${project.name}' (upgrading '${change.first}' to '${change.second}')")
        versionCatalogToml.updateVersionCatalog(change)
        logger.lifecycle(
            """
            $prefix bumpProjectVersion updated TOML file: '$versionCatalogToml'
            $prefix IMPORTANT: In some cases you may need to do a full clean and rebuild of the XDK repository."
        """.trimIndent())
    }

    private fun File.updateVersionCatalog(change: Pair<SemanticVersion, SemanticVersion>): List<Pair<String, String>> {
        val (current, next) = change
        val ls = System.lineSeparator()
        val changedLines = mutableListOf<Pair<String, String>>()
        val oldLines = readLines().map { it.trim() }
        val newLines = buildList {
            var section = ""
            for (it in oldLines) {
                if (it.isEmpty()) {
                    add(it)
                    continue
                }

                val first = it[0]
                val last = it[it.length - 1]
                if (first == '[' && last == ']') {
                    section = it
                    add(it)
                    continue
                }

                if (section != XDK_VERSION_CATALOG_VERSION_SECTION) {
                    add(it)
                    continue
                }

                when (val split = it.split("=")[0].trim()) {
                    XDK_VERSION_CATALOG_VERSION, XDK_VERSION_CATALOG_PLUGIN_VERSION -> {
                        if (!it.contains(current.artifactVersion)) {
                            throw project.buildException("ERROR: Failed to find current version '$current' in version catalog entry: $it")
                        }
                        val updated = "$split = \"${next.artifactVersion}\""
                        add(updated)
                        changedLines.add(it to updated)
                    }

                    else -> add(it)
                }
            }
        }

        if (changedLines.isEmpty()) {
            throw project.buildException("ERROR: Failed to replace any version strings for '$current' in toml: '$absolutePath'")
        }

        assert(oldLines.size == newLines.size)
        logger.lifecycle("$prefix bumpProjectVersion changed TOML entry '$current' to '$next'.")
        changedLines.forEach {
            logger.lifecycle("$prefix Changed line: $it")
        }.also {
            writeText(newLines.joinToString(ls, postfix = ls))
            return changedLines
        }
    }
}
