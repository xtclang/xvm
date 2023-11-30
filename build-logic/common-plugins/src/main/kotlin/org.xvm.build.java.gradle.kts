import XdkBuildLogic.Companion.DEFAULT_JAVA_BYTECODE_VERSION
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STARTED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED

import java.nio.charset.StandardCharsets.UTF_8

plugins {
    id("org.xvm.build.version")
    java
    // TODO: Checkstyle/Spotless
}

private val enablePreview = enablePreview()

java {
    toolchain {
        val xdkJavaVersion = JavaLanguageVersion.of(getXdkProperty("org.xvm.java.jdk", DEFAULT_JAVA_BYTECODE_VERSION).toInt())
        val buildProcessJavaVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
        if (!buildProcessJavaVersion.canCompileOrRun(xdkJavaVersion)) {
            throw buildException("Error in Java toolchain config. The builder can't compile requested Java version: $xdkJavaVersion")
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
    logger.info("$prefix Configuring JavaExec task $name from toolchain (Java version: ${java.toolchain.languageVersion})")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain)) // Or the plugin breaks
    if (enablePreview) {
        jvmArgs("--enable-preview")
    }
    doLast {
        logger.info("$prefix JVM arguments: $jvmArgs")
    }
}

tasks.withType<JavaCompile>().configureEach {
    val lint = getXdkProperty("org.xvm.java.lint", "false").toBoolean()
    val maxErrors = getXdkProperty("org.xvm.java.maxErrors", "0").toInt()
    val maxWarnings = getXdkProperty("org.xvm.java.maxWarnings", "0").toInt()
    val warningsAsErrors = getXdkProperty("org.xvm-java.warningsAsErrors", "false").toBoolean()

    if (!warningsAsErrors) {
        logger.warn("$prefix WARNING: Task '$name' XTC Java convention warnings are not treated as errors, which is best-practice (Enable -Werror).")
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

    options.compilerArgs.addAll(args)
    options.isDeprecation = lint
    options.isWarnings = lint
    options.encoding = UTF_8.toString()
    doLast {
        logger.info("$prefix Task '$name' configured (JavaCompile): [isDeprecation=${options.isDeprecation}, encoding=${options.encoding}, arguments=${options.compilerArgs}]")
    }
}

tasks.withType<Test>().configureEach {
    if (enablePreview) {
        jvmArgs("--enable-preview")
    }
    maxHeapSize = getXdkProperty("org.xvm.java.maxHeap", "4G")
    testLogging {
        showStandardStreams = getXdkPropertyBoolean("org.xvm.java.test.stdout", false)
        if (showStandardStreams) {
            events(STANDARD_OUT, STANDARD_ERROR, SKIPPED, STARTED, PASSED, FAILED)
        }
    }
    doLast {
        logger.info("$prefix Task '$name' configured (Test).")
    }
}

fun enablePreview(): Boolean {
    val enablePreview = getXdkPropertyBoolean("org.xvm.java.enablePreview", false)
    if (enablePreview) {
        logger.warn("$prefix WARNING; project has Java preview features enabled.")
    }
    return enablePreview
}
