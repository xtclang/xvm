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

// Keep Kotlin build logic on the same JDK/toolchain level as the rest of the build.
// Gradle 9.4.x embeds Kotlin 2.3.0, which supports JVM target 25.
val jdkVersion = versionProps.getProperty("org.xtclang.java.jdk")?.toInt() ?: error("org.xtclang.java.jdk not found in version.properties")
val kotlinJdkVersion = versionProps.getProperty("org.xtclang.kotlin.jdk")?.toInt() ?: error("org.xtclang.kotlin.jdk not found in version.properties")

logger.info("[settings] Boostrap properties: ${versionProps.size} direct properties (jdk=$jdkVersion, kotlin=$kotlinJdkVersion)")

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
