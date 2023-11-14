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

// TODO Can we move plugin application of taskTree here to get it out of every file?

/**
 * The explicit XDK subprojects that are built for each library included in the XDK.
 *   Naming convention: https://kotlinlang.org/docs/reference/coding-conventions.html#naming-rules
 */
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
        throw GradleException("[xdk] Can't find expected XDK project: '$projectName' (at: ${path.absolutePath})")
    }
    logger.lifecycle("[xdk] Resolved XDK subproject '$projectName' (at: '${path.absolutePath}')")
    include(":$p")
    project(":$p").projectDir = path
    project(":$p").name = projectName
}
