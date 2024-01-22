import XdkBuildLogic.Companion.DEFAULT_JAVA_BYTECODE_VERSION
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

private val pprefix = "org.xtclang.java"
private val lintProperty = "$pprefix.lint"

private val jdkVersion: Provider<Int> = provider {
    getXdkPropertyInt("$pprefix.jdk", DEFAULT_JAVA_BYTECODE_VERSION)
}

java {
    toolchain {
        val xdkJavaVersion = JavaLanguageVersion.of(jdkVersion.get())
        val buildProcessJavaVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
        if (!buildProcessJavaVersion.canCompileOrRun(xdkJavaVersion)) {
            throw buildException("Error in Java Toolchain config. The builder can't compile requested Java version: $xdkJavaVersion")
        }
        logger.info("$prefix Java Toolchain config; binary format version: 'JDK $xdkJavaVersion' (build process version: 'JDK $buildProcessJavaVersion')")
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
    logger.info("$prefix Configuring JavaExec task $name from toolchain (Java version: ${java.toolchain.languageVersion})")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    if (enablePreview()) {
        jvmArgs("--enable-preview")
    }
    doLast {
        logger.info("$prefix JVM arguments: $jvmArgs")
    }
}

//val assemble by tasks.existing
val checkWarnings by tasks.registering {
    if (!getXdkPropertyAndTaskInputBool(lintProperty, false)) {
        val lintPropertyHasValue = isXdkPropertySet(lintProperty)
        if (lintPropertyHasValue) {
            logger.warn("$prefix *** WARNING: Project EXPLICITLY disables Java linting/warnings in its properties. DO NOT RELEASE PRODUCTION CODE COMPILED WITH DISABLED WARNINGS!")
        } else {
            logger.warn("$prefix *** WARNING: Java linting/warnings disabled for project. This is not best practice!")
        }
    }
}

val assemble by tasks.existing {
    dependsOn(checkWarnings)
}

tasks.withType<JavaCompile>().configureEach {
    // TODO: These xdk properties may have to be declared as inputs.
    val lint = getXdkPropertyAndTaskInputBool(lintProperty, false)
    val maxErrors = getXdkPropertyAndTaskInputInt("$pprefix.maxErrors", 0)
    val maxWarnings = getXdkPropertyAndTaskInputInt("$pprefix.maxWarnings", 0)
    val warningsAsErrors = getXdkPropertyAndTaskInputBool("$pprefix.warningsAsErrors", true)
    if (!warningsAsErrors) {
        logger.warn("$prefix WARNING: Task '$name' XTC Java convention warnings are not treated as errors, which is best practice (Enable -Werror).")
    }

    val args = buildList {
        add("-Xlint:${if (lint) "all" else "none"}")

        if (enablePreview()) {
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
    if (enablePreview()) {
        jvmArgs("--enable-preview")
    }
    maxHeapSize = getXdkProperty("$pprefix.maxHeap", "4G")
    testLogging {
        showStandardStreams = getXdkPropertyAndTaskInputBool("$pprefix.test.stdout")
        if (showStandardStreams) {
            events(STANDARD_OUT, STANDARD_ERROR, SKIPPED, STARTED, PASSED, FAILED)
        }
    }
}

private fun enablePreview(): Boolean {
    val enablePreview = getXdkPropertyBool("$pprefix.enablePreview")
    if (enablePreview) {
        logger.warn("$prefix WARNING: Project has Java preview features enabled.")
    }
    return enablePreview
}
