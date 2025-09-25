/**
 * This is the build script for the precompile script build-logic plugin.
 */

plugins {
    `kotlin-dsl`
}

// Prevents Kotlin Gradle Plugin "Gradle 8.0" JVM Toolchain warning
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt()))
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.info("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
