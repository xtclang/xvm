import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.lang.kotlin.jvm)
    alias(libs.plugins.lang.ktlint)
    `java-library`
}

// Use the same Kotlin JDK version as the rest of the XDK (from version.properties)
val kotlinJdkVersion = xdkProperties.int("org.xtclang.kotlin.jdk")

java {
    toolchain {
        languageVersion.set(kotlinJdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(kotlinJdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

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

// Ensure ktlint runs during normal development
val ktlintCheck by tasks.existing
val compileKotlin by tasks.existing {
    dependsOn(ktlintCheck)
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.xvm.debug.XtcDebugServerLauncherKt",
        )
    }
}
