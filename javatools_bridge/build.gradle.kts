import org.xtclang.plugin.tasks.XtcCompileTask

/*
 * Build file for the JavaTools "bridge" (aka "_native") module that is used to connect the Java
 * runtime to the Ecstasy type system.
 */

plugins {
    id("org.xtclang.build.xdk.versioning")
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.collections)
    xtcModule(libs.xdk.crypto)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.net)
    xtcModule(libs.xdk.web)
}

val compileXtc by tasks.existing(XtcCompileTask::class) {
    outputFilename("_native.xtc" to "javatools_bridge.xtc")
}
