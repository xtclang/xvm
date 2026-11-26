plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.lang.kotlin.jvm)
    alias(libs.plugins.spotless)
    `java-library`
}

// JDK toolchain (and Kotlin's auto-inherited toolchain) is configured by the
// org.xtclang.build.xdk.properties convention plugin applied above.

repositories {
    mavenCentral()
}

// What lang's tests share, and none of lang's own code, so that a project's tests gain only this jar: the Kotlin waits
// over the watchdog of hangs (org.xvm.testing.Await, which comes with them)
dependencies {
    api(testFixtures(libs.javatools.utils))
}
