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
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0")
}
