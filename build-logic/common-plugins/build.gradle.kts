/**
 * Unified build script for all build-logic convention plugins.
 */

plugins {
    `kotlin-dsl`
}

val kotlinDslJavaVersion = JavaLanguageVersion.of(24)

java {
    toolchain {
        languageVersion.set(kotlinDslJavaVersion)
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(kotlinDslJavaVersion)
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0")
}

logger.info("[${project.name}] Gradle version: v${gradle.gradleVersion} (embedded Kotlin: v$embeddedKotlinVersion).")
