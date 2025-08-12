/*
 * Build file for the common Java utilities classes used by various Java projects in the XDK.
 */

plugins {
    id("org.xtclang.build.java")
}

dependencies {
    testImplementation(libs.junit.jupiter)
}

