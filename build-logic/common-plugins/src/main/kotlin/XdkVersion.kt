import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.findByType
import org.jetbrains.kotlin.gradle.plugin.extraProperties

data class SemanticVersion(val artifactGroup: String, val artifactId: String, val artifactVersion: String) {
    override fun toString(): String {
        return "$artifactGroup:$artifactId:$artifactVersion"
    }
}

class XdkVersionResolver(val project: Project) {
    private val semanticVersion: SemanticVersion = resolveProjectVersion()

    fun getSemanticVersion(): SemanticVersion {
        return semanticVersion
    }

    private fun resolveProjectVersion(groupKey: String = "xdk-group", versionKey: String = "xdk"): SemanticVersion {
        return SemanticVersion(
            resolveCatalogVersion(project, groupKey),
            project.name,
            resolveCatalogVersion(project, versionKey)
        )
    }

    private fun resolveCatalogVersion(project: Project, name: String, catalog: String = "libs"): String = project.run {
        extensions.findByType<VersionCatalogsExtension>()?.also { catalogs ->
            val versionCatalog = catalogs.named(catalog)
            val value = versionCatalog.findVersion(name)
            if (value.isPresent) {
                return value.get().toString()
            }
        }
        throw buildException("Project '$name':  Version catalog has no project version and/or group for '$catalog:$name'")
    }
}


fun Project.hasVersion(): Boolean {
    return version != Project.DEFAULT_VERSION
}

fun Project.isUnversioned(): Boolean {
    return !hasVersion()
}

fun Project.isSnapshotVersion(): Boolean {
    if (isUnversioned()) {
        throw buildException("$prefix is not version, XDK subprojects should inherit version from XDK.")
    }
    return version.toString().endsWith("-SNAPSHOT")
}

fun Project.ensureXdkProjectVersion(): SemanticVersion {
    val resolver = XdkVersionResolver(project);
    val semanticVersion = resolver.getSemanticVersion()
    group = semanticVersion.artifactGroup
    version = semanticVersion.artifactVersion
    assert(project.name == semanticVersion.artifactId)
    project.extraProperties.set("xdk.version.semantic", semanticVersion)
    logger.lifecycle("$prefix Ensure project group and version; '$name' assigned semantic version: '$semanticVersion'")
    if (isSnapshotVersion()) {
        logger.warn("$prefix WARNING; project '$name' is a SNAPSHOT version ($version), and cannot be published to non-local repos.")
    }
    return semanticVersion
}
