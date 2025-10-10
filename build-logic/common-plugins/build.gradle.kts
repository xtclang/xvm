/**
 * Unified build script for all build-logic convention plugins.
 */

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.xtclang.build:settings-plugins")

    // Publishing plugin dependencies - versions from libs.versions.toml
    implementation("com.vanniktech:gradle-maven-publish-plugin:${libs.versions.vanniktech.maven.publish.get()}")
}
