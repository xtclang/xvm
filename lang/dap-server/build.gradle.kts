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

dependencies {
    // LSP4J Debug Adapter Protocol implementation
    implementation(libs.lang.lsp4j.debug)
    implementation(libs.lang.lsp4j.jsonrpc)

    // Logging - bundled for out-of-process execution
    implementation(libs.lang.slf4j.api)
    implementation(libs.lang.logback)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.xvm.debug.XtcDebugServerLauncherKt",
        )
    }
}
