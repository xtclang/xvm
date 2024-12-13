pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
    includeBuild("../plugin")
}

includeBuild("../manualTests")

plugins {
    id("org.xtclang.build.common")
}

rootProject.name = "xdk"

val xdkProjectPath = rootDir

/**
 * The explicit XDK subprojects that are built for each library included in the XDK.
 */
listOf(
    "lib_ecstasy",
    "lib_aggregate",
    "lib_collections",
    "lib_convert",
    "lib_cli",
    "lib_crypto",
    "lib_net",
    "lib_json",
    "lib_jsondb",
    "lib_oodb",
    "lib_sec",
    "lib_web",
    "lib_webauth",
    "lib_webcli",
    "lib_xenia",
    "javatools_turtle",
    "javatools_launcher",
    "javatools_bridge"
).forEach { p ->
    fun projectName(name: String): String {
        return name.replace('_', '-')
    }
    val prefix = "[xdk]"
    val path = File(xdkProjectPath.parentFile, p)
    val projectName = projectName(p)
    if (!path.exists()) {
        throw GradleException("$prefix Can't find expected XDK project: '$projectName' (at: ${path.absolutePath})")
    }
    logger.info("$prefix Resolved XDK subproject '$projectName' (at: '${path.absolutePath}')")
    include(":$p")
    project(":$p").projectDir = path
    project(":$p").name = projectName
}
