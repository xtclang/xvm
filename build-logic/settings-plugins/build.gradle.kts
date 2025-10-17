import java.util.Properties

plugins {
    `kotlin-dsl`
}

group = "org.xtclang.build"

// BOOTSTRAPPING - the settings plugin need to hard code property access since it hasn't
// instantiated the XdkPropertiesExtension and the version catalog yet.
// Read JDK version from version.properties (parent directory walk to find it)
val versionProps = Properties().apply {
    val versionPropsFile = generateSequence(projectDir.parentFile)
        { it.parentFile }.
        firstNotNullOfOrNull { dir -> file("$dir/version.properties").takeIf { it.exists() } }
        ?: error("Could not find version.properties in parent directories")
    versionPropsFile.inputStream().use { load(it) }
}

// Kotlin doesn't support Java 25 yet (as of Kotlin 2.1.20), so use Kotlin JDK from version.properties
val jdkVersion = versionProps.getProperty("org.xtclang.java.jdk")?.toInt() ?: error("org.xtclang.java.jdk not found in version.properties")
val kotlinJdkVersion = versionProps.getProperty("org.xtclang.kotlin.jdk")?.toInt() ?: error("org.xtclang.kotlin.jdk not found in version.properties")

logger.lifecycle("[settings] Boostrap properties: ${versionProps.size} direct properties (jdk=$jdkVersion, kotlin=$kotlinJdkVersion)")

// NOTE: This avoids kotlin warnings, but the build system will run with Java 24.
// The produced output will still be Java 25.
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

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.gradle.toolchains.foojay-resolver-convention:org.gradle.toolchains.foojay-resolver-convention.gradle.plugin:1.0.0")
}
