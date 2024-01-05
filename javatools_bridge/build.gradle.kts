/*
 * Build file for the JavaTools "bridge" (aka "_native") module that is used to connect the Java
 * runtime to the Ecstasy type system.
 */

plugins {
    id("org.xtclang.build.version")
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

xtcCompile {
    // TODO: outputFilename = "_native.xtc" has a bug. Figure out why.
    moduleFilename("_native.xtc", "javatools_bridge.xtc")
}
