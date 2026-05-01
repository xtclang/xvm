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
// Gradle 9.5.x embeds Kotlin 2.3.20, which supports JVM target 25.
val jdkVersion = versionProps.getProperty("org.xtclang.java.jdk")?.toInt() ?: error("org.xtclang.java.jdk not found in version.properties")

logger.info("[settings] Bootstrap properties: ${versionProps.size} direct properties (jdk=$jdkVersion)")

// Kotlin auto-inherits this toolchain (no explicit kotlin.jvmToolchain needed).
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jdkVersion))
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    val foojay = libs.plugins.foojay.resolver.get()
    implementation("${foojay.pluginId}:${foojay.pluginId}.gradle.plugin:${foojay.version}")
}
