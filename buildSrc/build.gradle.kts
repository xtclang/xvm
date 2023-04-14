/**
 * Best practice is to use this file, the root buildSrc build file,
 * and the rest of the buildSrc source tree contain:
 *
 *   1) Common build logic shared between modules.
 *   2) Ideally, any logic that is mutating state. That should be implemented either
 *      as Gradle logic in the buildSrc tree, or inside buildSrc plugin and/or task
 *      implementations.
 */
plugins {
   `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.10")
}

val buildSrcTesting = "TESTING"
