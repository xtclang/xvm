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
