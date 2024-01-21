/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
    alias(libs.plugins.tasktree)
}

val xdkJavaToolsUtilsProvider by configurations.registering {
    description = "Provider configuration of the XVM javatools_utils classes."
    isCanBeResolved = false
    isCanBeConsumed = true
    isVisible = false
}
