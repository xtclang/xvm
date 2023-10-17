/**
 * Settings resolution level (early) configuration. The only purpose of this plugin is to make sure
 * that the ancestral root project resolves, so that we can connect any subproject or included build
 * task to the aggregator lifecycle, as well as using its version catalog.
 *
 * It also adds some bootstrapping logic for downloading and accessing Java toolchain components,
 * e.g. the JDK, through the Foojay Resolver plugin, which needs to come in at the settings level.
 */
val catalogPath = "gradle/libs.versions.toml"

val catalogFile = file(".").let {
    var dir = it
    var file = File(dir, catalogPath)
    while (!file.exists()) {
        dir = dir.parentFile
        file = File(dir, catalogPath)
    }
    file
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files(catalogFile))
        }
    }
}
