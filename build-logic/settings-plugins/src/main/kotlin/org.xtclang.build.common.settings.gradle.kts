/**
 * Settings resolution level (early) configuration. The only purpose of this plugin is to make sure
 * that the ancestral root project resolves, so that we can connect any subproject or included build
 * task to the aggregator lifecycle, as well as using its version catalog.
 *
 * It also adds some bootstrapping logic for downloading and accessing Java toolchain components,
 * e.g. the JDK, through the Foojay Resolver plugin, which needs to come in at the settings level.
 */

fun fileWithPath(path: String): File {
    var dir = file(".")
    var file = File(dir, path)
    while (!file.exists()) {
        dir = dir.parentFile
        file = File(dir, path)
    }
    return file
}

val libsVersionCatalog = fileWithPath("gradle/libs.versions.toml")
val xdkVersionProperties = fileWithPath("xdk.properties")

// If we can read properties here, we can also patch the catalog files.
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    //val xdkProperties = FileInputStream(xdkVersionProperties).use { fs -> Properties().also { it.load(fs) } }
    //println("XDK PROPERTIES: " + xdkProperties)
    versionCatalogs {
        val libs by versionCatalogs.creating {
            version("xdk2", "0.5.0")
            from(files(libsVersionCatalog)) // load versions
        }
    }
}
