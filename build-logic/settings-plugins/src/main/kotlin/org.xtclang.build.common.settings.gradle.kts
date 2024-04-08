/**
 * Settings resolution level (early) configuration. The only purpose of this plugin is to make sure
 * that the ancestral root project resolves, so that we can connect any subproject or included build
 * task to the aggregator lifecycle, as well as using its version catalog.
 *
 * It also adds some bootstrapping logic for downloading and accessing Java toolchain components,
 * e.g. the JDK, through the Foojay Resolver plugin, which needs to come in at the settings level.
 */

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

// If we can read properties here, we can also patch the catalog files.
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    // For bootstrapping reasons, we manually load the properties file, instead of falling back to the build logic automatic property handler.
    val prefix = "[${rootProject.name}]"
    val xdkGroup = compositeRootRelativeFile("GROUP")!!.readText().trim()
    val xdkVersionFromFile = compositeRootRelativeFile("VERSION")!!.readText().trim()
    val xdkVersion = System.getenv("XDK_OVERRIDE_VERSION") ?: xdkVersionFromFile
    if (xdkVersion != xdkVersionFromFile) {
        logger.warn("$prefix WARNING: XDK_OVERRIDE_VERSION found. Using '$xdkVersion' (VERSION file specifies: '$xdkVersionFromFile').")
    }
    logger.info("$prefix Configuring and versioning artifact: '$xdkGroup:${rootProject.name}:$xdkVersion'")
    logger.info(
        """
        $prefix XDK VERSION INFO:
        $prefix     Project : '${rootProject.name}' 
        $prefix     Group   : '$xdkGroup' (plugin: '$xdkGroup)')
        $prefix     Version : '$xdkVersion' (plugin: '$xdkVersion') ${if (xdkVersion != xdkVersionFromFile) "[OVERRIDDEN VERSION]" else ""}
    """.trimIndent()
    )

    versionCatalogs {
        val libs by creating {
            from(files(libsVersionCatalog)) // load versions
            version("xdk", xdkVersion)
            version("xtc-plugin", xdkVersion)
            version("group-xdk", xdkGroup)
            version("group-xtc-plugin", xdkGroup)
        }
    }
}
