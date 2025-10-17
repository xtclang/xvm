/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    alias(libs.plugins.xdk.build.java)
}

dependencies {
    compileOnly(libs.jetbrains.annotations)
    testCompileOnly(libs.jetbrains.annotations)
}

tasks.test {
    // Use custom logging configuration to suppress INFO-level cleanup messages
    systemProperty("java.util.logging.config.file", file("src/test/resources/logging.properties").absolutePath)
}

