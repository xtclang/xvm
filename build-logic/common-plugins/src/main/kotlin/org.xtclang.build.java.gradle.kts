import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED

import java.nio.charset.StandardCharsets.UTF_8

plugins {
    id("org.xtclang.build.xdk.versioning")
    java
}

private val jdkVersion: Int by extra
private val pprefix = "org.xtclang.java"
private val lintProperty = "$pprefix.lint"

// Compute default JVM args early for reuse everywhere
private val enablePreview = getXdkPropertyBoolean("$pprefix.enablePreview", false)
private val defaultJvmArgs = buildList {
    add("-ea")
    if (enablePreview) {
        add("--enable-preview")
        add("--enable-native-access=ALL-UNNAMED")
    }
}

// All projects use the same JDK version from toolchain - no exceptions
// Modern Gradle handles toolchain provisioning properly for build-logic projects

java {
    toolchain {
        val xdkJavaVersion = JavaLanguageVersion.of(jdkVersion)
        val buildProcessJavaVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
        if (!buildProcessJavaVersion.canCompileOrRun(xdkJavaVersion)) {
            logger.warn("NOTE: We are using a more modern Java tool chain than the build process. $buildProcessJavaVersion < $xdkJavaVersion")
        }
        logger.info("[java] Java Toolchain config; binary format version: 'JDK $xdkJavaVersion' (build process version: 'JDK $buildProcessJavaVersion')")
        languageVersion.set(xdkJavaVersion)
    }
}

testing {
    suites {
        @Suppress("UnstableApiUsage") val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    inputs.property("jdkVersion", jdkVersion)
    logger.info("[java] Configuring JavaExec task $name from toolchain (Java version: ${java.toolchain.languageVersion})")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    jvmArgs(defaultJvmArgs)
    doLast {
        logger.info("[java] JVM arguments: $jvmArgs")
    }
}

val checkWarnings by tasks.registering {
    if (!getXdkPropertyBoolean(lintProperty, false)) {
        val lintPropertyHasValue = isXdkPropertySet(lintProperty)
        logger.info("[java] Java warnings are ${if (lintPropertyHasValue) "explicitly" else ""} disabled for project.")
    }
}

val assemble by tasks.existing {
    dependsOn(checkWarnings)
}

tasks.withType<JavaCompile>().configureEach {
    // Declare toolchain and XDK properties as inputs for proper invalidation
    inputs.property("jdkVersion", jdkVersion)
    inputs.property("enablePreview", enablePreview)
    val lint = getXdkPropertyBoolean(lintProperty, false)
    inputs.property("lint", lint)
    val maxErrors = getXdkPropertyInt("$pprefix.maxErrors", 0)
    inputs.property("maxErrors", maxErrors)
    val maxWarnings = getXdkPropertyInt("$pprefix.maxWarnings", 0)
    inputs.property("maxWarnings", maxWarnings)
    val warningsAsErrors = getXdkPropertyBoolean("$pprefix.warningsAsErrors", true)
    inputs.property("warningsAsErrors", warningsAsErrors)
    if (!warningsAsErrors) {
        logger.warn("[java] WARNING: Task '$name' XTC Java convention warnings are not treated as errors, which is best practice (Enable -Werror).")
    }

    val args = buildList {
        add("-Xlint:${if (lint) "all" else "none"}")

        if (enablePreview) {
            add("--enable-preview")
            if (lint) {
                add("-Xlint:preview")
            }
        }

        if (maxErrors > 0) {
            add("-Xmaxerrs")
            add("$maxErrors")
        }

        if (maxWarnings > 0) {
            add("-Xmaxwarns")
            add("$maxWarnings")
        }

        if (warningsAsErrors) {
            add("-Werror")
        }
    }

    with(options) {
        compilerArgs.addAll(args)
        isDeprecation = lint
        isWarnings = lint
        encoding = UTF_8.toString()
        //isFork = false
    }
}

tasks.withType<Test>().configureEach {
    jvmArgs(defaultJvmArgs)
    testLogging {
        showStandardStreams = getXdkPropertyBoolean("$pprefix.test.stdout")
        if (showStandardStreams) {
            events(STANDARD_OUT, STANDARD_ERROR, SKIPPED, STARTED, PASSED, FAILED)
        }
    }
}

// Set the computed args as project extra property and log warnings  
project.extra.set("defaultJvmArgs", defaultJvmArgs)

if (enablePreview) {
    logger.info("[java] WARNING: Project has Java preview features enabled (JDK $jdkVersion).")
}
logger.info("[java] Set default JVM args as project extra property: $defaultJvmArgs")
