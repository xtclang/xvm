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

fun compositeRootRelativeFile(path: String): File? {
    var dir = file(".")
    var file = File(dir, path)
    while (!file.exists()) {
        dir = dir.parentFile
        file = File(dir, path)
    }
    return file
}

val libsVersionCatalog = compositeRootRelativeFile("gradle/libs.versions.toml")!!
val versionFile = compositeRootRelativeFile("VERSION")!!

// Read VERSION file for single source of truth
val xvmVersion = versionFile.readText().trim()

// Standard dependency resolution with dynamic version injection
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        val libs by creating {
            from(files(libsVersionCatalog))
            // Override versions from VERSION file to maintain single source of truth
            version("xdk", xvmVersion)
            version("xtc-plugin", xvmVersion)
        }
    }
}

// Optional: Log version info for debugging
val prefix = "[${rootProject.name}]"
logger.info("$prefix Using version catalog from: $libsVersionCatalog")
logger.info("$prefix XVM version from VERSION file: $xvmVersion")
