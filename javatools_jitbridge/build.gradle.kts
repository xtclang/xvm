/*
 * Build file for the JavaTools "bridge" for JIT (aka "_native") module that is used to connect the
 * Java runtime to the Ecstasy type system.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.tasktree)
}

dependencies {
    implementation(libs.javatools)
}
