/**
 * XDK Properties Convention Plugin
 *
 * Sets up the ProjectXdkProperties extension for any project that needs access to XDK properties.
 * This provides unified access to properties from gradle.properties, xdk.properties, and version.properties
 * with proper configuration cache support.
 *
 * Usage:
 *   plugins {
 *       id("org.xtclang.build.xdk.properties")
 *   }
 *
 * Then access properties:
 *   val jdk = xdkProperties.int("org.xtclang.java.jdk")
 */

// Get or register build service, loading properties if needed (for root build)
val xdkPropertiesService = gradle.sharedServices
    .registerIfAbsent("xdkPropertiesService", XdkPropertiesService::class.java) {
        // Load properties if service is being created (happens for root build)
        val props = java.util.Properties().apply {
            listOf("gradle.properties", "xdk.properties", "version.properties")
                .map { project.rootProject.file(it) }
                .filter { it.isFile }
                .forEach { it.inputStream().use(::load) }
        }
        parameters.entries.set(props.stringPropertyNames().associateWith(props::getProperty))
    }
    .get()

val xdkProperties = extensions.create<ProjectXdkProperties>(
    "xdkProperties",
    providers,
    xdkPropertiesService
)

// Automatically set group and version from properties
project.group = xdkProperties.stringValue("xdk.group")
project.version = xdkProperties.stringValue("xdk.version")

/**
 * Task to print version information for this project and all subprojects.
 * Only create if it doesn't already exist (aggregator may have created it in root build).
 */
if ("versions" !in tasks.names) {
    val versions by tasks.registering {
        group = "help"
        description = "Print group:name:version for this project and all subprojects"

        // Capture values during configuration for configuration cache compatibility
        val projectName = project.name
        val projectGroup = project.group
        val projectVersion = project.version
        val subprojectVersions = project.subprojects
            .sortedBy { it.name }
            .map { Triple(it.group, it.name, it.version) }

        doLast {
            logger.lifecycle("\n📦 Build: $projectName")
            logger.lifecycle("   $projectGroup:$projectName:$projectVersion")

            if (subprojectVersions.isNotEmpty()) {
                logger.lifecycle("\n   Subprojects:")
                subprojectVersions.forEach { (group, name, version) ->
                    logger.lifecycle("   ├─ $group:$name:$version")
                }
            }
            logger.lifecycle("")
        }
    }
}
