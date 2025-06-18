/**
 * This is the build script for the precompile script build-logic plugin.
 */

plugins {
    `kotlin-dsl`
    kotlin("jvm") version "2.1.20"
}

dependencies {
    // the "latest.release" version dependency broke the build on 12/12/2024
    // implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:latest.release")

    // reverted to the previously working version
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}


repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.info("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
