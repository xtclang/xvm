import java.util.Properties
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

// Load version properties from the root version.properties file
val versionProps = Properties().apply {
    file("../../version.properties").inputStream().use { load(it) }
}

val xdkVersion: String = versionProps.getProperty("xdk.version", "0.0.0-SNAPSHOT")
val releaseChannel: String = versionProps.getProperty("xdk.intellij.release.channel", "alpha")

group = "org.xtclang"
version = xdkVersion

// Publishing is disabled by default. Enable with: ./gradlew publishPlugin -PenablePublish=true
val enablePublish = project.findProperty("enablePublish")?.toString()?.toBoolean() ?: false

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.gradle")
        pluginVerifier()
    }
}

// Use JDK 24 for Kotlin (matches org.xtclang.kotlin.jdk from version.properties)
val kotlinJdkVersion = versionProps.getProperty("org.xtclang.kotlin.jdk", "24").toInt()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(kotlinJdkVersion))
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(kotlinJdkVersion))
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "org.xtclang.idea"
        name = "XTC Language Support"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "251" // IntelliJ 2025.1+
            untilBuild = provider { null } // No upper bound - compatible with future versions
        }

        changeNotes = """
            <h2>$xdkVersion</h2>
            <ul>
                <li>Initial alpha release</li>
                <li>New Project wizard for XTC projects</li>
                <li>Run configurations for XTC applications</li>
                <li>File type support for .x files</li>
            </ul>
        """.trimIndent()
    }

    signing {
        // Plugin signing is optional but recommended for production releases
        // Set these environment variables to enable signing:
        //   JETBRAINS_CERTIFICATE_CHAIN - Base64-encoded certificate chain
        //   JETBRAINS_PRIVATE_KEY - Base64-encoded private key
        //   JETBRAINS_PRIVATE_KEY_PASSWORD - Private key password
        certificateChain = providers.environmentVariable("JETBRAINS_CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("JETBRAINS_PRIVATE_KEY")
        password = providers.environmentVariable("JETBRAINS_PRIVATE_KEY_PASSWORD")
    }

    publishing {
        // Token from https://plugins.jetbrains.com/author/me/tokens
        token = providers.environmentVariable("JETBRAINS_TOKEN")

        // Release channel from version.properties: alpha, beta, or default (stable)
        channels = listOf(releaseChannel)
    }
}

val publishCheck by tasks.registering {
    doFirst {
        if (!enablePublish) {
            throw GradleException(
                """
                |Publishing is disabled by default.
                |To publish the plugin, run:
                |  ./gradlew publishPlugin -PenablePublish=true
                |
                |Current version: $xdkVersion
                |Release channel: $releaseChannel
                |
                |Required environment variables:
                |  JETBRAINS_TOKEN - Your JetBrains Marketplace token
                |
                |Optional (for signed releases):
                |  JETBRAINS_CERTIFICATE_CHAIN - Base64-encoded certificate chain
                |  JETBRAINS_PRIVATE_KEY - Base64-encoded private key
                |  JETBRAINS_PRIVATE_KEY_PASSWORD - Private key password
                """.trimMargin()
            )
        }
    }
}

val publishPlugin by tasks.existing {
    enabled = enablePublish
    dependsOn(publishCheck)
}

val buildSearchableOptions by tasks.existing {
    enabled = false // Speeds up build; enable for production
}
