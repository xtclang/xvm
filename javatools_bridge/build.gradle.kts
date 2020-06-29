/*
 * Build file for the JavaTools "bridge" (aka "_native") module that is used to connect the Java
 * runtime to the Ecstasy type system.
 *
 * This project does NOT build the javatools_bridge.xtc file. (The :xdk project builds it.)
 */

tasks.register("clean") {
    group       = "Build"
    description = "Delete previous build results"
    // this project does not build anything itself, so there is nothing to clean
}

tasks.register("build") {
    group       = "Build"
    description = "Build this project"
    // this project does not build anything itself
}
