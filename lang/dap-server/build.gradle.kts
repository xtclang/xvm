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

// A library of the language server, which hosts the debug sessions and runs their programs
dependencies {
    // LSP4J Debug Adapter Protocol implementation; the session's API is LSP4J's server interface
    api(libs.lang.lsp4j.debug)
    implementation(libs.lang.lsp4j.jsonrpc)

    // Logging goes through the language server's own logging
    implementation(libs.lang.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}
