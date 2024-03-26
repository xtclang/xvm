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
    // Hook up mavenCentral, so we can load the third party artifacts declared in libs.versions.toml
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    fun trimmed(file: File): String {
        val str = file.readText()
        val trimmed = str.trim()
        if (str != trimmed) {
            throw GradleException("${file.absolutePath} must not contain extra whitespace (${str.length} != ${trimmed.length}).")
        }
        return trimmed
    }

    // For bootstrapping reasons, we manually load the properties file, instead of falling back to the build logic automatic property handler.
    val xdkGroup = System.getenv("XTC_OVERRIDE_GROUP") ?: trimmed(compositeRootRelativeFile("GROUP")!!)
    val xdkVersion = System.getenv("XTC_OVERRIDE_VERSION") ?: trimmed(compositeRootRelativeFile("VERSION")!!)
    val prefix = "[${rootProject.name}]"
    logger.info("$prefix Configuring and versioning artifact: '$xdkGroup:${rootProject.name}:$xdkVersion'")
    logger.info(
        """
        $prefix XDK VERSION INFO:
        $prefix     Project : '${rootProject.name}' 
        $prefix     Group   : '$xdkGroup'
        $prefix     Version : '$xdkVersion'
    """.trimIndent()
    )

    versionCatalogs {
        val libs by creating {
            from(files(libsVersionCatalog))
            version("xdk", xdkVersion)
            version("xtc-plugin", xdkVersion)
            version("group-xdk", xdkGroup)
            version("group-xtc-plugin", xdkGroup)
        }
    }
}
