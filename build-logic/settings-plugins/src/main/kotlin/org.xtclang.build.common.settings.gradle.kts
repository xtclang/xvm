/**
 * Settings resolution level (early) configuration. The only purpose of this plugin is to make sure
 * that the ancestral root project resolves, so that we can connect any subproject or included build
 * task to the aggregator lifecycle, as well as using its version catalog.
 *
 * It also adds some bootstrapping logic for downloading and accessing Java toolchain components,
 * e.g. the JDK, through the Foojay Resolver plugin, which needs to come in at the settings level.
 */

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

fun compositeRootRelativeFile(path: String): File {
    var dir = file(".")
    var file = File(dir, path)
    while (!file.exists()) {
        dir = dir.parentFile
        file = File(dir, path)
    }
    return file
}

val libsVersionCatalog = compositeRootRelativeFile("gradle/libs.versions.toml")

// Load properties from files into a plain Map for the build service
val props = java.util.Properties().apply {
    listOf("gradle.properties", "xdk.properties", "version.properties")
        .map(::compositeRootRelativeFile)
        .filter { it.isFile }
        .forEach { it.inputStream().use(::load) }
}

// Register build service with plain string map (no Providers, configuration-cache safe)
gradle.sharedServices.registerIfAbsent(
    "xdkPropertiesService",
    XdkPropertiesService::class.java
) {
    parameters.entries.set(props.stringPropertyNames().associateWith(props::getProperty))
}

// Extract version info from the already-loaded properties (no redundant file reads)
val xvmVersion = props.getProperty("xdk.version")
    ?: error("xdk.version not found in version.properties file")
val xvmGroup = props.getProperty("xdk.group")
    ?: error("xdk.group not found in version.properties file")

// Standard dependency resolution with dynamic version injection
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        // Create libs catalog for included builds (root handles this separately)
        create("libs") {
            from(files(libsVersionCatalog))
            version("xdk", xvmVersion)
            version("xtc-plugin", xvmVersion)
            version("group-xdk", xvmGroup)
        }
    }
}

// Optional: Log version info for debugging
logger.info("""
    [settings] Using version catalog from: $libsVersionCatalog
    [settings] XVM version from version.properties file: $xvmVersion
    [settings] XVM group from version.properties file: $xvmGroup
    [settings] Loaded ${props.size} properties from property files
""".trimIndent())
