/*
 * Build file for the JavaTools "turtle" (aka "mack the bottom turtle") module that is used to
 * boot-strap the Ecstasy type system.
 *
 * This project does NOT build the javatools_turtle.xtc file. (The :xdk project builds it.)
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
