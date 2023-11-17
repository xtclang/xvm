pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
    includeBuild("../plugin")
}

includeBuild("../javatools")
includeBuild("../javatools_unicode")
includeBuild("../manualTests")

plugins {
    id("org.xvm.build.common")
}

rootProject.name = "xdk"

val xdkProjectPath = rootDir

/**
 * The explicit XDK subprojects that are built for each library included in the XDK.
 *   Naming convention: https://kotlinlang.org/docs/reference/coding-conventions.html#naming-rules
 */
// TODO turn these into included builds
listOfNotNull(
    "lib_ecstasy",
    "lib_collections",
    "lib_aggregate",
    "lib_crypto",
    "lib_net",
    "lib_json",
    "lib_jsondb",
    "lib_oodb",
    "lib_web",
    "lib_webauth",
    "lib_xenia",
    "javatools_turtle",
    "javatools_launcher",
    "javatools_bridge"
).forEach { p ->
    fun projectName(name: String): String {
        return name.replace('_', '-')
    }
    val path = File(xdkProjectPath.parentFile, p)
    val projectName = projectName(p)
    if (!path.exists()) {
        throw GradleException("Can't find expected XDK project: '$projectName' (at: ${path.absolutePath})")
    }
    logger.lifecycle("[xdk] Resolved XDK subproject '$projectName' (path: ${path.absolutePath})")
    include(":$p")
    project(":$p").projectDir = path
    project(":$p").name = projectName
}
