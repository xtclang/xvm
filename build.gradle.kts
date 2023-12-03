/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    id("org.xtclang.build.version")
    id("org.xtclang.build.aggregator")
    alias(libs.plugins.tasktree)
}
