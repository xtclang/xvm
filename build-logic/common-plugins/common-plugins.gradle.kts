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

dependencies {
    // Build-logic bootstrap dependencies (can't use main version catalog due to bootstrap order)

    // Testing dependencies
    testImplementation(platform("org.junit:junit-bom:5.13.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}


repositories {
    mavenCentral()
    gradlePluginPortal()
}

logger.info("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
