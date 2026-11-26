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

// What the IntelliJ plugin and the language and debug servers must agree on: the xtc/ extension of LSP, the types its
// requests and notifications carry, and xunit's test events. The plugin runs in the IDE, and the servers in a process
// of their own, each with its own lsp4j and Gson: the servers bundle theirs, and the plugin has LSP4IJ's and the IDE's,
// which it must use, as a second lsp4j in the plugin would clash with LSP4IJ's. So this jar compiles against lsp4j and
// Gson and holds neither.
dependencies {
    compileOnly(libs.lang.lsp4j)
    compileOnly(libs.gson)

    testImplementation(libs.gson)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}
