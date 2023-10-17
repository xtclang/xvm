/*
 * Main build file for the XVM project, producing the XDK.
 */

plugins {
    id("org.xvm.build.version")
    id("org.xvm.build.aggregator")
    alias(libs.plugins.tasktree)
}
