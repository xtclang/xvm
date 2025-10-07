import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Lightweight versioning plugin that assigns group and version from version catalog.
 * The version catalog is patched by settings plugin with values from version.properties.
 */

// Ensure project hasn't been versioned yet
require(project.version == Project.DEFAULT_VERSION) {
    "Project '${project.name}' is already versioned as ${project.version}. " +
    "The versioning plugin should only be applied once."
}

// Get version catalog (patched by settings plugin with xdk.version and xdk.group)
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

val resolvedGroup = libsCatalog.findVersion("group-xdk").get().requiredVersion
val resolvedVersion = libsCatalog.findVersion("xdk").get().requiredVersion

// Validate versions are not placeholders
require(!resolvedVersion.contains("PLACEHOLDER")) {
    "Version catalog not patched: xdk='$resolvedVersion'. Ensure common settings plugin is applied."
}
require(!resolvedGroup.contains("PLACEHOLDER")) {
    "Version catalog not patched: group-xdk='$resolvedGroup'. Ensure common settings plugin is applied."
}

// Assign to project
project.group = resolvedGroup
project.version = resolvedVersion

logger.lifecycle("[versioning] Versioned '${project.name}' as $resolvedGroup:${project.name}:$resolvedVersion")
