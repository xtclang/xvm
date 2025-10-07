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
