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

// Get or register build service, loading properties if needed
// Use shared utility to find files relative to composite root (works for included builds)
val xdkPropertiesService = gradle.sharedServices
    .registerIfAbsent("xdkPropertiesService", XdkPropertiesService::class.java) {
        // Load properties from composite root (not just rootProject, which may be an included build)
        val props = java.util.Properties().apply {
            listOf("gradle.properties", "xdk.properties", "version.properties")
                .map { XdkPropertiesService.compositeRootRelativeFile(project.rootProject.projectDir, it) }
                .filter { it.isFile }
                .forEach { it.inputStream().use(::load) }
        }
        parameters.entries.set(props.stringPropertyNames().associateWith(props::getProperty))
    }
    .get()

val xdkProperties = extensions.create<ProjectXdkProperties>("xdkProperties",providers, xdkPropertiesService)

// Automatically set group and version from properties
// Allow -Pversion override, otherwise use xdk.version from properties
project.group = xdkProperties.stringValue("xdk.group")
project.version = xdkProperties.stringValue("version", xdkProperties.stringValue("xdk.version"))
logger.info("[properties] Versioned '${project.name}': group=${project.group}, version=${project.version}")

// =============================================================================
// JDK toolchain (single source of truth for the entire composite build)
// =============================================================================
// Every project that applies this plugin gets the toolchain wired up here, so
// downstream Java/Kotlin projects don't need their own java { toolchain { ... } }
// blocks. Foojay (configured at the settings level) auto-acquires the JDK if it
// isn't already installed; gradle.properties enables auto-download so this works
// out-of-the-box on any developer machine or CI runner.
//
// Kotlin compilation auto-inherits this toolchain (Kotlin Gradle plugin reads
// java.toolchain when no explicit kotlin.jvmToolchain is set). Build-logic
// projects (common-plugins, aggregator, settings-plugins) bootstrap their own
// toolchain config since they cannot apply this convention plugin to themselves.
val jdkVersionProvider = xdkProperties.int("org.xtclang.java.jdk")

plugins.withType<JavaBasePlugin> {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(jdkVersionProvider.map { JavaLanguageVersion.of(it) })
    }
}

// =============================================================================
// Toolchain divergence guard
// =============================================================================
// Verifies that the resolved java.toolchain.languageVersion matches the
// canonical org.xtclang.java.jdk after project evaluation. This catches:
//   - direct overrides:        `java { toolchain { languageVersion = ... } }`
//   - kotlin shorthand drift:  `kotlin { jvmToolchain(N) }` propagates N to
//                              java.toolchain.languageVersion as well, so a
//                              divergent N surfaces here too.
// Cheaper and more robust than reflecting into the Kotlin extension's typed
// API (which moves between Kotlin Gradle plugin versions).
plugins.withType<JavaBasePlugin> {
    afterEvaluate {
        val expected = jdkVersionProvider.orNull
        val actual = the<JavaPluginExtension>().toolchain.languageVersion.orNull?.asInt()
        if (expected != null && actual != null && actual != expected) {
            throw GradleException(
                "JDK toolchain divergence in project '${project.path}': " +
                    "resolved toolchain languageVersion=$actual but org.xtclang.java.jdk=$expected. " +
                    "Either some build script overrides java.toolchain directly, or a " +
                    "kotlin { jvmToolchain(N) } block sets a different N (which propagates " +
                    "to java.toolchain). Single source of truth is org.xtclang.java.jdk in " +
                    "version.properties.",
            )
        }
    }
}

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

                // Validate all subproject versions match the project version
                val versionMismatches = mutableListOf<String>()

                subprojectVersions.forEach { (group, name, version) ->
                    logger.lifecycle("   ├─ $group:$name:$version")

                    if (version.toString() != projectVersion.toString()) {
                        versionMismatches.add("   ❌ $group:$name:$version (expected: $projectVersion)")
                    }
                }

                // Fail if any version mismatches found
                if (versionMismatches.isNotEmpty()) {
                    logger.lifecycle("")
                    logger.lifecycle("❌ VERSION VALIDATION FAILED")
                    logger.lifecycle("")
                    logger.lifecycle("All subprojects must have the same version as the parent project.")
                    logger.lifecycle("")
                    logger.lifecycle("Mismatches found:")
                    versionMismatches.forEach { logger.lifecycle(it) }
                    logger.lifecycle("")
                    logger.lifecycle("Expected version: $projectVersion")
                    logger.lifecycle("")

                    throw GradleException(
                        "Version validation failed: ${versionMismatches.size} " +
                        "subproject(s) have different versions. All projects must use version $projectVersion"
                    )
                }
            }
            logger.lifecycle("")
        }
    }
}
