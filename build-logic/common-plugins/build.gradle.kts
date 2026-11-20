/**
 * Unified build script for all build-logic convention plugins.
 */

plugins {
    `kotlin-dsl`
}

// Access properties from XdkPropertiesService for toolchain configuration
// Note: Can't use xdk.properties plugin here since it's defined in this project
val xdkPropertiesService = gradle.sharedServices.registrations.named("xdkPropertiesService").get().service.get() as XdkPropertiesService
val jdkVersion = providers.provider {
    xdkPropertiesService.get("org.xtclang.java.jdk")?.toInt()
        ?: error("org.xtclang.java.jdk not found")
}

// Kotlin auto-inherits this toolchain (Kotlin Gradle plugin reads java.toolchain
// when no explicit kotlin.jvmToolchain is set), so a separate kotlin block would
// be redundant.
java {
    toolchain {
        languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// The coordinates of a [plugins] alias's plugin marker, which depends on the plugin's implementation. Build-logic
// takes a plugin through the same catalog entry that other builds apply it with.
fun pluginMarker(plugin: Provider<PluginDependency>): Provider<String> =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version.requiredVersion}" }

dependencies {
    implementation("org.xtclang.build:settings-plugins")

    // Publishing plugin dependencies - from libs.versions.toml
    implementation(pluginMarker(libs.plugins.vanniktech.maven.publish))

    // Archive handling for native library builds (XZ/tar support)
    implementation(libs.apache.commons.compress)
    implementation(libs.tukaani.xz)

    // git-version: compileOnly so the convention plugin can reference the
    // public VersionDetails interface directly (no reflection). The plugin
    // itself is applied by consumers (e.g. docker/build.gradle.kts).
    compileOnly(pluginMarker(libs.plugins.palantir.git.version))
    compileOnly(platform(libs.guava.bom))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Hold these local plugins to the stricter validation Gradle applies to published plugins.
tasks.validatePlugins {
    enableStricterValidation = true
}
