import org.xtclang.plugin.tasks.XtcCompileTask

/*
 * Build file for the JavaTools "bridge" (aka "_native") module that is used to connect the Java
 * runtime to the Ecstasy type system.
 */

plugins {
    alias(libs.plugins.xtc)
}

dependencies {
    xdkJavaTools(libs.javatools)
    xtcModule(libs.xdk.ecstasy)
    xtcModule(libs.xdk.aggregate)
    xtcModule(libs.xdk.collections)
    xtcModule(libs.xdk.convert)
    xtcModule(libs.xdk.crypto)
    xtcModule(libs.xdk.json)
    xtcModule(libs.xdk.net)
    xtcModule(libs.xdk.sec)
    xtcModule(libs.xdk.web)
}

tasks.named<XtcCompileTask>("compileXtc") {
    // Older builds renamed _native.xtc to javatools_bridge.xtc here; the distribution does that now. Delete such a
    // leftover so the output directory, and every module path built from it, holds a single copy of the module.
    val leftover = outputDirectory.map { it.file("javatools_bridge.xtc") }
    doFirst { leftover.get().asFile.delete() }
}
