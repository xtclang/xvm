/**
 * Convention plugin for IntelliJ IDEA integration.
 *
 * Handles the mess of IntelliJ IDEA's poor Gradle integration:
 * - Enforces correct Java version (prevents Java 6 defaults)
 * - Configures resource directories for IDEA mode
 * - Marks generated directories appropriately
 *
 * Apply this plugin to any project that has IntelliJ-specific issues.
 */

plugins {
    idea
    java
    id("org.xtclang.build.xdk.properties")
}

val jdkVersion = xdkProperties.int("org.xtclang.java.jdk")

idea {
    module {
        // Don't download javadocs (large, rarely needed)
        isDownloadJavadoc = false
        // Do download sources (helpful for debugging)
        isDownloadSources = true

        // Mark common generated directories as generated sources
        // IntelliJ needs these marked to handle them correctly
        val generatedDirs = listOf(
            file("build/generated/resources/main"),
            file("build/generated/sources/annotationProcessor/java/main")
        )

        generatedSourceDirs.addAll(generatedDirs)

        // Ensure these directories exist so IntelliJ doesn't ignore them
        generatedDirs.forEach { it.mkdirs() }
    }
}

// For projects with custom resource generation (like javatools),
// we need to ensure resources are generated before IntelliJ's Make operation.
// This is handled by having the consumer add dependencies to their tasks.
