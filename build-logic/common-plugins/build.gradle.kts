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
val kotlinJdkVersion = providers.provider {
    xdkPropertiesService.get("org.xtclang.kotlin.jdk")?.toInt()
        ?: error("org.xtclang.kotlin.jdk not found")
}

java {
    toolchain {
        languageVersion.set(jdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(kotlinJdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.xtclang.build:settings-plugins")

    // Publishing plugin dependencies - versions from libs.versions.toml
    implementation("com.vanniktech:gradle-maven-publish-plugin:${libs.versions.vanniktech.maven.publish.get()}")

    // Archive handling for native library builds (XZ/tar support)
    implementation("org.apache.commons:commons-compress:${libs.versions.apache.commons.compress.get()}")
    implementation("org.tukaani:xz:${libs.versions.tukaani.xz.get()}")
}
